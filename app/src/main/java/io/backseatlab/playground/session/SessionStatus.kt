package io.backseatlab.playground.session

import io.backseatlab.playground.game.api.CardIds
import io.backseatlab.playground.game.api.Difficulty
import io.backseatlab.playground.game.api.GameStatus

/** 멈춤 사유(features.md 공통 정책). 하나라도 있으면 멈춤이고, 모두 풀리면 1초 뒤 재개한다. */
enum class PauseReason { TURN, HAZARD, BRAKE, FOCUS_LOSS }

/** 시작할 수 없는 이유(F-04-01). 화면이 이유와 필요한 조치를 안내한다. */
enum class StartBlock { NOT_PARKED, WARNING, NO_MIC, VOICE_NOT_READY }

enum class EndReason { USER, WARNING, ARRIVED, REST_TIMEOUT, FAILURE, LEFT }

enum class Phase { IDLE, GUIDING, LISTENING, THINKING, RESTING, PAUSED, ENDED }

/** 화면이 이용 상태에서 알아야 하는 것. */
data class SessionStatus(
    val phase: Phase = Phase.IDLE,
    val pauses: Set<PauseReason> = emptySet(),
    val endReason: EndReason? = null,
    val result: SessionResult? = null,
    val play: PlayStatus? = null,
    /** 화면 버튼으로 답할 수 있는 응답 차례가 열려 있는지. */
    val answerOpen: Boolean = false,
)

/** 주행 중 요약 패널(SC-04)에 쓰는 게임 진행 값. ui가 게임 계약을 모르도록 세션이 옮겨 준다. */
data class PlayStatus(
    val difficulty: String?,
    val missionNo: Int,
    val missionTitle: String?,
    val found: Int,
) {
    companion object {
        fun from(status: GameStatus) =
            PlayStatus(
                difficulty =
                    when (status.difficulty) {
                        Difficulty.EASY -> "초급"
                        Difficulty.NORMAL -> "중급"
                        Difficulty.HARD -> "고급"
                        null -> null
                    },
                missionNo = status.missionNo,
                missionTitle = status.missionTitle,
                found = status.found,
            )
    }
}

/** 끝난 이용의 결과(SC-07). 게임의 status()에서 가져오며 이 이용의 메모리에만 있다. */
data class SessionResult(
    val found: Int,
    val played: Int,
    val hints: Int,
)

/** 세션이 직접 하는 말. 게임 대사가 아니라 공통 흐름 안내다(말투: 해요체, 1~2문장, 질문 하나). */
object SessionLines {
    val intro =
        listOf(
            "안녕하세요!",
            "오늘은 창밖을 보면서 같이 놀아요.",
            "다시 듣고 싶으면 '다시 말해 줘'라고 말해요. 쉬고 싶으면 '잠깐 쉬자', 끝내고 싶으면 '그만할래'라고 하면 돼요.",
        )
    const val REST = "좋아요, 잠깐 쉬어요. '다시 하자'라고 하면 이어서 해요."
    const val REST_HELP = "'다시 하자'라고 하면 이어서 하고, '그만할래'라고 하면 끝나요."
    const val RESUME = "다시 시작해요!"
    const val CONFIRM_QUIT = "그만할까요?"

    /** 아이가 직접 그만하겠다고 확인했을 때만 하는 마무리 인사. n은 이번 이용에서 맞힌 수. */
    fun farewell(found: Int) = if (found == 0) "다음에 또 같이 놀아요!" else "오늘도 대단해요! ${found}개 맞췄어요! 다음에 또 같이 놀아요!"

    /** 주차 중 난이도 버튼 글자. 게임은 같은 글자를 음성 답과 똑같이 처리한다(work-split 약속). */
    val DIFFICULTIES = listOf("초급", "중급", "고급")

    /** 목소리 카드가 없을 때 TTS로 대신 하는 말. */
    fun cardText(cardId: String) =
        when (cardId) {
            CardIds.WIPER -> "와이퍼 출동!"
            CardIds.ARRIVED -> "벌써 도착!"
            CardIds.BYE -> "다녀오세요!"
            else -> ""
        }
}
