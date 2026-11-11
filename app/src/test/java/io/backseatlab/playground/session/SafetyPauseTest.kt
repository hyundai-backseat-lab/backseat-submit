package io.backseatlab.playground.session

import io.backseatlab.playground.pleos.Gear
import io.backseatlab.playground.pleos.Reading
import io.backseatlab.playground.pleos.Turn
import io.backseatlab.playground.pleos.VehicleState
import io.backseatlab.playground.pleos.fake.FakePleosClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** F-03: 차량 신호·다른 음성 안내가 SessionController를 결정표대로 멈추고 이어가게 하는지. */
@OptIn(ExperimentalCoroutinesApi::class)
class SafetyPauseTest {
    private val driving =
        VehicleState(
            gear = Reading.Value(Gear.DRIVE),
            parkingBrake = Reading.Value(false),
            brake = Reading.Value(false),
            turn = Reading.Value(Turn.OFF),
            warning = Reading.Value(false),
            hazard = Reading.Value(false),
        )
    private val pleos = FakePleosClient(initial = driving, speakMs = 3_000)
    private val logs = mutableListOf<String>()
    private lateinit var session: SessionController

    private fun TestScope.startSpeaking() {
        session =
            SessionController(
                pleos,
                { FakeGame(FakeGame.ask("문제", "t1")) },
                backgroundScope,
                { testScheduler.currentTime },
                { logs += it },
            )
        session.start()
        advanceTimeBy(1_000)
    }

    @Test
    fun `주행 중 방향지시등이 켜지면 300ms 안에 말을 멈추고 꺼지면 1초 뒤 끊긴 문장부터 이어간다`() =
        runTest {
            startSpeaking()
            pleos.updateVehicle { copy(turn = Reading.Value(Turn.LEFT)) }
            advanceTimeBy(300)

            assertEquals(1, pleos.stopSpeakingCount)
            assertEquals(Phase.PAUSED, session.status.value.phase)
            assertTrue("SAFETY state=DRIVING signal=TURN action=PAUSE" in logs)

            pleos.updateVehicle { copy(turn = Reading.Value(Turn.OFF)) }
            advanceTimeBy(1_100)

            assertTrue("TTS resumed" in logs)
            assertEquals(listOf(SessionLines.intro[0], SessionLines.intro[0]), pleos.spoken)
        }

    @Test
    fun `정차 중 브레이크는 멈추지 않고 주차 중 방향지시등도 멈추지 않는다`() =
        runTest {
            startSpeaking()
            pleos.updateVehicle { copy(brake = Reading.Value(true)) }
            advanceTimeBy(100)
            assertEquals(Phase.PAUSED, session.status.value.phase)
            advanceTimeBy(2_000)
            // 2초 넘게 밟으면 정차 → 브레이크는 계속
            assertTrue("SAFETY state=STOPPED signal=BRAKE action=RESUME" in logs)

            pleos.updateVehicle { copy(gear = Reading.Value(Gear.PARK), brake = Reading.Value(false)) }
            advanceTimeBy(1_100)
            pleos.updateVehicle { copy(turn = Reading.Value(Turn.RIGHT)) }
            runCurrent()
            assertEquals(emptySet<PauseReason>(), session.status.value.pauses)
        }

    @Test
    fun `다른 음성 안내는 멈춤 사유이고 그 사이 휴식 요청은 안내가 끝나도 유지된다`() =
        runTest {
            startSpeaking()
            pleos.setFocusLost(true)
            runCurrent()
            assertTrue("SAFETY reason=FOCUS_LOSS action=PAUSE" in logs)
            assertEquals(setOf(PauseReason.FOCUS_LOSS), session.status.value.pauses)

            pleos.say("잠깐 쉬자")
            runCurrent()
            pleos.setFocusLost(false)
            advanceTimeBy(1_100)

            assertEquals(Phase.RESTING, session.status.value.phase)
            assertTrue(SessionLines.REST !in pleos.spoken)
        }

    @Test
    fun `차량 경고가 오면 추가 발화 없이 무음 종료한다`() =
        runTest {
            startSpeaking()
            val spokenBefore = pleos.spoken.size
            pleos.updateVehicle { copy(warning = Reading.Value(true)) }
            advanceTimeBy(10_000)

            assertEquals(EndReason.WARNING, session.status.value.endReason)
            assertTrue("SESSION end reason=WARNING" in logs)
            assertEquals(spokenBefore, pleos.spoken.size)
        }
}
