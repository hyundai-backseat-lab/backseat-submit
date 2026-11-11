package io.backseatlab.playground.session

import io.backseatlab.playground.game.api.Difficulty
import io.backseatlab.playground.game.api.GameInput
import io.backseatlab.playground.game.api.GameStatus
import io.backseatlab.playground.pleos.Gear
import io.backseatlab.playground.pleos.Reading
import io.backseatlab.playground.pleos.VehicleState
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

/** F-06: 주차 중 화면 버튼과 요약 패널 값. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenControlTest {
    private val pleos = FakePleosClient(initial = VehicleState(gear = Reading.Value(Gear.PARK)))
    private lateinit var session: SessionController

    private fun TestScope.listening(game: FakeGame) {
        session = SessionController(pleos, { game }, backgroundScope, { testScheduler.currentTime }, {})
        session.start()
        while (session.status.value.phase != Phase.LISTENING) advanceTimeBy(100)
        runCurrent()
    }

    @Test
    fun `주차 중 난이도 버튼은 버튼 글자를 응답으로 보내고 주행 중에는 강제로 눌러도 동작하지 않는다`() =
        runTest {
            val game = FakeGame(FakeGame.ask("어떤 난이도로 할까요?", "t1"), FakeGame.ask("문제", "t2"))
            listening(game)
            pleos.updateVehicle { copy(gear = Reading.Value(Gear.DRIVE)) }
            runCurrent()

            assertFalse(session.answerFromScreen("중급"))
            assertFalse(session.restFromScreen())
            assertEquals(1, game.inputs.size)

            pleos.updateVehicle { copy(gear = Reading.Value(Gear.PARK)) }
            runCurrent()
            assertTrue(session.answerFromScreen("중급"))
            advanceTimeBy(100)
            assertEquals(GameInput.Answer("t1", listOf("중급")), game.inputs[1])
        }

    @Test
    fun `주차 중 쉬기 버튼은 휴식 요청이고 요약 패널 값은 게임 status에서 온다`() =
        runTest {
            val played = GameStatus(difficulty = Difficulty.NORMAL, missionNo = 3, missionTitle = "빨간 간판", found = 2)
            listening(FakeGame(FakeGame.ask("문제", "t1"), played = played))

            assertEquals(PlayStatus("중급", 3, "빨간 간판", 2), session.status.value.play)
            assertTrue(session.restFromScreen())
            runCurrent()
            assertEquals(Phase.RESTING, session.status.value.phase)
            assertFalse(session.restFromScreen())
        }

    @Test
    fun `그만할까요 확인 중과 도착 마무리 중에는 화면 답을 받지 않는다`() =
        runTest {
            val game = FakeGame(FakeGame.ask("문제", "t1"), FakeGame.finish("마무리"))
            listening(game)
            assertTrue(session.status.value.answerOpen)

            pleos.say("그만할래")
            advanceTimeBy(100)
            assertFalse(session.status.value.answerOpen)
            assertFalse(session.answerFromScreen("중급"))
            pleos.say("아니")
            while (!session.status.value.answerOpen) advanceTimeBy(100)

            pleos.arrive()
            runCurrent()
            assertFalse(session.answerFromScreen("중급"))
            advanceTimeBy(1_000)
            assertEquals(listOf(GameInput.Start, GameInput.Arrived), game.inputs)
        }
}
