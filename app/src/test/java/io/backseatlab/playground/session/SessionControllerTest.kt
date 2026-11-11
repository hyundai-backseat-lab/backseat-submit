package io.backseatlab.playground.session

import io.backseatlab.playground.game.api.GameInput
import io.backseatlab.playground.game.api.GameSession
import io.backseatlab.playground.game.api.NoAnswerReason
import io.backseatlab.playground.pleos.Reading
import io.backseatlab.playground.pleos.SpeakResult
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

@OptIn(ExperimentalCoroutinesApi::class)
class SessionControllerTest {
    private val pleos = FakePleosClient()
    private val logs = mutableListOf<String>()
    private lateinit var session: SessionController

    private fun TestScope.controller(
        client: FakePleosClient,
        newGame: () -> GameSession,
    ) = SessionController(client, newGame, backgroundScope, { testScheduler.currentTime }, { logs += it })

    private fun TestScope.start(game: FakeGame): FakeGame {
        session = controller(pleos) { game }
        session.start()
        runCurrent()
        return game
    }

    /** 응답 차례가 열려 아이 차례 신호까지 끝날 때까지 진행한다. */
    private fun TestScope.untilListening() {
        repeat(MAX_STEPS) {
            if (session.status.value.phase == Phase.LISTENING) {
                runCurrent()
                return
            }
            advanceTimeBy(STEP_MS)
        }
    }

    private fun spokenAfter(text: String) = pleos.spoken.drop(pleos.spoken.lastIndexOf(text) + 1)

    @Test
    fun `시작하면 참여 방법을 안내하고 ready 뒤 신호를 낸 다음 답을 게임에 한 번 전달한다`() =
        runTest {
            val game = start(FakeGame(FakeGame.ask("문제", "t1"), FakeGame.finish("끝")))
            untilListening()
            pleos.say("사과")
            advanceTimeBy(STEP_MS)

            assertEquals(SessionLines.intro + listOf("문제", "끝"), pleos.spoken)
            assertEquals(listOf(GameInput.Start, GameInput.Answer("t1", listOf("사과"))), game.inputs)
            assertTrue("SESSION start id=1" in logs)
            assertEquals(Phase.ENDED, session.status.value.phase)
        }

    @Test
    fun `14초 동안 답이 없으면 SILENT, 오류와 빈 결과는 UNHEARD로 전달한다`() =
        runTest {
            val game = start(FakeGame(FakeGame.ask("문제", "t1"), FakeGame.ask("다시", "t2"), FakeGame.ask("또", "t3")))
            untilListening()
            advanceTimeBy(13_900)
            assertEquals(1, game.inputs.size)
            advanceTimeBy(200)
            untilListening()
            pleos.fail("NO_RESPONSE")
            untilListening()
            pleos.say(" ")
            advanceTimeBy(STEP_MS)

            assertEquals(
                listOf(
                    GameInput.NoAnswer("t1", NoAnswerReason.SILENT),
                    GameInput.NoAnswer("t2", NoAnswerReason.UNHEARD),
                    GameInput.NoAnswer("t3", NoAnswerReason.UNHEARD),
                ),
                game.inputs.drop(1),
            )
        }

    @Test
    fun `안내 중 잠깐 쉬자는 휴식, 그만할래는 확인 후 무음 종료`() =
        runTest {
            val slow = FakePleosClient(speakMs = 3_000)
            val game = FakeGame(FakeGame.ask("문제", "t1"))
            session = controller(slow) { game }
            session.start()
            advanceTimeBy(1_000)

            slow.say("잠깐 쉬자")
            advanceTimeBy(STEP_MS)
            assertEquals(Phase.RESTING, session.status.value.phase)
            assertTrue("SESSION rest reason=USER" in logs)

            advanceTimeBy(3_000)
            slow.say("그만할래")
            advanceTimeBy(3_100)
            assertEquals(SessionLines.CONFIRM_QUIT, slow.spoken.last())
            slow.say("응")
            advanceTimeBy(STEP_MS + 3_100)
            assertEquals(SessionLines.farewell(0), slow.spoken.last())

            assertEquals(EndReason.USER, session.status.value.endReason)
            assertTrue("SESSION end reason=USER" in logs)
            assertEquals(emptyList<GameInput>(), game.inputs)
        }

    @Test
    fun `그만할까요에 아니라고 하면 하던 차례로 돌아간다`() =
        runTest {
            val game = start(FakeGame(FakeGame.ask("문제", "t1")))
            untilListening()
            pleos.say("그만할래")
            advanceTimeBy(STEP_MS)
            pleos.say("아니")
            untilListening()

            assertEquals(Phase.LISTENING, session.status.value.phase)
            assertEquals(1, game.inputs.size)
        }

    @Test
    fun `응답 접수 직후 휴식하고 재개하면 같은 답을 다시 보내지 않고 계산 결과부터 이어간다`() =
        runTest {
            val game = start(FakeGame(FakeGame.ask("문제", "t1"), FakeGame.ask("잘했어요", "t2"), stepMs = 2_000))
            untilListening()
            pleos.say("사과")
            advanceTimeBy(500)
            pleos.say("잠깐 쉬자")
            advanceTimeBy(5_000)
            assertEquals(Phase.RESTING, session.status.value.phase)
            assertFalse("잘했어요" in pleos.spoken)

            pleos.say("다시 하자")
            untilListening()

            assertEquals(listOf(SessionLines.RESUME, "잘했어요"), spokenAfter(SessionLines.REST))
            assertEquals(1, game.inputs.count { it is GameInput.Answer })
        }

