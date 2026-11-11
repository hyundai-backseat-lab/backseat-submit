package io.backseatlab.playground.game

import io.backseatlab.playground.game.api.GameInput
import io.backseatlab.playground.game.api.GameNext
import io.backseatlab.playground.game.api.GamePlan
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GiltamGameTest {
    private val observe = ObserveGame(OBSERVE_MISSIONS, Random(1))
    private val game = GiltamGame(observe) { WordChain(WORD_LINKS, Random(1)) }

    private suspend fun GiltamGame.say(text: String): GamePlan = step(GameInput.Answer("t", listOf(text)))

    private suspend fun GiltamGame.startAt(level: String) {
        step(GameInput.Start)
        say(level)
    }

    private suspend fun GiltamGame.solve(): GamePlan {
        val m = OBSERVE_MISSIONS.first { it.title == status().missionTitle }
        return say(m.right.first())
    }

    @Test
    fun `미션 3개를 마치면 말잇기를 연다`() =
        runTest {
            game.startAt("초급")
            game.solve()
            game.solve()
            val plan = game.solve()
            assertTrue(plan.text(), plan.text().contains("이번엔 말잇기 해 볼까요? 원숭이 엉덩이는 빨개요!"))
            assertTrue(plan.next is GameNext.AwaitAnswer)
        }

    @Test
    fun `말잇기 중에는 화면 제목도 말잇기로 바뀐다`() =
        runTest {
            game.startAt("초급")
            game.solve()
            game.solve()
            game.solve()
            assertEquals("말잇기", game.status().missionTitle)
        }

    @Test
    fun `말잇기 세 차례 뒤 관찰 미션으로 돌아와 미션을 안내한다`() =
        runTest {
            game.startAt("초급")
            repeat(3) { game.solve() }
            val title = game.status().missionTitle!!
            game.say("사과")
            game.say("사탕")
            val last = game.say("공")
            assertTrue(last.text(), last.text().contains("우리 정말 잘했어요!"))
            assertTrue(last.text(), title.trim('\'') in last.text() || last.text().contains("찾아볼까요?"))
            assertEquals(3, game.status().found)
            // 다시 관찰 미션: 정답이 통한다
            game.solve()
            assertEquals(4, game.status().found)
        }

    @Test
    fun `말잇기 중에 도착하면 마무리하고 끝난다`() =
        runTest {
            game.startAt("초급")
            repeat(3) { game.solve() }
            assertEquals(GameNext.Finished, game.step(GameInput.Arrived).next)
        }

    @Test
    fun `말잇기 중 Start는 마지막 말을 다시 한다`() =
        runTest {
            game.startAt("초급")
            repeat(3) { game.solve() }
            assertTrue(game.step(GameInput.Start).text().contains("빨개요"))
            game.close()
        }
}
