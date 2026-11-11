package io.backseatlab.playground.game

import io.backseatlab.playground.game.api.Difficulty
import io.backseatlab.playground.game.api.GameInput
import io.backseatlab.playground.game.api.GameNext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class ObserveArrivalTest {
    private suspend fun started() =
        ObserveGame(OBSERVE_MISSIONS, Random(1)).also {
            it.step(GameInput.Start)
            it.say("초급")
        }

    @Test
    fun `도착하면 찾은 수로 마무리하고 새 미션 없이 끝난다`() =
        runTest {
            val g = started()
            val m = OBSERVE_MISSIONS.first { it.title == g.status().missionTitle }
            g.say(m.right.first())
            val plan = g.step(GameInput.Arrived)
            assertEquals("벌써 도착했어요! 오늘 미션 1개 찾았어요.", plan.text())
            assertEquals(GameNext.Finished, plan.next)
            assertEquals(1, plan.messages.size)
            assertEquals(null, plan.messages.single().cardId)
        }

    @Test
    fun `난이도를 정하기 전에 도착해도 0개로 마무리한다`() =
        runTest {
            val plan = ObserveGame(OBSERVE_MISSIONS, Random(1)).step(GameInput.Arrived)
            assertEquals("벌써 도착했어요! 오늘 미션 0개 찾았어요.", plan.text())
        }

    @Test
    fun `status는 난이도 순번 제목 찾은 수 진행 수 힌트 수를 돌려준다`() =
        runTest {
            val g = started()
            assertEquals(Difficulty.EASY, g.status().difficulty)
            assertEquals(1, g.status().missionNo)
            g.say("바나나")
            g.say("사과")
            val s = g.status()
            assertEquals(listOf(2, 0, 1, 1), listOf(s.missionNo, s.found, s.played, s.hints))
        }
}
