package io.backseatlab.playground.pleos.sdk

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import io.backseatlab.playground.BackseatLog
import io.backseatlab.playground.pleos.RecordResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException

/** 목소리 카드 녹음(F-08). F-01 점검 코드(RecordingProbe)의 MediaRecorder 설정을 그대로 쓴다. 메인 스레드에서만 부른다. */
class CardRecorder(
    private val context: Context,
) {
    @Volatile private var stopRequested = false

    suspend fun record(
        path: String,
        maxMs: Long,
    ): RecordResult {
        stopRequested = false
        @Suppress("DEPRECATION")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        var started = false
        return try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(path)
            recorder.prepare()
            recorder.start()
            started = true
            val startedAt = SystemClock.elapsedRealtime()
            var amplitudeMax = 0
            while (!stopRequested && SystemClock.elapsedRealtime() - startedAt < maxMs) {
                delay(AMPLITUDE_POLL_MS)
                amplitudeMax = maxOf(amplitudeMax, recorder.maxAmplitude)
            }
            val durationMs = SystemClock.elapsedRealtime() - startedAt
            started = false
            // 너무 짧아 녹음된 데이터가 없으면 stop()이 RuntimeException을 던진다. 짧은 녹음(한 번 더 크게)으로 본다
            val stopped = runCatching { recorder.stop() }.isSuccess
            if (stopped) RecordResult.Done(durationMs, amplitudeMax) else RecordResult.Done(0, 0)
        } catch (e: CancellationException) {
            // CancellationException도 RuntimeException이라 먼저 그대로 올린다(화면을 닫거나 출발하면 취소된다)
            throw e
        } catch (e: RuntimeException) {
            failed(e)
        } catch (e: IOException) {
            failed(e)
        } finally {
            if (started) runCatching { recorder.stop() }
            recorder.release()
        }
    }

    private fun failed(e: Exception): RecordResult {
        BackseatLog.w("CARD record failed", e)
        return RecordResult.Failed(e::class.simpleName ?: "ERROR")
    }

    fun stop() {
        stopRequested = true
    }

    private companion object {
        const val AMPLITUDE_POLL_MS = 100L
    }
}
