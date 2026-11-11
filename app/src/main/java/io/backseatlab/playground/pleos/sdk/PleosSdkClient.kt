package io.backseatlab.playground.pleos.sdk

import android.content.Context
import io.backseatlab.playground.pleos.CarLocation
import io.backseatlab.playground.pleos.CardAudio
import io.backseatlab.playground.pleos.ListenResult
import io.backseatlab.playground.pleos.PleosClient
import io.backseatlab.playground.pleos.RecordResult
import io.backseatlab.playground.pleos.SpeakResult
import io.backseatlab.playground.pleos.VehicleState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 실제 Pleos SDK(Vehicle·TTS·STT·NaviHelper)와 안드로이드 기본 톤·오디오 포커스로 PleosClient를 채운다. */
class PleosSdkClient(
    context: Context,
) : PleosClient {
    private val car = PleosVehicle(context)
    private val tts = PleosTts(context)
    private val stt = PleosStt(context)
    private val beeper = ToneBeeper()
    private val focus = AudioFocus(context)
    private val navi = PleosNavi(context)
    private val cards = CardPlayer(context)

    override val vehicle: StateFlow<VehicleState> = car.state
    override val focusLost: StateFlow<Boolean> = focus.lost
    override val arrivals: Flow<Unit> = navi.arrivals

    private val gps = GpsLocation(context)
    private val recorder = CardRecorder(context)

    override suspend fun record(
        path: String,
        maxMs: Long,
    ): RecordResult = recorder.record(path, maxMs)

    override fun stopRecording() = recorder.stop()

    override suspend fun location(): CarLocation? = gps.latest() ?: navi.location()

    /** 앱 시작 때 한 번. 음성 초기화를 미리 해 두어 이용을 시작할 때 기다리지 않게 한다. */
    fun start() {
        car.start()
        tts.start()
        stt.start()
        navi.start()
    }

    override fun voiceReady() = tts.initialized && stt.initialized

    override fun holdAudioFocus() = focus.hold()

    override fun releaseAudioFocus() = focus.release()

    override suspend fun speak(text: String): SpeakResult = tts.speak(text)

    override suspend fun playCard(audio: CardAudio): SpeakResult = cards.play(audio)

    override fun stopSpeaking() {
        tts.stop()
        cards.stop()
    }

    override suspend fun listen(onReady: () -> Unit): ListenResult = stt.listen(onReady)

    override fun stopListening() = stt.stop()

    override suspend fun beep() = beeper.beep()
}
