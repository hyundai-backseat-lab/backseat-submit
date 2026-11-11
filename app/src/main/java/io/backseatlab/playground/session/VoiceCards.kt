package io.backseatlab.playground.session

import io.backseatlab.playground.game.api.CardIds
import io.backseatlab.playground.pleos.CardAudio
import io.backseatlab.playground.pleos.Driving
import io.backseatlab.playground.pleos.Reading
import io.backseatlab.playground.pleos.VehicleState

/** 목소리 카드 저장소(F-09). 녹음 카드 → 샘플 카드 순서로 찾고, 없으면 null(세션이 TTS로 대신 말한다). 구현은 di. */
fun interface VoiceCardStore {
    fun find(cardId: String): CardAudio?
}

/** 차량 반응 카드 하나. [trigger]는 로그용 sim 이벤트 이름. */
data class Reaction(
    val cardId: String,
    val trigger: String,
)

/**
 * 차 상태가 바뀔 때 어떤 반응 카드를 낼지 정한다(F-09). 와이퍼는 정차 중 켜질 때 주행 1회당 한 번,
 * 문 열림은 주차 중, 도착 카드는 도착과 주차 브레이크가 [ARRIVAL_WINDOW_MS] 안에 함께 왔을 때 한 번.
 * 주행 1회 = 주차에서 벗어난 뒤 다시 주차할 때까지.
 */
class CarReactions(
    private val clock: () -> Long,
) {
    private var wiperUsedThisDrive = false
    private var lastWiper = false
    private var lastDoor = false
    private var lastParkBrake = false
    private var arrivedAt: Long? = null

    fun onVehicle(
        driving: Driving,
        state: VehicleState,
    ): Reaction? {
        if (driving == Driving.PARKED) wiperUsedThisDrive = false
        val wiper = state.wiper.isOn()
        val door = state.doorOpen.isOn()
        val parkBrake = state.parkingBrake.isOn()
        val reaction =
            when {
                wiper && !lastWiper && driving == Driving.STOPPED && !wiperUsedThisDrive -> {
                    wiperUsedThisDrive = true
                    Reaction(CardIds.WIPER, "wiper-on")
                }
                parkBrake && !lastParkBrake && arrivedRecently() -> {
                    arrivedAt = null
                    Reaction(CardIds.ARRIVED, "park-on")
                }
                door && !lastDoor && driving == Driving.PARKED -> Reaction(CardIds.BYE, "door-open")
                else -> null
            }
        lastWiper = wiper
        lastDoor = door
        lastParkBrake = parkBrake
        return reaction
    }

    /** 도착 순간 주차 브레이크가 이미 걸려 있으면 바로 도착 카드, 아니면 잠시 기다린다. */
    fun onArrived(state: VehicleState): Reaction? {
        if (state.parkingBrake.isOn()) return Reaction(CardIds.ARRIVED, "nav-arrive")
        arrivedAt = clock()
        return null
    }

    private fun arrivedRecently() = arrivedAt?.let { clock() - it <= ARRIVAL_WINDOW_MS } == true

    private fun Reading<Boolean>.isOn() = (this as? Reading.Value)?.value == true

    private companion object {
        const val ARRIVAL_WINDOW_MS = 10_000L
    }
}
