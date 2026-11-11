package io.backseatlab.playground.game

import io.backseatlab.playground.game.api.CardIds
import io.backseatlab.playground.game.api.Difficulty
import io.backseatlab.playground.game.api.GameInput
import io.backseatlab.playground.game.api.GameNext
import io.backseatlab.playground.game.api.GamePlan
import io.backseatlab.playground.game.api.NoAnswerReason
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ObserveGameTest {
    private val logs = mutableListOf<String>()

    private val game = { ObserveGame(OBSERVE_MISSIONS, Random(1)) { logs += it } }

    private suspend fun started(level: String) =
        game().also {
            it.step(GameInput.Start)
            it.say(level)
        }

    @Test
    fun `시작하면 난이도를 묻고 중급을 고르면 중급 미션이 나온다`() =
        runTest {
            val g = game()
            assertTrue(g.step(GameInput.Start).text().contains("초급, 중급, 고급"))
            val plan = g.say("중급")
            assertEquals(Difficulty.NORMAL, g.status().difficulty)
            assertTrue(
                plan.messages
                    .single()
                    .cardId!!
                    .startsWith("mission-normal-"),
            )
        }

    @Test
    fun `난이도 무응답은 한 번 다시 묻고 또 없으면 중급으로 시작하며 바꾸는 법을 알린다`() =
        runTest {
            val g = game()
            g.step(GameInput.Start)
            assertTrue(g.silent().text().contains("초급, 중급, 고급"))
            val plan = g.silent()
            assertEquals(Difficulty.NORMAL, g.status().difficulty)
            assertTrue(plan.text().contains("쉬운 걸로 해 줘"))
        }

    @Test
    fun `쉬운 걸로 해 줘 하면 초급으로 바뀐다`() =
        runTest {
            val g = started("중급")
            g.say("쉬운 걸로 해 줘")
            assertEquals(Difficulty.EASY, g.status().difficulty)
        }

    @Test
    fun `15개 연속 진행해도 같은 미션이 연달아 나오지 않는다`() =
        runTest {
            val g = started("초급")
            var last = g.status().missionTitle
            repeat(15) {
                val mission = OBSERVE_MISSIONS.first { it.title == g.status().missionTitle }
                g.say(mission.right.first())
                assertTrue(g.status().missionTitle != last)
                last = g.status().missionTitle
            }
            assertEquals(15, g.status().found)
        }

    @Test
    fun `찾았다만 말하면 한 번 되묻고 판정 횟수에 넣지 않는다`() =
        runTest {
            val g = started("중급")
            assertTrue(g.say("찾았다").text().contains("뭘 찾았"))
            assertEquals(0, g.status().hints)
            assertFalse(g.say("여기").text().contains("뭘 찾았"))
            assertTrue(logs.any { it.endsWith("result=FOUND_ASK") })
        }

    @Test
    fun `오답은 힌트 다음 격려와 함께 다음 미션으로 넘어가고 틀렸다고 말하지 않는다`() =
        runTest {
            val g = started("중급")
            val first = g.status().missionTitle
            val hint = g.say("바나나").text()
            assertTrue(hint.contains("바나나요! 그것도 멋져요") && hint.contains("힌트"))
            assertEquals(1, g.status().hints)
            val next = g.say("사과")
            assertTrue(next.text().contains("다음 미션"))
            assertTrue(g.status().missionTitle != first)
            assertEquals(1, g.status().played)
            assertEquals(0, g.status().found)
            assertTrue(listOf(hint, next.text()).none { "틀렸" in it || "오답" in it || "실패" in it })
        }

    @Test
    fun `무응답은 기다리고 안내한 뒤 다음 미션으로 넘어간다`() =
        runTest {
            val g = started("중급")
            val first = g.status().missionTitle
            assertTrue(g.silent().messages.isEmpty())
            assertTrue(g.silent(NoAnswerReason.UNHEARD).text().let { t -> Situation.GUIDE.lines.any { it in t } })
            assertTrue(g.silent().text().contains("다음 미션"))
            assertTrue(g.status().missionTitle != first)
        }

    @Test
    fun `응답 없는 미션이 3개 연속이면 휴식을 제안하고 답하면 세어 둔 횟수가 지워진다`() =
        runTest {
            val g = started("중급")
            repeat(2) { repeat(3) { g.silent() } }
            g.say("바나나")
            repeat(2) { repeat(3) { g.silent() } }
            var last: GamePlan? = null
            repeat(3) { last = g.silent() }
            assertEquals(GameNext.SuggestRest, last!!.next)
        }

    @Test
    fun `같은 미션을 다시 안내할 때도 미션 카드 ID를 붙인다`() =
        runTest {
            val g = started("고급")
            val id = OBSERVE_MISSIONS.first { it.title == g.status().missionTitle }.id
            assertEquals(
                CardIds.mission(id),
                g
                    .step(GameInput.Start)
                    .messages
                    .single()
                    .cardId,
            )
        }
}

internal fun GamePlan.text() = messages.joinToString(" ") { it.text }

internal suspend fun ObserveGame.say(vararg heard: String) = step(GameInput.Answer("t", heard.toList()))

private suspend fun ObserveGame.silent(why: NoAnswerReason = NoAnswerReason.SILENT) = step(GameInput.NoAnswer("t", why))
