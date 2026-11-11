package io.backseatlab.playground.debug

import io.backseatlab.playground.BackseatLog
import io.backseatlab.playground.pleos.Gear
import io.backseatlab.playground.pleos.ListenResult
import io.backseatlab.playground.pleos.PleosClient
import io.backseatlab.playground.pleos.Reading
import io.backseatlab.playground.pleos.Turn
import io.backseatlab.playground.pleos.VehicleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.selects.select

/**
 * 진짜 Pleos(base) 위에 `sim.sh` 이벤트로 차량 신호 일부를 덮어쓰고, `sim.sh stt "<문장>"`으로 인식 결과를 주입한다.
 * `sim.sh focus-loss|focus-gain`은 다른 음성 안내(오디오 포커스 상실)를 흉내 낸다.
 * 같은 신호(turn, brake...)는 마지막에 받은 이벤트가 이긴다. 듣는 동안 주입이 오면 그 문장이 결과가 되고 진짜 듣기는 멈춘다.
 */
class SimPleosClient(
    private val base: PleosClient,
    scope: CoroutineScope,
) : PleosClient by base {
    /** 이벤트 하나: sim.sh 이름(event), 바꾸는 신호(signal), 로그에 찍을 값(shown), 바꾸는 방법(apply). */
    private class Override(
        val event: String,
        val signal: String,
        val shown: String,
        val apply: VehicleState.() -> VehicleState,
    )

    private val overridesByEvent: Map<String, Override> =
        listOf(
            Override("turn-left", "turn", "LEFT") { copy(turn = Reading.Value(Turn.LEFT)) },
            Override("turn-right", "turn", "RIGHT") { copy(turn = Reading.Value(Turn.RIGHT)) },
            Override("turn-off", "turn", "OFF") { copy(turn = Reading.Value(Turn.OFF)) },
            Override("brake-on", "brake", "ON") { copy(brake = Reading.Value(true)) },
            Override("brake-off", "brake", "OFF") { copy(brake = Reading.Value(false)) },
            Override("speed-0", "speed", "0") { copy(speedKmh = Reading.Value(0f)) },
            Override("speed-30", "speed", "30") { copy(speedKmh = Reading.Value(30f)) },
            Override("park-on", "park", "ON") { copy(parkingBrake = Reading.Value(true)) },
            Override("park-off", "park", "OFF") { copy(parkingBrake = Reading.Value(false)) },
            Override("wiper-on", "wiper", "ON") { copy(wiper = Reading.Value(true)) },
            Override("wiper-off", "wiper", "OFF") { copy(wiper = Reading.Value(false)) },
            Override("door-open", "door", "OPEN") { copy(doorOpen = Reading.Value(true)) },
            Override("hazard-on", "hazard", "ON") { copy(hazard = Reading.Value(true)) },
            Override("hazard-off", "hazard", "OFF") { copy(hazard = Reading.Value(false)) },
            Override("warning", "warning", "ON") { copy(warning = Reading.Value(true)) },
            Override("gear-d", "gear", "D") { copy(gear = Reading.Value(Gear.DRIVE)) },
            Override("gear-p", "gear", "P") { copy(gear = Reading.Value(Gear.PARK)) },
        ).associateBy { it.event }

    /** 지금 덮어쓰는 중인 값. 신호 이름(turn, brake...)당 하나. */
    private val active = MutableStateFlow<Map<String, Override>>(emptyMap())
    private val injected = Channel<String>(Channel.CONFLATED)

    /** sim.sh focus-loss|focus-gain으로 덮어쓴 오디오 포커스 상실. null이면 진짜 값. 실제 내비 연동이 아니라 시뮬레이션이다. */
    private val simFocusLost = MutableStateFlow<Boolean?>(null)

    private val simArrivals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** sim.sh nav-arrive로 도착을 흉내 낸다(시뮬레이션). 진짜 도착도 그대로 흘려보낸다. */
    override val arrivals: Flow<Unit> = merge(base.arrivals, simArrivals)

    override val focusLost: StateFlow<Boolean> =
        combine(base.focusLost, simFocusLost) { real, sim -> sim ?: real }
            .stateIn(scope, SharingStarted.Eagerly, base.focusLost.value)

    override val vehicle: StateFlow<VehicleState> =
        combine(base.vehicle, active) { real, overrides ->
            overrides.values.fold(real) { state, override -> override.apply(state) }
        }.stateIn(scope, SharingStarted.Eagerly, base.vehicle.value)

    /** `sim.sh`의 이벤트 이름(예: brake-on)을 받아 반영한다. 모르는 이름이면 false. */
    fun send(event: String): Boolean {
        val override = overridesByEvent[event]
        when {
            event == "nav-arrive" -> {
                BackseatLog.i("SIM nav=ARRIVED (시뮬레이션)")
                simArrivals.tryEmit(Unit)
            }
            event == "focus-loss" || event == "focus-gain" -> {
                simFocusLost.value = event == "focus-loss"
                BackseatLog.i("SIM focus=${if (event == "focus-loss") "LOST" else "GAINED"} (시뮬레이션)")
            }
            override == null -> {
                BackseatLog.w("SIM unknown event=$event")
                return false
            }
            else -> {
                active.update { it + (override.signal to override) }
                BackseatLog.i("SIM ${override.signal}=${override.shown}")
            }
        }
        return true
    }

    /** 듣기 전에 주입한 문장은 다음 듣기에서 쓰인다. */
    fun inject(text: String) {
        BackseatLog.i("SIM stt=\"$text\"")
        injected.trySend(text)
    }

    override suspend fun listen(onReady: () -> Unit): ListenResult =
        coroutineScope {
            val real = async { base.listen(onReady) }
            val sim = async { ListenResult.Heard(injected.receive()) }
            val result =
                select {
                    real.onAwait { it }
                    sim.onAwait { it }
                }
            if (!real.isCompleted) base.stopListening()
            real.cancel()
            sim.cancel()
            result
        }
}
