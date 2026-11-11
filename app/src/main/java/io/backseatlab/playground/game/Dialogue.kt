package io.backseatlab.playground.game

import kotlin.random.Random

/** 앱이 말하는 상황. 상황마다 대사 2~4개를 두고 [Dialogue]가 골라 쓴다. 질문은 한 번에 하나뿐이다. */
enum class Situation(
    val lines: List<String>,
) {
    PRAISE_FIRST(listOf("와, 첫 번째로 찾았어요! 대단해요!", "첫 미션 성공! 눈이 정말 좋네요!", "처음으로 찾아냈어요! 멋져요!")),
    PRAISE_STREAK(listOf("또 찾았네요! 연속으로 척척이에요!", "계속 맞히고 있어요! 정말 잘해요!", "와, 줄줄이 성공이에요! 대단해요!")),
    PRAISE_WANDER(listOf("끝까지 찾아냈네요! 포기 안 해서 멋져요!", "헤매다가 해냈어요! 정말 대단해요!", "드디어 찾았어요! 끈기 최고예요!")),
    PRAISE_NORMAL(listOf("맞아요, 잘 찾았어요!", "정답이에요! 눈썰미가 좋아요!", "좋아요, 찾아냈네요!", "오, 정확해요!")),
    ENCOURAGE(listOf("괜찮아요, 다음 미션으로 가요.", "아쉽지만 괜찮아요. 다음 미션을 찾아봐요.", "잘하고 있어요. 다음 미션으로 넘어가요.")),
    FOUND_ASK(listOf("뭘 찾았는지 말해 줄래요?", "오, 찾았군요! 무엇인지 알려 줄래요?")),
    GUIDE(listOf("천천히 말해도 돼요.", "급하지 않아요. 편하게 말해 줘요.", "조금 더 보고 말해도 돼요.")),
    MUTTER(listOf("생각 중이군요. 기다릴게요.", "천천히 생각해 봐도 괜찮아요.", "혼잣말이 들렸어요. 편하게 말해도 돼요.")),
    OTHER_FIND(listOf("오, 다른 것도 봤군요! 눈이 밝아요.", "멋진 발견이에요! 그것도 신기해요.", "와, 그것도 봤어요? 대단해요!")),
    QUESTION(listOf("좋은 질문이에요. 같이 찾아보면서 생각해 봐요.", "궁금한 게 많군요. 엄마 아빠한테 물어봐도 좋아요.", "신기하죠. 일단 미션을 이어 가 봐요.")),
    BORED(listOf("조금만 더 하면 재밌는 게 나올 거예요.", "눈을 크게 뜨고 찾아봐요. 숨은 게 많아요.", "쉬운 걸로 해 줘, 라고 말하면 쉬운 미션으로 바꿀 수 있어요.")),
    JOY(listOf("신나는군요! 저도 기뻐요.", "좋아요, 같이 신나게 찾아봐요.", "와, 기분 좋아 보여요!")),
    TIRED(listOf("피곤하면 잠깐 쉬자, 라고 말해 봐요.", "힘들면 쉬어 가도 괜찮아요.", "졸리면 잠깐 쉬어도 돼요.")),
    PARENT(listOf("엄마 아빠한테 말해 줄래요?", "그건 엄마 아빠가 도와줄 수 있어요. 말해 줄래요?")),
    ETA(listOf("도착 시간은 엄마 아빠한테 물어봐요.", "엄마 아빠한테 물어봐요.")),
}

/** 최근에 한 말과 겹치지 않게 대사를 고른다. */
class Dialogue(
    private val random: Random,
) {
    private val recent = ArrayDeque<String>()

    fun say(situation: Situation): String {
        val fresh = situation.lines.filter { it !in recent && firstWord(it) != recent.lastOrNull()?.let(::firstWord) }
        val pool = fresh.ifEmpty { situation.lines.filter { it != recent.lastOrNull() } }.ifEmpty { situation.lines }
        val line = pool.random(random)
        recent.addLast(line)
        if (recent.size > RECENT) recent.removeFirst()
        return line
    }

    private fun firstWord(line: String) = line.substringBefore(' ')

    private companion object {
        const val RECENT = 3
    }
}

/** 아이 말을 어떤 반응으로 받을지 가르는 규칙(AI 없음). 순서가 곧 우선순위다. */
fun classify(text: String): Situation? =
    when {
        listOf("배고", "화장실", "멀미", "토할", "오줌").any { it in text } -> Situation.PARENT
        "도착" in text && listOf("언제", "얼마", "아직", "몇").any { it in text } -> Situation.ETA
        listOf("심심", "지루", "재미없").any { it in text } -> Situation.BORED
        listOf("졸려", "피곤", "힘들").any { it in text } -> Situation.TIRED
        listOf("신나", "신난", "재밌", "최고").any { it in text } -> Situation.JOY
        "?" in text || listOf("뭐야", "뭐예요", "왜", "어디").any { it in text } -> Situation.QUESTION
        listOf("봤", "있다", "있어").any { it in text } -> Situation.OTHER_FIND
        text.length <= 1 || text in listOf("음", "어", "아", "으음", "흠") -> Situation.MUTTER
        else -> null
    }

/** 받침이 있으면 "이요", 없으면 "요". 한글이 아니면 "요". */
fun yo(word: String): String {
    val last = word.lastOrNull() ?: return "요"
    val hasFinal = last in '가'..'힣' && (last - '가') % 28 != 0
    return if (hasFinal) "이요" else "요"
}
