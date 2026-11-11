package io.backseatlab.playground.pleos

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DrivingTest {
    private val drive = VehicleState(gear = Reading.Value(Gear.DRIVE), parkingBrake = Reading.Value(false))

    @Test
    fun `기어 P나 주차 브레이크면 주차, 둘 다 모르면 확인 불가`() {
        assertEquals(Parking.PARKED, VehicleState(gear = Reading.Value(Gear.PARK)).parking())
        assertEquals(Parking.PARKED, drive.copy(parkingBrake = Reading.Value(true)).parking())
        assertEquals(Parking.NOT_PARKED, drive.parking())
        assertEquals(Parking.UNKNOWN, VehicleState().parking())
    }

    @Test
    fun `브레이크를 2초 이상 밟아야 정차이고 다른 신호가 바뀌어도 정차가 유지된다`() =
        runTest {
            val vehicle = MutableStateFlow(drive)
            val seen = mutableListOf<Driving>()
            backgroundScope.launch { vehicle.driving().collect { seen += it } }
            runCurrent()

            vehicle.value = drive.copy(brake = Reading.Value(true))
            advanceTimeBy(1_999)
            assertEquals(listOf(Driving.DRIVING), seen)
            advanceTimeBy(2)
            assertEquals(Driving.STOPPED, seen.last())

            vehicle.value = vehicle.value.copy(wiper = Reading.Value(true))
            advanceTimeBy(100)
            assertEquals(Driving.STOPPED, seen.last())

            vehicle.value = vehicle.value.copy(brake = Reading.Value(false))
            runCurrent()
            assertEquals(Driving.DRIVING, seen.last())
        }

    @Test
    fun `값을 모르면 주행으로 본다`() =
        runTest {
            val seen = mutableListOf<Driving>()
            backgroundScope.launch { MutableStateFlow(VehicleState()).driving().collect { seen += it } }
            runCurrent()
            assertEquals(listOf(Driving.DRIVING), seen)
        }
}
