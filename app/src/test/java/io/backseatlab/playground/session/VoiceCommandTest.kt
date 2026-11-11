package io.backseatlab.playground.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandTest {
    @Test
    fun `정해진 문구를 제어 요청으로 알아듣는다`() {
        listOf("그만할래", "그만하자", "안 할래", "끝낼래", "그만", "끝!").forEach {
            assertEquals(it, VoiceCommand.QUIT, VoiceCommand.parse(it))
        }
        assertEquals(VoiceCommand.REST, VoiceCommand.parse("잠깐 쉬자"))
        assertEquals(VoiceCommand.REST, VoiceCommand.parse("쉬고 싶어"))
        assertEquals(VoiceCommand.RESUME, VoiceCommand.parse("다시 하자"))
        assertEquals(VoiceCommand.RESUME, VoiceCommand.parse("이어서"))
        assertEquals(VoiceCommand.REPEAT, VoiceCommand.parse("다시 말해 줘"))
        assertEquals(VoiceCommand.REPEAT, VoiceCommand.parse("뭐라고 했어?"))
    }

    @Test
    fun `문장 속 그만과 일반 대답은 제어 요청이 아니다`() {
        assertNull(VoiceCommand.parse("엄마 그만 좀 해"))
        assertNull(VoiceCommand.parse("끝말잇기 그만 좀"))
        assertNull(VoiceCommand.parse("국밥집"))
    }
}
