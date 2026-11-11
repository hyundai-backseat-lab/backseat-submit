package io.backseatlab.playground.session

import io.backseatlab.playground.pleos.Driving
import io.backseatlab.playground.pleos.PleosClient
import io.backseatlab.playground.pleos.Reading
import io.backseatlab.playground.pleos.Turn
import io.backseatlab.playground.pleos.VehicleState
import io.backseatlab.playground.pleos.driving
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

enum class SafetyAction { CONTINUE, PAUSE, END }

/** 결정표에 들어가는 신호(도착은 F-05가 따로 처리한다). */
enum class SafetySignal { TURN, BRAKE, HAZARD, OTHER_VOICE, WARNING }

/**
 * features.md 공통 정책 "주행 상태 × 신호 결정표". 저속 칸은 정차와 같다(D-027).
 * 방향지시등은 주차 중에만 계속, 브레이크는 주행 중에만 멈춤, 비상등·다른 음성 안내는 언제나 멈춤, 경고는 무음 종료.
 */
object SafetyRules {
    fun decide(
        driving: Driving,
        signal: SafetySignal,
    ): SafetyAction =
        when (signal) {
            SafetySignal.TURN -> if (driving == Driving.PARKED) SafetyAction.CONTINUE else SafetyAction.PAUSE
            SafetySignal.BRAKE -> if (driving == Driving.DRIVING) SafetyAction.PAUSE else SafetyAction.CONTINUE
            SafetySignal.HAZARD, SafetySignal.OTHER_VOICE -> SafetyAction.PAUSE
            SafetySignal.WARNING -> SafetyAction.END
        }

    /** 지금 켜져 있는 신호. 좌우 동시(비상등)는 방향지시등이 아니라 비상등으로 본다. */
    fun activeSignals(
        state: VehicleState,
        focusLost: Boolean,
    ): Set<SafetySignal> =
        buildSet {
            val hazard = state.hazard.isOn()
            if (hazard) add(SafetySignal.HAZARD)
            val turn = (state.turn as? Reading.Value)?.value
            if (!hazard && (turn == Turn.LEFT || turn == Turn.RIGHT)) add(SafetySignal.TURN)
            if (state.brake.isOn()) add(SafetySignal.BRAKE)
            if (focusLost) add(SafetySignal.OTHER_VOICE)
            if (state.warning.isOn()) add(SafetySignal.WARNING)
        }

    /** 차량 상태·주행 상태·오디오 포커스가 바뀔 때마다 (주행 상태, 켜진 신호)를 낸다. */
    fun watch(pleos: PleosClient): Flow<Pair<Driving, Set<SafetySignal>>> =
        combine(pleos.vehicle, pleos.vehicle.driving(), pleos.focusLost) { state, driving, focusLost ->
            driving to activeSignals(state, focusLost)
        }.distinctUntilChanged()

    private fun Reading<Boolean>.isOn() = (this as? Reading.Value)?.value == true
}

/** 결정표 신호가 멈춤일 때 남기는 사유. 경고는 멈춤이 아니라 종료라 없다. */
val SafetySignal.pauseReason: PauseReason?
    get() =
        when (this) {
            SafetySignal.TURN -> PauseReason.TURN
            SafetySignal.BRAKE -> PauseReason.BRAKE
            SafetySignal.HAZARD -> PauseReason.HAZARD
            SafetySignal.OTHER_VOICE -> PauseReason.FOCUS_LOSS
            SafetySignal.WARNING -> null
        }
