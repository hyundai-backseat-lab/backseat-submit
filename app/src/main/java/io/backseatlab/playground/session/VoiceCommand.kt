package io.backseatlab.playground.session

/** 공통 음성 제어 4종(features.md 공통 정책 "음성 제어"). 정해진 문구만 알아듣는다. */
enum class VoiceCommand {
    REPEAT,
    REST,
    RESUME,
    QUIT,
    ;

    companion object {
        private val repeat = listOf("다시말해", "뭐라고했어", "다시설명해")
        private val rest = listOf("잠깐쉬자", "쉬고싶어")
        private val resume = listOf("다시하자", "계속", "이어서")
        private val quit = listOf("그만할래", "그만하자", "안할래", "끝낼래")

        /** 문장 속 "그만"("엄마 그만 좀 해")은 종료가 아니다. "그만"·"끝"은 그 말만 했을 때만. */
        private val quitAlone = listOf("그만", "끝")

        fun parse(text: String): VoiceCommand? {
            val plain = text.filterNot { it.isWhitespace() || it in "!?.,~" }
            return when {
                quit.any { it in plain } || plain in quitAlone -> QUIT
                rest.any { it in plain } -> REST
                repeat.any { it in plain } -> REPEAT
                resume.any { it in plain } -> RESUME
                else -> null
            }
        }
    }
}
