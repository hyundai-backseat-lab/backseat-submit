package io.backseatlab.playground.pleos.sdk

import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.delay

/** 안드로이드 기본 톤(ToneGenerator)으로 짧은 "삐" 소리를 낸다. 별도 음원 파일이 필요 없다. */
class ToneBeeper {
    suspend fun beep() {
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME_PERCENT)
        try {
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, BEEP_MS)
            delay(BEEP_MS + TAIL_MS)
        } finally {
            tone.release()
        }
    }

    private companion object {
        const val VOLUME_PERCENT = 80
        const val BEEP_MS = 250
        const val TAIL_MS = 100L
    }
}
