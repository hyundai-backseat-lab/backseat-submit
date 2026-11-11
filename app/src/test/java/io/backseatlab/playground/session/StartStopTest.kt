package io.backseatlab.playground.session

import io.backseatlab.playground.pleos.Gear
import io.backseatlab.playground.pleos.Reading
import io.backseatlab.playground.pleos.SpeakResult
import io.backseatlab.playground.pleos.VehicleState
import io.backseatlab.playground.pleos.fake.FakePleosClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** F-04: 시작 조건, 화면 이탈·음성 장애 종료. */
@OptIn(ExperimentalCoroutinesApi::class)
class StartStopTest {
    private val parked = VehicleState(gear = Reading.Value(Gear.PARK), warning = Reading.Value(false))
    private val pleos = FakePleosClient(initial = parked)
    private var created = 0
    private lateinit var session: SessionController

    private fun TestScope.controller(): SessionController {
        session =
            SessionController(
                pleos,
                {
                    created++
                    FakeGame(FakeGame.ask("문제", "t1"))
                },
                backgroundScope,
                { testScheduler.currentTime },
                {},
            )
        runCurrent()
        return session
    }

    @Test
    fun `주차·마이크 권한·음성 준비가 모두 되어야 시작하고 아니면 이유를 돌려준다`() =
        runTest {
            controller()
            pleos.updateVehicle { copy(gear = Reading.Value(Gear.DRIVE), parkingBrake = Reading.Value(false)) }
            runCurrent()
            assertEquals(StartBlock.NOT_PARKED, session.requestStart(micGranted = true))

            pleos.updateVehicle { copy(gear = Reading.Value(Gear.PARK), warning = Reading.Value(true)) }
            runCurrent()
            assertEquals(StartBlock.WARNING, session.requestStart(micGranted = true))

            pleos.updateVehicle { copy(warning = Reading.Value(false)) }
            runCurrent()
            assertEquals(StartBlock.NO_MIC, session.requestStart(micGranted = false))
            pleos.voiceAvailable = false
            assertEquals(StartBlock.VOICE_NOT_READY, session.requestStart(micGranted = true))
            assertEquals(0, created)

            pleos.voiceAvailable = true
            assertNull(session.requestStart(micGranted = true))
            assertEquals(1, created)
        }

    @Test
    fun `주차 확인 불가면 시작하지 않는다`() =
        runTest {
            pleos.updateVehicle { VehicleState() }
            controller()
            assertEquals(StartBlock.NOT_PARKED, session.requestStart(micGranted = true))
        }

    @Test
    fun `시작한 이용은 주행 중에도 계속되고 다시 눌러도 새 이용을 만들지 않는다`() =
        runTest {
            controller().requestStart(micGranted = true)
            pleos.updateVehicle { copy(gear = Reading.Value(Gear.DRIVE), parkingBrake = Reading.Value(false)) }
            advanceTimeBy(1_000)

            assertEquals(StartBlock.NOT_PARKED, session.requestStart(micGranted = true))
            pleos.updateVehicle { copy(gear = Reading.Value(Gear.PARK)) }
            runCurrent()
            session.requestStart(micGranted = true)
            assertEquals(1, created)
            assertNull(session.status.value.endReason)
        }

    @Test
    fun `화면을 떠나면 무음 종료한다`() =
        runTest {
            controller().requestStart(micGranted = true)
            advanceTimeBy(1_000)
            session.onScreenLeft()
            assertEquals(EndReason.LEFT, session.status.value.endReason)
        }

    @Test
    fun `권한 상실 같은 음성 기능 장애는 못 알아들음이 아니라 장애 종료다`() =
        runTest {
            controller().requestStart(micGranted = true)
            while (session.status.value.phase != Phase.LISTENING) advanceTimeBy(100)
            runCurrent()
            pleos.fail("PERMISSION_DENIED")
            advanceTimeBy(100)
            assertEquals(EndReason.FAILURE, session.status.value.endReason)
        }

    @Test
    fun `말하는 중 제어 요청 듣기나 말하기에서 장애가 나도 장애 종료한다`() =
        runTest {
            val slow = FakePleosClient(initial = parked, speakMs = 3_000)
            session = SessionController(slow, { FakeGame() }, backgroundScope, { testScheduler.currentTime }, {})
            session.requestStart(micGranted = true)
            advanceTimeBy(500)
            slow.fail("MIC_ERROR")
            advanceTimeBy(500)
            assertNull(session.status.value.endReason)
            slow.fail("PERMISSION_DENIED")
            advanceTimeBy(100)
            assertEquals(EndReason.FAILURE, session.status.value.endReason)

            val broken = FakePleosClient(initial = parked, speakResult = SpeakResult.Failed("INIT"))
            session = SessionController(broken, { FakeGame() }, backgroundScope, { testScheduler.currentTime }, {})
            session.requestStart(micGranted = true)
            advanceTimeBy(100)
            assertEquals(EndReason.FAILURE, session.status.value.endReason)
        }
}
