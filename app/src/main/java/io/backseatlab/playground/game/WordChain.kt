package io.backseatlab.playground.game

import kotlin.random.Random

/** 말잇기 한 판의 결과. [done]이면 판이 끝났고 [line]은 마무리 말이다. */
class ChainReply(
    val line: String,
    val done: Boolean,
)

/**
 * "원숭이 엉덩이는 빨개요!" 말잇기 한 판([TURNS]차례). 사전([links])은 단어 → 이어 말할 단어들이다.
 * 말은 칭찬 한 문장 + 이어 말하기 한 문장, 모두 2문장 이하로 한다.
 */
class WordChain(
    private val links: Map<String, List<String>>,
    random: Random,
    private val log: (String) -> Unit = {},
) {
    private val dialogue = Dialogue(random)
    private var current = START
    private val used = mutableSetOf(START)
    private var turn = 1
    private var fails = 0
    private var line = "원숭이 엉덩이는 ${START}${yo(START)}!"

    /** 판을 연다. 처음 말한 뒤 아이 차례. */
    fun open(): String = "이번엔 말잇기 해 볼까요? $line"

    /** 아이 차례를 다시 안내해야 할 때(휴식 뒤 등): 마지막으로 한 말만 다시 한다. */
    fun reopen(): String = line

    /**
     * 아이 대답. 사전에 있는 말이면 OK, 사전에 없어도 이미 나온 말이 아니면 받아 준다(ANY).
     * 아이가 고른 말을 틀렸다고 하지 않는다(F-24 관대한 판정).
     */
    fun answer(heard: List<String>): ChainReply {
        val hits = links[current].orEmpty().filter { w -> heard.any { w in it } }
        val word = hits.filter { it !in used }.maxByOrNull { it.length }
        val text = heard.firstOrNull().orEmpty().trim()
        val own = text.substringAfterLast(' ')
        return when {
            word != null -> advance(word, dialogue.say(Situation.PRAISE_NORMAL) + " ", text, "OK")
            hits.isNotEmpty() || own in used -> miss("DUP", text)
            own.isNotEmpty() -> advance(own, dialogue.say(Situation.PRAISE_NORMAL) + " ", text, "ANY")
            else -> miss("RETRY", text)
        }
    }

    /** 무응답·못 알아들음. 한 번은 다시 묻고, 두 번째는 힌트를 주고 기다리고, 세 번째는 힌트 말로 넘어간다. */
    fun silent(): ChainReply = miss("RETRY", "")

    private fun miss(
        kind: String,
        heard: String,
    ): ChainReply {
        fails++
        val hint = links[current].orEmpty().firstOrNull { it !in used }
        return when {
            fails == 1 -> {
                log(entry(heard, kind))
                ChainReply(if (kind == "DUP") "그건 아까 나왔어요!" else "다시 말해 줄래요?", false)
            }
            hint == null -> finish("다 나왔네요! ")
            fails == 2 -> {
                log(entry(heard, "HINT"))
                ChainReply("$hint 같은 말도 좋아요! 다시 말해 볼래요?", false)
            }
            else -> advance(hint, "${hint}도 좋아요! ", heard, "HINT")
        }
    }

    private fun advance(
        word: String,
        prefix: String,
        heard: String,
        kind: String,
    ): ChainReply {
        log(entry(heard, kind))
        used += word
        fails = 0
        // 사전에 없는 새 단어(젤리 등)여도 판이 끝나지 않게 기본 말로 잇는다
        val next = (links[word] ?: FALLBACK).firstOrNull { it !in used }
        if (next == null || turn >= TURNS) return finish(prefix)
        turn++
        current = next
        used += next
        line = "${eunNeun(word)} $next${yo(next)}!"
        return ChainReply(prefix + line, false)
    }

    private fun finish(prefix: String) = ChainReply("${prefix}우리 정말 잘했어요!", true)

    private fun entry(
        heard: String,
        kind: String,
    ) = "GAME word turn=$turn heard=\"$heard\" result=$kind"

    private companion object {
        const val START = "빨개"
        const val TURNS = 3
        val FALLBACK = listOf("맛있어", "커", "작아", "동그래")
    }
}

/** "사과는", "얼음은"처럼 받침에 맞춰 은/는을 붙인다. */
internal fun eunNeun(word: String): String {
    val last = word.last()
    val hasFinal = last in '가'..'힣' && (last - '가') % 28 != 0
    return word + if (hasFinal) "은" else "는"
}
