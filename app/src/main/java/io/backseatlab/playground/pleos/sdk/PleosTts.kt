package io.backseatlab.playground.pleos.sdk

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import io.backseatlab.playground.BackseatLog
import io.backseatlab.playground.pleos.SpeakResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Pleos TextToSpeech(3.3.1)를 ON_DEVICE 모드로 쓴다. 사용법: docs/sdk-notes/Voice.md
 * 콜백에 요청 ID가 없어 한 번에 한 문장만 말하고, 끝 콜백(done·stop·error)이 온 뒤에 다음 문장을 시작한다.
 */
class PleosTts(
    context: Context,
) {
    private val tts = TextToSpeech(context, Mode.ON_DEVICE)
    private val ready = CompletableDeferred<Boolean>()

    @Volatile var initialized = false
        private set
    private val turn = Mutex()

    @Volatile private var current = CompletableDeferred<SpeakResult>(SpeakResult.Done)

    /** 끝 콜백을 못 받고 넘어간 이전 문장 수. 그 문장의 늦은 콜백은 이번 문장의 onStart 전까지 버린다. */
    @Volatile private var stale = 0

    private val events =
        object : EventListener {
            override fun onStart() {
                stale = 0
                BackseatLog.i("TTS start")
            }

            override fun onReady() = Unit

            override fun onDone() {
                if (staleEnd()) return
                BackseatLog.i("TTS done")
                current.complete(SpeakResult.Done)
            }

            override fun onError(errorType: ErrorType) {
                if (staleEnd()) return
                BackseatLog.i("TTS error code=$errorType")
                current.complete(SpeakResult.Failed(errorType.name))
            }

            // 문서: stop()을 부르면 onDone이 아니라 onStop이 온다
            override fun onStop() {
                if (staleEnd()) return
                BackseatLog.i("TTS cancel")
                current.complete(SpeakResult.Cancelled)
            }

            override fun onUpdatedRms(rms: Double) = Unit
        }

    fun start() {
        // SDK 규칙: initialize를 먼저 부른 뒤에 리스너를 단다(반대로 하면 앱이 죽는다)
        tts.initialize(
            object : OnInitializeListener {
                override fun onSuccess() {
                    tts.setLocale(Locale.KOREA)
                    initialized = true
                    ready.complete(true)
                }

                override fun onFailure(errorType: ErrorType) {
                    BackseatLog.i("TTS error code=$errorType (init)")
                    ready.complete(false)
                }
            },
        )
        tts.addEventListener(events)
    }

    suspend fun speak(text: String): SpeakResult =
        turn.withLock {
            if (withTimeoutOrNull(INIT_WAIT_MS) { ready.await() } != true) return@withLock SpeakResult.Failed("INIT")
            // 멈춘 이전 문장의 끝 콜백(onStop)을 받은 뒤에 말한다. 못 받으면 그 늦은 콜백은 버린다
            if (withTimeoutOrNull(END_WAIT_MS) { current.await() } == null) stale++
            val done = CompletableDeferred<SpeakResult>().also { current = it }
            try {
                tts.speak(text, true)
                withTimeoutOrNull(SPEAK_WAIT_MS) { done.await() }
                    ?: SpeakResult.Failed("NO_RESPONSE").also { BackseatLog.i("TTS error code=NO_RESPONSE") }
            } finally {
                if (!done.isCompleted) tts.stop()
            }
        }

    private fun staleEnd(): Boolean {
        if (stale == 0) return false
        stale--
        return true
    }

    /** 말하는 중이면 멈춘다. 말하던 speak()는 Cancelled를 돌려준다. */
    fun stop() {
        if (!current.isCompleted) tts.stop()
    }

    private companion object {
        const val INIT_WAIT_MS = 5_000L
        const val SPEAK_WAIT_MS = 30_000L
        const val END_WAIT_MS = 1_000L
    }
}
