package io.backseatlab.playground.debug

import io.backseatlab.playground.pleos.ListenResult
import io.backseatlab.playground.pleos.Reading
import io.backseatlab.playground.pleos.Turn
import io.backseatlab.playground.pleos.fake.FakePleosClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimPleosClientTest {
    @Test
    fun `brake-on은 브레이크만 덮어쓰고 나머지는 원본 값을 유지한다`() =
        runTest(UnconfinedTestDispatcher()) {
            val base = FakePleosClient()
            base.updateVehicle { copy(wiper = Reading.Value(true)) }
            val sim = SimPleosClient(base, backgroundScope)

            assertTrue(sim.send("brake-on"))

            assertEquals(Reading.Value(true), sim.vehicle.value.brake)
            assertEquals(Reading.Value(true), sim.vehicle.value.wiper)
        }

    @Test
    fun `같은 신호는 마지막에 보낸 이벤트가 이긴다`() =
        runTest(UnconfinedTestDispatcher()) {
            val sim = SimPleosClient(FakePleosClient(), backgroundScope)
            sim.send("turn-left")
            sim.send("turn-right")
            sim.send("brake-on")
            sim.send("brake-off")
            assertEquals(Reading.Value(Turn.RIGHT), sim.vehicle.value.turn)
            assertEquals(Reading.Value(false), sim.vehicle.value.brake)
        }

    @Test
    fun `원본 값이 바뀌어도 덮어쓴 값은 유지된다`() =
        runTest(UnconfinedTestDispatcher()) {
            val base = FakePleosClient()
            val sim = SimPleosClient(base, backgroundScope)
            sim.send("park-on")
            base.updateVehicle { copy(brake = Reading.Value(true)) }
            assertEquals(Reading.Value(true), sim.vehicle.value.parkingBrake)
            assertEquals(Reading.Value(true), sim.vehicle.value.brake)
        }

    @Test
    fun `모르는 이벤트는 거절하고 상태를 바꾸지 않는다`() =
        runTest(UnconfinedTestDispatcher()) {
            val base = FakePleosClient()
            val sim = SimPleosClient(base, backgroundScope)
            assertFalse(sim.send("fly"))
            assertEquals(base.vehicle.value, sim.vehicle.value)
        }

    @Test
    fun `주입한 문장이 인식 결과로 나오고 진짜 듣기는 멈춘다`() =
        runTest(UnconfinedTestDispatcher()) {
            val base = FakePleosClient()
            base.heard += null
            val sim = SimPleosClient(base, backgroundScope)
            var result: ListenResult? = null
            launch { result = sim.listen() }

            sim.inject("빨가면 사과")

            assertEquals(ListenResult.Heard("빨가면 사과"), result)
            assertEquals(1, base.stopListeningCount)
        }

    @Test
    fun `주입이 없으면 진짜 듣기 결과와 ready 신호가 그대로 나온다`() =
        runTest(UnconfinedTestDispatcher()) {
            val base = FakePleosClient()
            base.heard += ListenResult.Heard("사과")
            val sim = SimPleosClient(base, backgroundScope)
            var ready = 0
            assertEquals(ListenResult.Heard("사과"), sim.listen { ready++ })
            assertEquals(1, ready)
        }

    @Test
    fun `듣기 전에 주입한 문장은 다음 듣기에서 쓰인다`() =
        runTest(UnconfinedTestDispatcher()) {
            val base = FakePleosClient()
            base.heard += null
            val sim = SimPleosClient(base, backgroundScope)
            sim.inject("딸기")
            assertEquals(ListenResult.Heard("딸기"), sim.listen())
        }

    @Test
    fun `focus-loss와 focus-gain은 오디오 포커스 상실을 흉내 낸다`() =
        runTest(UnconfinedTestDispatcher()) {
            val sim = SimPleosClient(FakePleosClient(), backgroundScope)
            assertTrue(sim.send("focus-loss"))
            assertTrue(sim.focusLost.value)
            sim.send("focus-gain")
            assertFalse(sim.focusLost.value)
        }

    @Test
    fun `nav-arrive는 도착 이벤트를 흉내 낸다`() =
        runTest(UnconfinedTestDispatcher()) {
            val sim = SimPleosClient(FakePleosClient(), backgroundScope)
            var arrived = 0
            backgroundScope.launch { sim.arrivals.collect { arrived++ } }
            assertTrue(sim.send("nav-arrive"))
            assertEquals(1, arrived)
        }
}
