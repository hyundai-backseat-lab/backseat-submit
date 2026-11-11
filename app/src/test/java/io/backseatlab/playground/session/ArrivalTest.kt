package io.backseatlab.playground.session

import io.backseatlab.playground.game.api.GameInput
import io.backseatlab.playground.game.api.GameStatus
import io.backseatlab.playground.pleos.fake.FakePleosClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F-05: 목적지 도착 → 게임 마무리 문장 → 도착 종료, 결과 값. */
@OptIn(ExperimentalCoroutinesApi::class)
class ArrivalTest {
    private val logs = mutableListOf<String>()
    private lateinit var session: SessionController

    private fun TestScope.start(
        pleos: FakePleosClient,
        game: FakeGame,
    ) {
        session = SessionController(pleos, { game }, backgroundScope, { testScheduler.currentTime }, { logs += it })
        session.start()
        runCurrent()
    }

    @Test
    fun `도착하면 게임에 Arrived를 보내 마무리 문장을 말하고 결과와 함께 끝낸다`() =
        runTest {
            val pleos = FakePleosClient()
            val game =
                FakeGame(
                    FakeGame.ask("문제", "t1"),
                    FakeGame.finish("벌써 도착했어요! 오늘 미션 2개 찾았어요."),
                    played = GameStatus(found = 2, played = 3, hints = 1),
                )
            start(pleos, game)
            while (session.status.value.phase != Phase.LISTENING) advanceTimeBy(100)

            pleos.arrive()
            advanceTimeBy(1_000)

            assertEquals(GameInput.Arrived, game.inputs.last())
            assertEquals("벌써 도착했어요! 오늘 미션 2개 찾았어요.", pleos.spoken.last())
            assertEquals(EndReason.ARRIVED, session.status.value.endReason)
            assertEquals(SessionResult(found = 2, played = 3, hints = 1), session.status.value.result)
            assertTrue("SESSION end reason=ARRIVED" in logs)
        }

    @Test
    fun `멈춤 중에 도착하면 말없이 종료한다`() =
        runTest {
            val pleos = FakePleosClient()
            val game = FakeGame(FakeGame.ask("문제", "t1"), FakeGame.finish("마무리"))
            start(pleos, game)
            advanceTimeBy(1_000)
            session.setPause(PauseReason.TURN, true)
            val spoken = pleos.spoken.size

            pleos.arrive()
            advanceTimeBy(5_000)

            assertEquals(EndReason.ARRIVED, session.status.value.endReason)
            assertEquals(spoken, pleos.spoken.size)
            assertFalse(GameInput.Arrived in game.inputs)
        }

    @Test
    fun `하던 말은 최대 3초 안에 마무리하고 더 진행하지 않는다`() =
        runTest {
            val pleos = FakePleosClient(speakMs = 10_000)
            val game = FakeGame(FakeGame.finish("마무리"))
            start(pleos, game)
            advanceTimeBy(500)

            pleos.arrive()
            advanceTimeBy(2_900)
            assertFalse(GameInput.Arrived in game.inputs)
            advanceTimeBy(200)
            assertEquals(1, pleos.stopSpeakingCount)

            advanceTimeBy(15_000)
            assertEquals(listOf<GameInput>(GameInput.Arrived), game.inputs)
            assertEquals(listOf(SessionLines.intro[0], "마무리"), pleos.spoken)
        }

    @Test
    fun `계산 중에 도착하면 그 step이 끝난 뒤 Arrived를 보내고 두 번째 도착은 무시한다`() =
        runTest {
            val pleos = FakePleosClient()
            val plans = arrayOf(FakeGame.ask("문제", "t1"), FakeGame.ask("다음", "t2"), FakeGame.finish("마무리"))
            val game = FakeGame(*plans, stepMs = 2_000)
            start(pleos, game)
            while (session.status.value.phase != Phase.LISTENING) advanceTimeBy(100)
            pleos.say("사과")
            advanceTimeBy(500)
            assertEquals(Phase.THINKING, session.status.value.phase)

            pleos.arrive()
            pleos.arrive()
            advanceTimeBy(10_000)

            assertEquals(GameInput.Arrived, game.inputs.last())
            assertEquals(1, game.inputs.count { it == GameInput.Arrived })
            assertEquals(EndReason.ARRIVED, session.status.value.endReason)
        }

    @Test
    fun `휴식 중에 도착해도 마무리하고, 마무리 전에 멈추면 말없이 끝낸다`() =
        runTest {
            val pleos = FakePleosClient()
            val game = FakeGame(FakeGame.ask("문제", "t1"), FakeGame.finish("마무리"))
            start(pleos, game)
            while (session.status.value.phase != Phase.LISTENING) advanceTimeBy(100)
            pleos.say("잠깐 쉬자")
            advanceTimeBy(1_000)
            assertEquals(Phase.RESTING, session.status.value.phase)

            pleos.arrive()
            advanceTimeBy(1_000)
            assertEquals("마무리", pleos.spoken.last())
            assertEquals(EndReason.ARRIVED, session.status.value.endReason)

            val slow = FakePleosClient(speakMs = 2_000)
            val other = FakeGame(FakeGame.finish("마무리"))
            start(slow, other)
            advanceTimeBy(500)
            slow.arrive()
            runCurrent()
            session.setPause(PauseReason.TURN, true)
            advanceTimeBy(5_000)
            assertEquals(EndReason.ARRIVED, session.status.value.endReason)
            assertFalse("마무리" in slow.spoken)
        }
}
