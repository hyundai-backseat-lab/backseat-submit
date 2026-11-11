package io.backseatlab.playground.game

import io.backseatlab.playground.game.api.Difficulty
import io.backseatlab.playground.game.api.GameInput
import io.backseatlab.playground.game.api.GamePlan
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DialogueTest {
    private val banned = listOf("틀렸", "오답", "실패")

    private suspend fun playing(): ObserveGame =
        ObserveGame(OBSERVE_MISSIONS, Random(3)).also {
            it.step(GameInput.Start)
            it.step(GameInput.Answer("t", listOf("중급")))
        }

    private suspend fun ObserveGame.say(heard: String): GamePlan = step(GameInput.Answer("t", listOf(heard)))

    private fun GamePlan.text() = messages.joinToString(" ") { it.text }

    @Test
    fun `배고파 화장실 멀미는 부모에게 넘기고 판정하지 않는다`() =
        runTest {
            listOf("배고파", "화장실 가고 싶어", "멀미 나").forEach {
                val g = playing()
                assertTrue(g.say(it).text().contains("엄마 아빠"))
                assertEquals(0, g.status().hints)
            }
            assertTrue(playing().say("언제 도착해?").text().contains("엄마 아빠한테 물어봐요"))
        }

    @Test
    fun `부모 말이 정답보다 먼저 처리된다`() =
        runTest {
            val g = playing()
            val m = OBSERVE_MISSIONS.first { it.title == g.status().missionTitle }
            assertTrue(g.say("배고파 ${m.right.first()}").text().contains("엄마 아빠"))
            assertEquals(0, g.status().found)
        }

    @Test
    fun `그 외 발화는 분류대로 반응하고 판정 횟수에 넣지 않는다`() =
        runTest {
            val cases =
                mapOf(
                    "심심해" to Situation.BORED,
                    "졸려" to Situation.TIRED,
                    "신난다" to Situation.JOY,
                    "저건 뭐야" to Situation.QUESTION,
                    "저기 강아지 봤어" to Situation.OTHER_FIND,
                    "음" to Situation.MUTTER,
                )
            cases.forEach { (said, situation) ->
                val g = playing()
                val reply = g.say(said).text()
                assertTrue("$said → $reply", situation.lines.any { it in reply })
                assertEquals(0, g.status().hints)
                assertEquals(0, g.status().played)
            }
        }

    @Test
    fun `칭찬은 첫 발견 3연속 헤매다 찾음 그 외 순서로 고른다`() =
        runTest {
            val g = playing()

            suspend fun right(): String {
                val m = OBSERVE_MISSIONS.first { it.title == g.status().missionTitle }
                return g.say(m.right.first()).text()
            }

            fun isPraise(
                said: String,
                s: Situation,
            ) = s.lines.any { it in said }
            assertTrue(isPraise(right(), Situation.PRAISE_FIRST))
            assertTrue(isPraise(right(), Situation.PRAISE_NORMAL))
            assertTrue(isPraise(right(), Situation.PRAISE_STREAK))
            g.say("바나나")
            assertTrue(isPraise(right(), Situation.PRAISE_WANDER))
        }

    @Test
    fun `칭찬 5회 연속에서 같은 문장이 이어지지 않고 금지 표현과 복수 질문이 없다`() =
        runTest {
            val g = playing()
            val said = mutableListOf<String>()
            repeat(5) {
                val m = OBSERVE_MISSIONS.first { it.title == g.status().missionTitle }
                said += g.say(m.right.first()).text()
                said += g.say("바나나").text()
                said += g.say("사과").text()
            }
            said.zipWithNext().forEach { (a, b) -> assertTrue(a != b) }
            said.forEach { line ->
                assertTrue(banned.none { it in line })
                assertTrue(line, line.count { it == '?' } <= 1)
            }
        }

    @Test
    fun `Dialogue는 직전 문장과 같은 첫 단어를 피한다`() {
        val d = Dialogue(Random(1))
        val lines = List(20) { d.say(Situation.PRAISE_NORMAL) }
        lines.zipWithNext().forEach { (a, b) -> assertFalse(a.substringBefore(' ') == b.substringBefore(' ')) }
    }

    @Test
    fun `받침에 따라 이요 요를 붙인다`() {
        assertEquals("이요", yo("국밥집"))
        assertEquals("요", yo("바나나"))
        assertEquals("요", yo(""))
        assertEquals("요", yo("abc"))
    }

    @Test
    fun `대사는 상황마다 2개 이상 4개 이하다`() {
        Situation.entries.forEach { assertTrue(it.name, it.lines.size in 2..4) }
        assertEquals(Difficulty.entries.size, 3)
    }
}
