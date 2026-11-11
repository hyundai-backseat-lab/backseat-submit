package io.backseatlab.playground.pleos

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface SpeakResult {
    data object Done : SpeakResult

    /** stopSpeaking()으로 멈췄다. 취소된 발화 뒤의 다음 단계는 실행하지 않는다. */
    data object Cancelled : SpeakResult

    /** [code]는 SDK의 오류 이름(예: ON_DEVICE_ENGINE_ERROR). */
    data class Failed(
        val code: String,
    ) : SpeakResult
}

/** 목소리 카드 음원 위치. 녹음 카드는 기기 파일, 샘플 카드는 앱 assets. */
sealed interface CardAudio {
    data class Asset(
        val path: String,
    ) : CardAudio

    data class File(
        val path: String,
    ) : CardAudio
}

/** 현재 위치와 진행 방향(도, 0=북, 시계 방향). 저장하거나 로그에 남기지 않는다. */
data class CarLocation(
    val lat: Double,
    val lng: Double,
    val heading: Double,
)

sealed interface RecordResult {
    /** [maxAmplitude]는 녹음 동안 잰 최대 진폭(0 = 무음). */
    data class Done(
        val durationMs: Long,
        val maxAmplitude: Int,
    ) : RecordResult

    data class Failed(
        val code: String,
    ) : RecordResult
}

sealed interface ListenResult {
    data class Heard(
        val text: String,
    ) : ListenResult

    /** [code]는 SDK의 오류 이름(예: TIMEOUT). */
    data class Failed(
        val code: String,
    ) : ListenResult
}

/**
 * 앱이 아는 Pleos SDK의 전부(설계서 2절). 차량 신호·말하기·듣기·효과음을 한곳에 모은다.
 * SDK 구현은 pleos/sdk, 테스트용은 pleos/fake, debug 빌드의 sim.sh 덮어쓰기는 debug/에 있다.
 */
interface PleosClient {
    val vehicle: StateFlow<VehicleState>

    /** 다른 앱(내비 안내 등)이 오디오 포커스를 가져가 앱이 말하면 안 되는 동안 true. 원인은 단정하지 않는다. */
    val focusLost: StateFlow<Boolean>

    /** 이용 동안 오디오 포커스를 잡는다(GAIN_TRANSIENT_MAY_DUCK). 이용이 끝나면 [releaseAudioFocus]. */
    fun holdAudioFocus()

    fun releaseAudioFocus()

    /** 내비 목적지 도착(NaviHelper onDestinationArrived). */
    val arrivals: Flow<Unit>

    /** 지금 위치. GPS가 먼저, 없으면 NaviHelper 현재 위치(D-031). 둘 다 없으면 null. */
    suspend fun location(): CarLocation?

    /** TTS·STT 초기화가 끝나 음성을 쓸 수 있는지(F-04 시작 조건). */
    fun voiceReady(): Boolean

    /** 말이 끝나거나 실패할 때까지 기다린다. */
    suspend fun speak(text: String): SpeakResult

    /** 목소리 카드를 끝까지 재생한다. [stopSpeaking]으로 멈추면 Cancelled. */
    suspend fun playCard(audio: CardAudio): SpeakResult

    /** 말하기와 카드 재생을 멈춘다. */
    fun stopSpeaking()

    /**
     * 한 번 듣고 결과가 올 때까지 기다린다.
     * [onReady]는 STT가 듣기 준비를 마친 순간(ready) 한 번 불린다. 아이에게 "말해!" 신호를 주는 시점이다.
     */
    suspend fun listen(onReady: () -> Unit = {}): ListenResult

    fun stopListening()

    /** 마이크로 [path]에 녹음한다. [maxMs]가 지나거나 [stopRecording]을 부르면 끝난다(F-08 목소리 카드). */
    suspend fun record(
        path: String,
        maxMs: Long,
    ): RecordResult

    fun stopRecording()

    /** 짧은 효과음. 소리가 끝날 때까지 기다린다. */
    suspend fun beep()
}