    @Test
    fun `종료 뒤 늦게 온 게임 결과는 버리고 step이 끝난 뒤 close한다`() =
        runTest {
            val game = start(FakeGame(FakeGame.ask("문제", "t1"), FakeGame.ask("늦은 결과", "t2"), stepMs = 2_000))
            untilListening()
            pleos.say("사과")
            advanceTimeBy(500)

            session.end(EndReason.LEFT)
            assertEquals(0, game.closed)
            advanceTimeBy(5_000)

            assertFalse("늦은 결과" in pleos.spoken)
            assertEquals(1, game.closed)
            assertFalse(pleos.holdingFocus)
        }

    @Test
    fun `응답 없는 미션이 이어져 게임이 휴식을 제안하면 휴식하고 2분 뒤 종료한다`() =
        runTest {
            val game = start(FakeGame(FakeGame.suggestRest("잠깐 쉬어 갈까요")))
            advanceTimeBy(STEP_MS)
            assertTrue("SESSION rest reason=NO_RESPONSE" in logs)
            assertEquals(Phase.RESTING, session.status.value.phase)

            advanceTimeBy(119_000)
            assertEquals(Phase.RESTING, session.status.value.phase)
            advanceTimeBy(2_000)

            assertEquals(EndReason.REST_TIMEOUT, session.status.value.endReason)
            assertEquals(listOf(GameInput.Start), game.inputs)
        }

    @Test
    fun `멈춤 중 재개와 재안내 요청은 보관하지 않고 멈춤이 풀리면 1초 뒤 신호 없이 듣기만 다시 한다`() =
        runTest {
            start(FakeGame(FakeGame.ask("문제", "t1")))
            untilListening()
            session.setPause(PauseReason.TURN, true)
            runCurrent()
            assertEquals(Phase.PAUSED, session.status.value.phase)
            pleos.say("다시 하자")
            runCurrent()
            pleos.say("다시 말해 줘")
            runCurrent()
            val spokenBefore = pleos.spoken.size

            session.setPause(PauseReason.TURN, false)
            advanceTimeBy(900)
            assertEquals(Phase.PAUSED, session.status.value.phase)
            advanceTimeBy(200)

            assertEquals(Phase.LISTENING, session.status.value.phase)
            assertEquals(spokenBefore, pleos.spoken.size)
        }

    @Test
    fun `멈춤 중 종료 요청은 확인 없이 즉시 종료한다`() =
        runTest {
            start(FakeGame(FakeGame.ask("문제", "t1")))
            untilListening()
            session.setPause(PauseReason.FOCUS_LOSS, true)
            runCurrent()

            pleos.say("그만할래")
            runCurrent()

            assertEquals(EndReason.USER, session.status.value.endReason)
            assertFalse(SessionLines.CONFIRM_QUIT in pleos.spoken)
        }

    @Test
    fun `말하던 중 멈추면 끊긴 문장만 다시 말한다`() =
        runTest {
            val slow = FakePleosClient(speakMs = 3_000)
            session = controller(slow) { FakeGame(FakeGame.ask("문제", "t1")) }
            session.start()
            advanceTimeBy(4_000)
            session.setPause(PauseReason.BRAKE, true)
            runCurrent()
            session.setPause(PauseReason.BRAKE, false)
            advanceTimeBy(1_100)

            assertEquals(listOf(SessionLines.intro[0], SessionLines.intro[1], SessionLines.intro[1]), slow.spoken)
        }

    @Test
    fun `멈춤 없이 발화가 취소되면 같은 문장을 다시 말하고 진행이 멈추지 않는다`() =
        runTest {
            val cancelling = FakePleosClient(speakResult = SpeakResult.Cancelled, speakMs = 10)
            session = controller(cancelling) { FakeGame(FakeGame.ask("문제", "t1")) }
            session.start()
            advanceTimeBy(STEP_MS)

            assertTrue(cancelling.spoken.size > 2)
            assertEquals(setOf(SessionLines.intro[0]), cancelling.spoken.toSet())
            assertEquals(Phase.GUIDING, session.status.value.phase)
        }

    @Test
    fun `앱이 방금 말한 문장 속 제어 문구는 반향으로 보고 버린다`() =
        runTest {
            val slow = FakePleosClient(speakMs = 3_000)
            session =
                SessionController(
                    slow,
                    { FakeGame(FakeGame.ask("문제", "t1")) },
                    backgroundScope,
                    { testScheduler.currentTime },
                    { logs += it },
                )
            session.start()
            advanceTimeBy(7_000)
            slow.say("그만할래라고 하면 돼요")
            advanceTimeBy(STEP_MS)

            assertTrue("SESSION echo dropped" in logs)
            assertFalse(SessionLines.CONFIRM_QUIT in slow.spoken)
        }

    @Test
    fun `진행 중에 다시 시작해도 새 이용을 만들지 않고 차량 신호는 화면 상태에 반영된다`() =
        runTest {
            var created = 0
            session =
                SessionController(
                    pleos,
                    {
                        created++
                        FakeGame(FakeGame.ask("문제", "t1"))
                    },
                    backgroundScope,
                    log = {},
                )
            session.start()
            advanceTimeBy(1_000)
            session.start()
            pleos.updateVehicle { copy(wiper = Reading.Value(true)) }
            advanceTimeBy(1_000)

            assertEquals(1, created)
            assertTrue(Signal.WIPER in session.carStatus.value.active)
        }

    @Test
    fun `게임 step이 예외를 던지면 재시도 없이 종료한다`() =
        runTest {
            session = controller(pleos) { ThrowingGame() }
            session.start()
            advanceTimeBy(STEP_MS)

            assertEquals(EndReason.FAILURE, session.status.value.endReason)
        }

    private companion object {
        const val STEP_MS = 100L
        const val MAX_STEPS = 300
    }
}
