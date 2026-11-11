package io.backseatlab.playground.pleos.sdk

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import io.backseatlab.playground.BackseatLog
import io.backseatlab.playground.pleos.ListenResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Pleos SpeechToText(3.3.1)를 ON_DEVICE 모드로 쓴다. 사용법: docs/sdk-notes/Voice.md
 * 최종 결과(completed=true)만 쓴다. SDK는 후보를 하나만 준다.
 * 콜백에 요청 ID가 없어 한 번에 하나만 듣고, 이전 요청의 인식 종료(onEndedRecognition·onError)가 온 뒤에 다음 요청을 낸다.
 */
class PleosStt(
    context: Context,
) {
    private val stt = SpeechToText(context, Mode.ON_DEVICE)
    private val ready = CompletableDeferred<Boolean>()

    @Volatile var initialized = false
        private set
    private val turn = Mutex()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var result = CompletableDeferred<ListenResult>()

    @Volatile private var ended = CompletableDeferred(Unit)

    /** 끝 콜백을 못 받고 넘어간 이전 요청 수. 그 요청의 늦은 콜백은 이번 요청의 ready 전까지 버린다. */
    @Volatile private var stale = 0

    /** 이번 listen()의 ready 신호 받는 곳. SDK가 onReady를 부를 때 한 번 쓴다. */
    @Volatile private var onReadyCallback: () -> Unit = {}

    private val results =
        object : ResultListener {
            override fun onStartedRecognition() = BackseatLog.i("STT speech-begin")

            override fun onUpdated(
                stt: String,
                completed: Boolean,
            ) {
                if (stale > 0) return
                if (completed && result.complete(ListenResult.Heard(stt))) {
                    BackseatLog.i("STT end code=OK heard=\"$stt\"")
                }
            }

            override fun onEndedRecognition() {
                if (stale > 0) {
                    stale--
                    return
                }
                // 에뮬레이터에서 최종 결과가 끝 콜백보다 늦게(약 2ms) 온다. 바로 빈 결과로 확정하지 않고 잠깐 기다린다
                val pending = result
                main.postDelayed(
                    { if (pending.complete(ListenResult.Heard(""))) BackseatLog.i("STT end code=EMPTY") },
                    LATE_RESULT_MS,
                )
                ended.complete(Unit)
            }

            override fun onError(errorType: ErrorType) {
                if (stale > 0) {
                    stale--
                    return
                }
                if (result.complete(ListenResult.Failed(errorType.name))) BackseatLog.i("STT end code=$errorType")
                ended.complete(Unit)
            }

            override fun onReady() {
                stale = 0
                BackseatLog.i("STT ready")
                onReadyCallback()
            }

            override fun onUpdatedEpdData(
                on: Long,
                off: Long,
            ) = Unit

            override fun onUpdatedRms(rms: Double) = Unit
        }

    fun start() {
        // SDK 규칙: initialize → addListener → setLocale 순서
        stt.initialize(
            object : OnInitializeListener {
                override fun onSuccess() {
                    BackseatLog.i("STT init done")
                    initialized = true
                    ready.complete(true)
                }

                override fun onFailure(errorType: ErrorType) {
                    BackseatLog.i("STT end code=$errorType (init)")
                    ready.complete(false)
                }
            },
        )
        stt.addListener(results)
        stt.setLocale(Locale.KOREA)
    }

    suspend fun listen(onReady: () -> Unit): ListenResult =
        turn.withLock {
            if (withTimeoutOrNull(WAIT_MS) { ready.await() } != true) return@withLock ListenResult.Failed("INIT")
            // 이전 요청의 인식 종료(문서: stop() 뒤 onEndedRecognition)를 받은 뒤에 요청한다. 못 받으면 그 늦은 콜백은 버린다
            if (withTimeoutOrNull(END_WAIT_MS) { ended.await() } == null) stale++
            val heard = CompletableDeferred<ListenResult>().also { result = it }
            val finished = CompletableDeferred<Unit>().also { ended = it }
            onReadyCallback = onReady
            BackseatLog.i("STT start")
            try {
                stt.request()
                // SDK가 아무 콜백도 주지 않는 경우를 대비해 끝을 정해 둔다
                withTimeoutOrNull(WAIT_MS) { heard.await() }
                    ?: ListenResult.Failed("NO_RESPONSE").also { BackseatLog.i("STT end code=NO_RESPONSE") }
            } finally {
                // 결과 없이 빠져나가면(취소·시간 초과) 듣기를 멈춘다. 종료 콜백은 다음 요청이 기다린다
                if (!heard.isCompleted) stt.stop()
            }
        }

    fun stop() {
        if (!ended.isCompleted) stt.stop()
    }

    private companion object {
        const val WAIT_MS = 30_000L
        const val END_WAIT_MS = 200L
        const val LATE_RESULT_MS = 300L
    }
}
