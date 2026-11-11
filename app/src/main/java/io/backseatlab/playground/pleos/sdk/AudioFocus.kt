package io.backseatlab.playground.pleos.sdk

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import io.backseatlab.playground.BackseatLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 안드로이드 오디오 포커스. Pleos 가이드가 다른 소리와 함께 쓰라고 한 GAIN_TRANSIENT_MAY_DUCK으로 요청하고,
 * 어떤 종류든 잃으면 [lost]를 true로 둔다(잠깐 줄이기(DUCK)도 내비 안내일 수 있어 멈춤으로 다룬다).
 */
class AudioFocus(
    context: Context,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val mutableLost = MutableStateFlow(false)
    val lost: StateFlow<Boolean> = mutableLost

    private val request =
        AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            ).setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> setLost(false)
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                    -> setLost(true)
                }
            }.build()

    fun hold() {
        val granted = audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        // 거절돼도 멈춤으로 두지 않는다: 지연 획득을 받지 않아 GAIN이 오지 않으므로 영영 멈춰 버린다
        BackseatLog.i("FOCUS request granted=$granted")
    }

    fun release() {
        audio.abandonAudioFocusRequest(request)
        setLost(false)
    }

    private fun setLost(value: Boolean) {
        if (mutableLost.value == value) return
        mutableLost.value = value
        BackseatLog.i(if (value) "FOCUS lost" else "FOCUS gained")
    }
}
