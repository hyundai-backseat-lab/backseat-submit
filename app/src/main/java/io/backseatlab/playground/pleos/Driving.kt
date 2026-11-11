package io.backseatlab.playground.pleos

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

/** 주차 확인(F-03-06). 값을 모르면 UNKNOWN이고, 시작 조건에서는 주차가 아닌 것으로 본다. */
enum class Parking { PARKED, NOT_PARKED, UNKNOWN }

/**
 * 결정표의 주행 상태(features.md 공통 정책). 속도가 SDK에 없어 저속은 따로 두지 않는다(결정표에서 저속 = 정차와 같은 칸).
 * 값을 모르면 DRIVING(안전한 쪽).
 */
enum class Driving { PARKED, STOPPED, DRIVING }

/** 주차 = 기어 P 또는 주차 브레이크. 둘 다 모르면 확인 불가. */
fun VehicleState.parking(): Parking {
    val gearValue = (gear as? Reading.Value)?.value
    val parkBrake = (parkingBrake as? Reading.Value)?.value
    return when {
        gearValue == Gear.PARK || parkBrake == true -> Parking.PARKED
        gearValue != null || parkBrake == false -> Parking.NOT_PARKED
        else -> Parking.UNKNOWN
    }
}

/** 정차 = 주차가 아니고 브레이크 페달을 [STOP_HOLD_MS] 이상 계속 밟음. 밟는 순간이 아니라 2초 뒤에 정차가 된다. */
fun Flow<VehicleState>.driving(): Flow<Driving> =
    // 와이퍼·문처럼 상관없는 신호가 바뀌어도 2초 계산을 다시 시작하지 않게 필요한 두 값만 본다
    map { (it.parking() == Parking.PARKED) to (it.inDriveOrReverse() && (it.brake as? Reading.Value)?.value == true) }
        .distinctUntilChanged()
        .transformLatest { (parked, braking) ->
            when {
                parked -> emit(Driving.PARKED)
                braking -> {
                    emit(Driving.DRIVING)
                    delay(STOP_HOLD_MS)
                    emit(Driving.STOPPED)
                }
                else -> emit(Driving.DRIVING)
            }
        }.distinctUntilChanged()

private fun VehicleState.inDriveOrReverse(): Boolean {
    val value = (gear as? Reading.Value)?.value
    return value == Gear.DRIVE || value == Gear.REVERSE
}

const val STOP_HOLD_MS = 2_000L
