package io.backseatlab.playground.pleos

/** 차량 신호 하나의 값. 받을 수 없는 이유를 구분한다. */
sealed interface Reading<out T> {
    /** 이 차량(또는 SDK)이 그 신호를 주지 않는다. */
    data object Unsupported : Reading<Nothing>

    /** 아직 값을 못 받았다. 안전 판정에서는 위험한 쪽으로 본다. */
    data object Unknown : Reading<Nothing>

    data class Value<T>(
        val value: T,
    ) : Reading<T>
}

enum class Turn { OFF, LEFT, RIGHT }

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE }

/** 차량 신호 전체의 현재 값. */
data class VehicleState(
    val turn: Reading<Turn> = Reading.Unknown,
    val hazard: Reading<Boolean> = Reading.Unknown,
    val brake: Reading<Boolean> = Reading.Unknown,
    val parkingBrake: Reading<Boolean> = Reading.Unknown,
    val warning: Reading<Boolean> = Reading.Unknown,
    val wiper: Reading<Boolean> = Reading.Unknown,
    val doorOpen: Reading<Boolean> = Reading.Unknown,
    val speedKmh: Reading<Float> = Reading.Unknown,
    val gear: Reading<Gear> = Reading.Unknown,
) {
    private val readings: List<Reading<Any>>
        get() = listOf(turn, hazard, brake, parkingBrake, warning, wiper, doorOpen, speedKmh, gear)

    /** 값을 하나라도 받았으면 차량과 연결된 것으로 본다. */
    val isConnected: Boolean get() = readings.any { it is Reading.Value }

    /** 아직 값을 못 받은 신호가 있다. 이때 "신호 없음"이라고 단정하면 안 된다. */
    val hasUnknown: Boolean get() = readings.any { it == Reading.Unknown }
}
