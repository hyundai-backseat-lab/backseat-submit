package io.backseatlab.playground.game

import io.backseatlab.playground.game.api.CardIds
import io.backseatlab.playground.game.api.Difficulty
import io.backseatlab.playground.game.api.GameInput
import io.backseatlab.playground.game.api.GameMessage
import io.backseatlab.playground.game.api.GameNext
import io.backseatlab.playground.game.api.GamePlan
import io.backseatlab.playground.game.api.GameSession
import io.backseatlab.playground.game.api.GameStatus
import io.backseatlab.playground.game.api.NoAnswerReason
import io.backseatlab.playground.game.api.PlaceSource
import kotlinx.coroutines.CancellationException
import kotlin.random.Random

private const val LEVEL_ASK = "어떤 난이도로 시작할까요? 초급, 중급, 고급!"
private val FOUND_WORDS = listOf("찾았다", "찾았어", "여기", "있다", "있어")
private const val MAX_QUIET_MISSIONS = 3
private const val NEAR_M = 150
private const val FAR_M = 500
private const val PASS_MARGIN_M = 30

/** 관찰 미션 놀이(gameId "observe"). 난이도를 고른 뒤 간판·사물 미션을 하나씩 낸다. */
@Suppress("TooManyFunctions") // 차례 진행을 한 클래스에 모아 둔다: 흐름을 한 파일에서 읽게 하려고
class ObserveGame(
    private val missions: List<Mission> = OBSERVE_MISSIONS,
    random: Random = Random,
    private val places: PlaceSource = PlaceSource { emptyList() },
    private val log: (String) -> Unit = {},
) : GameSession {
    private var level: Difficulty? = null
    private var levelRetried = false
    private var current: Mission? = null
    private val deck = MissionDeck(missions, random)
    private var lastId: String? = null
    private var turn = 0
    private val turnId: () -> String = { "t${++turn}" }
    private var missionNo = 0
    private var found = 0
    private var played = 0
    private var hints = 0
    private var streak = 0
    private var lastPlace: String? = null
    private var nearest = 0
    private val dialogue = Dialogue(random)

    // 미션 하나 안에서 센다
    private var wrongs = 0
    private var askedFound = false
    private var quiet = 0

    // 아이가 말을 안 한 채 지나간 미션이 연속 몇 개인지
    private var quietMissions = 0

    override suspend fun step(input: GameInput): GamePlan =
        when {
            input == GameInput.Arrived -> arrive()
            level == null -> chooseLevel(input)
            input == GameInput.Start -> announce(checkNotNull(current), "")
            !hitsAnswer(input) && passed(checkNotNull(current)) -> reveal(checkNotNull(current))
            input is GameInput.Answer -> answer(checkNotNull(current), input.candidates)
            input is GameInput.NoAnswer -> noAnswer(checkNotNull(current), input.reason)
            else -> error("처리하지 못하는 입력: $input")
        }

    override fun status() = GameStatus(level, missionNo, current?.title, found, played, hints)

    override suspend fun close() = Unit

    /** 새 미션 없이 마무리 문장만 말한다. n은 이번 이용에서 찾은 미션 수. */
    private fun arrive() = GamePlan(listOf(GameMessage("벌써 도착했어요! 오늘 미션 ${found}개 찾았어요.")), GameNext.Finished)

    private suspend fun chooseLevel(input: GameInput): GamePlan {
        val picked = (input as? GameInput.Answer)?.candidates?.firstNotNullOfOrNull(::levelOf)
        return when {
            input == GameInput.Start -> await(LEVEL_ASK)
            picked != null -> pickLevel(picked, "${levelName(picked)}으로 게임을 시작할게요! ")
            !levelRetried -> {
                levelRetried = true
                await(LEVEL_ASK)
            }
            else -> pickLevel(Difficulty.NORMAL, "중급으로 시작할게요. 쉬운 걸로 해 줘, 라고 말하면 바꿀 수 있어요. ")
        }
    }

    private suspend fun pickLevel(
        picked: Difficulty,
        intro: String,
    ): GamePlan {
        level = picked
        return nextMission(intro)
    }

    private suspend fun answer(
        mission: Mission,
        heard: List<String>,
    ): GamePlan {
        val text = heard.firstOrNull().orEmpty()
        if (heard.all { it.isBlank() }) return noAnswer(mission, NoAnswerReason.UNHEARD)
        quiet = 0
        quietMissions = 0
        val kind = classify(text)
        return when {
            "쉬운" in text && level != Difficulty.EASY -> {
                level = Difficulty.EASY
                deck.reset()
                nextMission("쉬운 걸로 바꿀게요. ")
            }
            kind == Situation.PARENT || kind == Situation.ETA -> {
                log(result(mission, text, "PARENT"))
                await(dialogue.say(kind), mission.prompt)
            }
            !askedFound && heard.all { h -> FOUND_WORDS.fold(h) { s, w -> s.replace(w, "") }.isBlank() } -> {
                askedFound = true
                log(result(mission, text, "FOUND_ASK"))
                await(dialogue.say(Situation.FOUND_ASK), mission.prompt)
            }
            heard.any { h -> mission.answers.any { it in h } } -> correct(mission, text)
            kind != null -> {
                log(result(mission, text, kind.name))
                await("${dialogue.say(kind)} ${mission.prompt}", mission.prompt)
            }
            wrongs == 0 -> {
                wrongs++
                hints++
                log(result(mission, text, "WRONG"))
                await("$text${yo(text)}! 그것도 멋져요. 힌트를 줄게요. ${mission.hint}", mission.prompt)
            }
            else -> {
                log(result(mission, text, "WRONG"))
                skip()
            }
        }
    }

    private suspend fun correct(
        mission: Mission,
        text: String,
    ): GamePlan {
        log(result(mission, text, "CORRECT"))
        val stage =
            when {
                found == 0 -> Situation.PRAISE_FIRST
                wrongs > 0 -> Situation.PRAISE_WANDER
                streak >= 2 -> Situation.PRAISE_STREAK
                else -> Situation.PRAISE_NORMAL
            }
        found++
        played++
        streak++
        return nextMission(dialogue.say(stage) + " ")
    }

    private suspend fun skip() = skipWith(dialogue.say(Situation.ENCOURAGE) + " ")

    private suspend fun skipWith(intro: String): GamePlan {
        played++
        streak = 0
        return nextMission(intro)
    }

    private suspend fun noAnswer(
        mission: Mission,
        reason: NoAnswerReason,
    ): GamePlan {
        log(result(mission, "", reason.name))
        quiet++
        return when (quiet) {
            1 -> GamePlan(emptyList(), GameNext.AwaitAnswer(turnId(), mission.prompt))
            2 -> await("${dialogue.say(Situation.GUIDE)} ${mission.prompt}", mission.prompt)
            else -> {
                quietMissions++
                if (quietMissions >= MAX_QUIET_MISSIONS) {
                    played++
                    streak = 0
                    quietMissions = 0
                    prepareMission()
                    GamePlan(emptyList(), GameNext.SuggestRest)
                } else {
                    skip()
                }
            }
        }
    }

    private suspend fun nextMission(intro: String): GamePlan = announce(prepareMission(), intro)

    private fun announce(
        mission: Mission,
        intro: String,
    ) = GamePlan(
        listOf(GameMessage(intro + mission.prompt, CardIds.mission(mission.id).takeIf { mission.hasCard })),
        GameNext.AwaitAnswer(turnId(), mission.prompt),
    )

    private suspend fun prepareMission(): Mission {
        wrongs = 0
        askedFound = false
        quiet = 0
        missionNo++
        val mission = placeMission() ?: basicMission()
        current = mission
        lastId = mission.id
        return mission
    }

    private fun hitsAnswer(input: GameInput) =
        input is GameInput.Answer && input.candidates.any { h -> checkNotNull(current).answers.any { it in h } }

    /** 장소 미션의 장소가 다시 멀어졌는가(가장 가까웠던 거리보다 30m 넘게). 목록에서 사라진 경우는 판단하지 않는다. */
    private suspend fun passed(mission: Mission): Boolean {
        val now =
            if (mission.id.startsWith("place-")) {
                runCatching { places.ahead(null) }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrDefault(emptyList())
                    .firstOrNull { it.name == mission.answers.first() }
            } else {
                null
            }
        nearest = minOf(nearest, now?.distanceM ?: nearest)
        return now != null && now.distanceM > nearest + PASS_MARGIN_M
    }

    private suspend fun reveal(mission: Mission): GamePlan {
        val name = mission.answers.first()
        log("MISSION source=PLACE result=PASSED")
        return skipWith("방금 지나간 게 $name${if (yo(name) == "이요") "이었어요" else "였어요"}! ")
    }

    private fun basicMission() = deck.next(level, lastId).also { log("MISSION source=BASIC category=${it.level.name}") }

    /** 앞 150~500m 장소가 있으면 그 장소 미션을, 없거나 실패면 null(데이터 없는 미션으로 대체). */
    private suspend fun placeMission(): Mission? {
        val place =
            runCatching { places.ahead(null) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrDefault(emptyList())
                .filter { it.distanceM in NEAR_M..FAR_M && it.name != lastPlace }
                .minByOrNull { it.distanceM }
        lastPlace = place?.name ?: lastPlace
        nearest = place?.distanceM ?: nearest
        return place?.let {
            log("MISSION source=PLACE category=${it.category}")
            Mission(
                id = "place-$missionNo",
                level = checkNotNull(level),
                title = "'${it.name}'",
                prompt = "곧 '${it.name}' 같은 가게가 보일지도 몰라요. 찾아볼까요?",
                hint = "${it.category} 가게예요",
                answers = listOf(it.name),
                hasCard = false,
            )
        }
    }

    private fun await(
        say: String,
        prompt: String = say,
    ) = GamePlan(listOf(GameMessage(say)), GameNext.AwaitAnswer(turnId(), prompt))
}

/** 음성 인식이 "초급"을 "초금"·"조급"처럼 바꿔 주는 일이 있어 비슷한 소리도 받는다. */
private fun levelOf(text: String): Difficulty? {
    val t = text.filterNot { it.isWhitespace() }
    return when {
        listOf("초급", "초금", "조급", "초그", "쉬운", "쉬워", "처음").any { it in t } -> Difficulty.EASY
        listOf("중급", "중금", "중간", "보통").any { it in t } -> Difficulty.NORMAL
        listOf("고급", "고금", "어려운", "어려워").any { it in t } -> Difficulty.HARD
        else -> null
    }
}

private fun result(
    mission: Mission,
    heard: String,
    result: String,
) = "GAME mission=${mission.id} heard=\"$heard\" result=$result"

/** 난이도별로 섞어서 한 바퀴 돌고, 바퀴가 바뀔 때도 같은 미션이 연달아 나오지 않게 한다. */
private class MissionDeck(
    private val missions: List<Mission>,
    private val random: Random,
) {
    private var queue = mutableListOf<Mission>()

    fun next(
        level: Difficulty?,
        lastId: String?,
    ): Mission {
        if (queue.isEmpty()) {
            queue = missions.filter { it.level == level }.shuffled(random).toMutableList()
            if (queue.size > 1 && queue.first().id == lastId) queue.add(queue.removeAt(0))
        }
        return queue.removeAt(0)
    }

    fun reset() = queue.clear()
}

private fun levelName(level: Difficulty) =
    when (level) {
        Difficulty.EASY -> "초급"
        Difficulty.NORMAL -> "중급"
        Difficulty.HARD -> "고급"
    }
