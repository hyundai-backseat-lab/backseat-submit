package io.backseatlab.playground.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WordChainTest {
    private val logs = mutableListOf<String>()
    private val links =
        mapOf(
            "빨개" to listOf("사과", "딸기"),
            "사과" to listOf("달아"),
            "달아" to listOf("사탕", "사과"),
            "사탕" to listOf("동그래"),
            "동그래" to listOf("공"),
        )

    private fun chain() = WordChain(links, Random(1)) { logs += it }

    private fun WordChain.hear(text: String) = answer(listOf(text))

    @Test
    fun `판을 열면 원숭이 엉덩이는 빨개요`() {
        assertEquals("이번엔 말잇기 해 볼까요? 원숭이 엉덩이는 빨개요!", chain().open())
    }

    @Test
    fun `연상어를 말하면 칭찬하고 이어서 말하고 OK를 남긴다`() {
        val c = chain()
        val r = c.hear("빨가면 사과")
        assertFalse(r.done)
        assertTrue(r.line, r.line.endsWith("사과는 달아요!"))
        assertEquals("GAME word turn=1 heard=\"빨가면 사과\" result=OK", logs.single())
        assertEquals("사과는 달아요!", c.reopen())
    }

    @Test
    fun `대답이 없으면 한 번 다시 묻고 두 번째는 힌트를 주고 기다리며 세 번째에 넘어간다`() {
        val c = chain()
        assertEquals("다시 말해 줄래요?", c.silent().line)
        val hint = c.silent()
        assertEquals("사과 같은 말도 좋아요! 다시 말해 볼래요?", hint.line)
        assertEquals("원숭이 엉덩이는 빨개요!", c.reopen())
        assertTrue(c.silent().line.startsWith("사과도 좋아요!"))
        assertEquals(listOf("RETRY", "HINT", "HINT"), logs.map { it.substringAfter("result=") })
    }

    @Test
    fun `힌트 뒤에 아이가 말한 힌트 단어를 같은 질문의 답으로 받는다`() {
        val c = chain()
        c.silent()
        c.silent()
        val r = c.hear("빨가면 사과")
        assertTrue(r.line, r.line.endsWith("사과는 달아요!"))
        assertTrue(logs.last().endsWith("result=OK"))
    }

    @Test
    fun `사전에 없는 말도 이미 나온 말이 아니면 받아 준다`() {
        val c = chain()
        val r = c.hear("빨가면 고추")
        assertFalse(r.done)
        assertFalse(r.line, r.line.contains("다시 말해"))
        assertTrue(logs.single().endsWith("result=ANY"))
    }

    @Test
    fun `같은 단어를 또 말하면 그건 아까 나왔어요`() {
        val c = chain()
        c.hear("사과") // 이제 달아
        assertEquals("그건 아까 나왔어요!", c.hear("사과").line)
        assertTrue(logs.last().endsWith("result=DUP"))
    }

    @Test
    fun `세 차례가 끝나면 마무리하고 판이 끝난다`() {
        val c = chain()
        c.hear("사과")
        c.hear("사탕")
        val r = c.hear("공")
        assertTrue(r.done)
        assertTrue(r.line.endsWith("우리 정말 잘했어요!"))
    }

    @Test
    fun `이을 말이 없거나 힌트도 없으면 끝난다`() {
        val c = WordChain(mapOf("빨개" to listOf("사과"), "사과" to emptyList()), Random(1))
        assertTrue(c.hear("사과").done)
        val d = WordChain(mapOf("빨개" to emptyList()), Random(1))
        d.silent()
        assertTrue(d.silent().done)
    }

    @Test
    fun `은 는을 받침에 맞춘다`() {
        assertEquals("얼음은", eunNeun("얼음"))
        assertEquals("사과는", eunNeun("사과"))
    }
}
