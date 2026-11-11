package io.backseatlab.playground.session

import io.backseatlab.playground.pleos.Reading
import io.backseatlab.playground.pleos.Turn
import io.backseatlab.playground.pleos.VehicleState

/** 화면에 보여 줄 수 있는 신호 이름. 문구는 ui가 strings.xml에서 고른다. */
enum class Signal {
    TURN,
    TURN_LEFT,
    TURN_RIGHT,
    HAZARD,
    BRAKE,
    PARKING_BRAKE,
    WARNING,
    WIPER,
    DOOR,
    SPEED,
    GEAR,
}

/** 화면이 차량 상태에서 알아야 하는 것. ui는 pleos를 모르므로 세션이 이 값으로 바꿔 준다. */
data class CarStatus(
    val connected: Boolean,
    val hasUnknown: Boolean,
    val active: List<Signal>,
    val unsupported: List<Signal>,
) {
    companion object {
        fun from(state: VehicleState) =
            CarStatus(
                connected = state.isConnected,
                hasUnknown = state.hasUnknown,
                active = activeSignals(state),
                unsupported = unsupportedSignals(state),
            )

        /** 지금 켜져 있는 신호들. 화면 한 줄에 나열한다. */
        private fun activeSignals(state: VehicleState): List<Signal> =
            buildList {
                when ((state.turn as? Reading.Value)?.value) {
                    Turn.LEFT -> add(Signal.TURN_LEFT)
                    Turn.RIGHT -> add(Signal.TURN_RIGHT)
                    else -> Unit
                }
                if (state.hazard.isOn()) add(Signal.HAZARD)
                if (state.brake.isOn()) add(Signal.BRAKE)
                if (state.parkingBrake.isOn()) add(Signal.PARKING_BRAKE)
                if (state.warning.isOn()) add(Signal.WARNING)
                if (state.wiper.isOn()) add(Signal.WIPER)
                if (state.doorOpen.isOn()) add(Signal.DOOR)
            }

        /** 이 차량이 주지 않는 신호들. "미지원"으로 표시한다. */
        private fun unsupportedSignals(state: VehicleState): List<Signal> =
            buildList {
                if (state.turn == Reading.Unsupported) add(Signal.TURN)
                if (state.hazard == Reading.Unsupported) add(Signal.HAZARD)
                if (state.brake == Reading.Unsupported) add(Signal.BRAKE)
                if (state.parkingBrake == Reading.Unsupported) add(Signal.PARKING_BRAKE)
                if (state.warning == Reading.Unsupported) add(Signal.WARNING)
                if (state.wiper == Reading.Unsupported) add(Signal.WIPER)
                if (state.doorOpen == Reading.Unsupported) add(Signal.DOOR)
                if (state.speedKmh == Reading.Unsupported) add(Signal.SPEED)
                if (state.gear == Reading.Unsupported) add(Signal.GEAR)
            }

        private fun Reading<Boolean>.isOn() = (this as? Reading.Value)?.value == true
    }
}
