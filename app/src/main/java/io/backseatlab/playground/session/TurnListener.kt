package io.backseatlab.playground.session

import io.backseatlab.playground.pleos.ListenResult
import io.backseatlab.playground.pleos.PleosClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/** 한 번의 듣기 결과. [Silent]는 기다린 시간 안에 아무 결과가 없었다는 뜻이다. */
sealed interface Heard {
    data class Text(
        val text: String,
    ) : Heard

    /** STT 오류·빈 결과·SDK 응답 없음(F-01-03). */
    data object Unheard : Heard

    data object Silent : Heard

    /** 권한 상실·서비스 불가처럼 음성 기능을 쓸 수 없는 오류. 장애 종료한다(F-04-07). */
    data class Broken(
        val code: String,
    ) : Heard
}

/** 개별 발화를 못 알아들은 것과 구분해야 하는 음성 기능 장애 코드(SDK ErrorType 이름 + 앱의 INIT). */
private val FATAL_CODES =
    setOf("INIT", "PERMISSION_DENIED", "UNAUTHENTICATED", "UNAVAILABLE_LANGUAGE", "NOT_SUPPORTED_API")

fun isVoiceBroken(code: String) = code in FATAL_CODES

/**
 * 응답 차례의 듣기. STT가 ready가 된 뒤에만 신호(효과음)를 내고, 신호 뒤부터 [waitMs]를 센다.
 */
class TurnListener(
    private val pleos: PleosClient,
    private val clock: () -> Long,
    private val log: (String) -> Unit,
) {
    suspend fun listen(
        attempt: Int,
        cue: Boolean,
        waitMs: Long,
    ): Heard =
        coroutineScope {
            val ready = CompletableDeferred<Unit>()
            val sawReady = AtomicBoolean(false)
            val startedAt = clock()
            val first =
                async {
                    pleos.listen {
                        sawReady.set(true)
                        ready.complete(Unit)
                    }
                }
            // 듣기가 ready 없이 끝나도(실패) 여기서 멈추지 않게 한다
            first.invokeOnCompletion { ready.complete(Unit) }
            ready.await()
            if (sawReady.get() && cue) {
                log("SESSION cue attempt=$attempt ready_ms=${clock() - startedAt}")
                pleos.beep()
            }
            val result = withTimeoutOrNull(waitMs) { first.await() }
            if (result == null) {
                first.cancel()
                pleos.stopListening()
            }
            when (result) {
                null -> Heard.Silent
                is ListenResult.Heard -> if (result.text.isBlank()) Heard.Unheard else Heard.Text(result.text.trim())
                is ListenResult.Failed -> if (isVoiceBroken(result.code)) Heard.Broken(result.code) else Heard.Unheard
            }
        }
}

/**
 * 앱 자신의 목소리가 인식된 것(반향)인지 본다. 앱이 말하는 중이거나 끝난 직후에 들은 문장이
 * 지금 말한 문장 안에 들어 있으면 반향이다(예: 안내 속 "그만할래라고 말해요"가 종료 요청으로 접수되는 것을 막는다).
 */
class EchoFilter(
    private val clock: () -> Long,
) {
    private var spoken = ""
    private var speakingNow = false
    private var tailUntil = 0L

    fun speaking(text: String) {
        spoken = plain(text)
        speakingNow = true
    }

    fun done() {
        speakingNow = false
        tailUntil = clock() + TAIL_MS
    }

    fun isEcho(heard: String): Boolean {
        val text = plain(heard)
        val recent = speakingNow || clock() <= tailUntil
        return recent && text.length >= MIN_LENGTH && spoken.contains(text)
    }

    private fun plain(text: String) = text.filterNot { it.isWhitespace() || it in "!?.,~'\"" }

    private companion object {
        /** STT 최종 결과는 말이 끝난 뒤 조금 늦게 온다. */
        const val TAIL_MS = 2_000L
        const val MIN_LENGTH = 2
    }
}
