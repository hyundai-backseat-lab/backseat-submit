package io.backseatlab.playground.game

import io.backseatlab.playground.game.api.Difficulty
import io.backseatlab.playground.game.api.GameInput
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ObserveMissionDataTest {
    @Test
    fun `난이도마다 6개 이상이고 정답 오답 예시는 3개씩이다`() {
        Difficulty.entries.forEach { level -> assertTrue(OBSERVE_MISSIONS.count { it.level == level } >= 6) }
        OBSERVE_MISSIONS.forEach {
            assertEquals(3, it.right.size)
            assertEquals(3, it.wrong.size)
        }
    }

    @Test
    fun `미션마다 정답 예시는 맞고 오답 예시는 틀린다`() =
        runTest {
            OBSERVE_MISSIONS.forEach { m ->
                m.right.forEach { assertTrue("${m.id} $it", judge(m, it)) }
                m.wrong.forEach { assertFalse("${m.id} $it", judge(m, it)) }
            }
        }

    private suspend fun judge(
        m: Mission,
        heard: String,
    ): Boolean {
        val g = ObserveGame(listOf(m), Random(1))
        g.step(GameInput.Start)
        g.step(GameInput.Answer("t", listOf(m.level.label())))
        g.step(GameInput.Answer("t", listOf(heard)))
        return g.status().found == 1
    }

    private fun Difficulty.label() =
        when (this) {
            Difficulty.EASY -> "초급"
            Difficulty.NORMAL -> "중급"
            Difficulty.HARD -> "고급"
        }
}
