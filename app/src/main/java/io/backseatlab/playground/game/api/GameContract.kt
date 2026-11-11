package io.backseatlab.playground.game.api

/**
 * 게임 연결 계약(docs/features.md "게임 연결 계약" + docs/work-split.md 확장 2가지).
 * session(개발자 A)과 game(개발자 B)이 이 파일만 보고 따로 개발한다. 바꾸려면 두 사람이 합의하고 별도 PR로.
 */
interface GameSession {
    suspend fun step(input: GameInput): GamePlan

    /** 화면(SC-04·SC-07)과 도착 마무리에 쓰는 현재 진행 값. 언제 불러도 즉시 돌려준다. */
    fun status(): GameStatus

    suspend fun close()
}

sealed interface GameInput {
    data object Start : GameInput

    /** 인식 후보 여러 개. 주차 중 화면 버튼(난이도 등)도 버튼 글자를 후보 하나로 담아 보낸다. */
    data class Answer(
        val turnId: String,
        val candidates: List<String>,
    ) : GameInput

    /** 14초 무응답(SILENT) 또는 빈 결과·오류(UNHEARD). */
    data class NoAnswer(
        val turnId: String,
        val reason: NoAnswerReason,
    ) : GameInput

    /** 목적지 도착. 게임은 새 미션 없이 마무리 문장 + Finished를 돌려준다. */
    data object Arrived : GameInput
}

enum class NoAnswerReason { SILENT, UNHEARD }

/** cardId가 있고 카드가 저장돼 있으면 TTS 대신 아이 목소리 카드를 재생한다(CardIds 참고). */
data class GameMessage(
    val text: String,
    val cardId: String? = null,
)

data class GamePlan(
    val messages: List<GameMessage>,
    val next: GameNext,
)

sealed interface GameNext {
    data class AwaitAnswer(
        val turnId: String,
        val prompt: String,
    ) : GameNext

    /** 응답 없는 미션 3개 연속. 세션이 휴식으로 바꾼다. */
    data object SuggestRest : GameNext

    data object Finished : GameNext
}

enum class Difficulty { EASY, NORMAL, HARD }

/** 확장 ①: 화면·결과에 쓰는 값. 아직 정하지 않았으면 null·0. */
data class GameStatus(
    val difficulty: Difficulty? = null,
    val missionNo: Int = 0,
    val missionTitle: String? = null,
    val found: Int = 0,
    val played: Int = 0,
    val hints: Int = 0,
)

/** 확장 ②: 카드 ID 규칙. 미션 카드는 게임이, 차량 반응 카드는 세션이 쓴다. */
object CardIds {
    const val WIPER = "vehicle-wiper"
    const val ARRIVED = "vehicle-arrived"
    const val BYE = "vehicle-bye"

    fun mission(missionId: String) = "mission-$missionId"
}

/** 장소 중계 API 결과 하나. 좌표는 게임에 넘기지 않는다. */
data class Place(
    val name: String,
    val category: String,
    val distanceM: Int,
)

/** 위치·진행 방향은 구현(place/, di/)이 알아서 넣는다. 실패·동의 전·서버 없음이면 빈 목록. */
fun interface PlaceSource {
    suspend fun ahead(category: String?): List<Place>
}
