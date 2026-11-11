package io.backseatlab.playground.pleos.sdk

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import io.backseatlab.playground.BackseatLog
import io.backseatlab.playground.pleos.CardAudio
import io.backseatlab.playground.pleos.SpeakResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** 목소리 카드(짧은 녹음 파일)를 안드로이드 MediaPlayer로 재생한다. 앱 음성과 같은 용도(ASSISTANT)로 낸다. */
class CardPlayer(
    private val context: Context,
) {
    @Volatile private var player: MediaPlayer? = null

    @Volatile private var done = CompletableDeferred<SpeakResult>(SpeakResult.Done)

    suspend fun play(audio: CardAudio): SpeakResult {
        val finished = CompletableDeferred<SpeakResult>().also { done = it }
        val media = MediaPlayer()
        return try {
            open(media, audio)
            // 준비하는 사이 stop()이 왔으면 소리를 내지 않는다
            if (finished.isCompleted) return finished.await()
            media.setOnCompletionListener { finished.complete(SpeakResult.Done) }
            media.setOnErrorListener { _, what, _ ->
                finished.complete(SpeakResult.Failed("CARD_ERROR_$what"))
            }
            player = media
            media.start()
            withTimeoutOrNull(MAX_CARD_MS) { finished.await() } ?: SpeakResult.Failed("CARD_TIMEOUT")
        } catch (e: java.io.IOException) {
            failed(e)
        } catch (e: IllegalStateException) {
            failed(e)
        } catch (e: IllegalArgumentException) {
            failed(e)
        } catch (e: SecurityException) {
            failed(e)
        } finally {
            player = null
            media.release()
        }
    }

    fun stop() {
        if (done.complete(SpeakResult.Cancelled)) player?.stop()
    }

    private fun failed(e: Exception): SpeakResult {
        BackseatLog.w("VOICE card failed", e)
        return SpeakResult.Failed("CARD_OPEN")
    }

    private fun open(
        media: MediaPlayer,
        audio: CardAudio,
    ) {
        media.setAudioAttributes(
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        when (audio) {
            is CardAudio.Asset -> context.assets.openFd(audio.path).use { media.setDataSource(it) }
            is CardAudio.File -> media.setDataSource(audio.path)
        }
        media.prepare()
    }

    private companion object {
        /** 녹음은 최대 8초(F-08). 넉넉히 잡는다. */
        const val MAX_CARD_MS = 15_000L
    }
}
