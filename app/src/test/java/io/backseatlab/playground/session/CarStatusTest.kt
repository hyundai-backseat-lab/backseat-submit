package io.backseatlab.playground.session

import io.backseatlab.playground.pleos.Gear
import io.backseatlab.playground.pleos.Reading
import io.backseatlab.playground.pleos.Turn
import io.backseatlab.playground.pleos.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarStatusTest {
    private val allOff =
        VehicleState(
            turn = Reading.Value(Turn.OFF),
            hazard = Reading.Value(false),
            brake = Reading.Value(false),
            parkingBrake = Reading.Value(false),
            warning = Reading.Value(false),
            wiper = Reading.Value(false),
            doorOpen = Reading.Value(false),
            speedKmh = Reading.Unsupported,
            gear = Reading.Value(Gear.DRIVE),
        )

    @Test
    fun `켜진 신호가 없으면 활성 신호 목록이 비어 있다`() {
        assertEquals(emptyList<Signal>(), CarStatus.from(allOff).active)
    }

    @Test
    fun `브레이크와 와이퍼가 켜지면 둘 다 활성 신호로 나온다`() {
        val state = allOff.copy(brake = Reading.Value(true), wiper = Reading.Value(true))
        assertEquals(listOf(Signal.BRAKE, Signal.WIPER), CarStatus.from(state).active)
    }

    @Test
    fun `방향지시등은 왼쪽과 오른쪽을 구분한다`() {
        assertEquals(listOf(Signal.TURN_LEFT), CarStatus.from(allOff.copy(turn = Reading.Value(Turn.LEFT))).active)
        assertEquals(listOf(Signal.TURN_RIGHT), CarStatus.from(allOff.copy(turn = Reading.Value(Turn.RIGHT))).active)
    }

    @Test
    fun `지원하지 않는 신호는 미지원 목록에 나온다`() {
        assertEquals(listOf(Signal.SPEED), CarStatus.from(allOff).unsupported)
    }

    @Test
    fun `값을 하나라도 받아야 연결된 것으로 본다`() {
        assertFalse(CarStatus.from(VehicleState()).connected)
        assertTrue(CarStatus.from(VehicleState(brake = Reading.Value(true))).connected)
    }

    @Test
    fun `값을 못 받은 신호가 있으면 모름이 있다고 알린다`() {
        assertTrue(CarStatus.from(allOff.copy(brake = Reading.Unknown)).hasUnknown)
        assertFalse(CarStatus.from(allOff).hasUnknown)
    }
}
