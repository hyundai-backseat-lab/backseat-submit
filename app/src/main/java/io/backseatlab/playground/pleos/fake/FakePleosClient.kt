package io.backseatlab.playground.pleos.fake

import io.backseatlab.playground.pleos.CarLocation
import io.backseatlab.playground.pleos.CardAudio
import io.backseatlab.playground.pleos.ListenResult
import io.backseatlab.playground.pleos.PleosClient
import io.backseatlab.playground.pleos.RecordResult
import io.backseatlab.playground.pleos.SpeakResult
import io.backseatlab.playground.pleos.VehicleState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * 테스트용 가짜 Pleos. 차량 신호는 [updateVehicle]로 바꾼다. 듣기는 [heard]에 미리 넣은 결과를 순서대로 돌려주고,
 * 비어 있으면 [say]·[fail]이 올 때까지 기다린다(`null`도 기다림). [events]에 speak·listen·ready·beep 순서를 남긴다.
 */
class FakePleosClient(
    initial: VehicleState = VehicleState(),
    private val speakResult: SpeakResult = SpeakResult.Done,
    private val speakMs: Long = 0,
    private val readyDelayMs: Long = 0,
    private val callReady: Boolean = true,
) : PleosClient {
    private val mutableVehicle = MutableStateFlow(initial)
    override val vehicle: StateFlow<VehicleState> = mutableVehicle
    private val mutableFocusLost = MutableStateFlow(false)
    override val focusLost: StateFlow<Boolean> = mutableFocusLost
    var holdingFocus = false
        private set
    var voiceAvailable = true
    private val mutableArrivals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val arrivals: Flow<Unit> = mutableArrivals
    var currentLocation: CarLocation? = null

    /** 비어 있으면 [stopRecording]까지 녹음이 이어지고 2초·충분한 음량으로 끝난다. */
    val recordResults = ArrayDeque<RecordResult>()
    var recordCount = 0
        private set
    var lastRecordMaxMs = 0L
        private set
    var stopRecordingCount = 0
        private set
    private var recordingNow: CompletableDeferred<RecordResult>? = null

    override suspend fun record(
        path: String,
        maxMs: Long,
    ): RecordResult {
        recordCount++
        lastRecordMaxMs = maxMs
        val result =
            recordResults.removeFirstOrNull()
                ?: CompletableDeferred<RecordResult>().also { recordingNow = it }.await()
        if (result is RecordResult.Done) java.io.File(path).writeText("audio")
        return result
    }

    override fun stopRecording() {
        stopRecordingCount++
        recordingNow?.complete(RecordResult.Done(durationMs = 2_000, maxAmplitude = 5_000))
        recordingNow = null
    }

    var locationAsked = 0
        private set

    override suspend fun location(): CarLocation? = currentLocation.also { locationAsked++ }

    val heard = ArrayDeque<ListenResult?>()
    private val said = Channel<ListenResult>(Channel.UNLIMITED)
    val spoken = mutableListOf<String>()
    val events = mutableListOf<String>()
    var stopSpeakingCount = 0
        private set
    var stopListeningCount = 0
        private set

    fun updateVehicle(change: VehicleState.() -> VehicleState) {
        mutableVehicle.update { it.change() }
    }

    fun arrive() {
        mutableArrivals.tryEmit(Unit)
    }

    fun setFocusLost(lost: Boolean) {
        mutableFocusLost.value = lost
    }

    override fun voiceReady() = voiceAvailable

    override fun holdAudioFocus() {
        holdingFocus = true
    }

    override fun releaseAudioFocus() {
        holdingFocus = false
    }

    override suspend fun speak(text: String): SpeakResult {
        spoken += text
        events += "speak:$text"
        delay(speakMs)
        return speakResult
    }

    override suspend fun playCard(audio: CardAudio): SpeakResult {
        events += "card:$audio"
        delay(speakMs)
        return speakResult
    }

    override fun stopSpeaking() {
        stopSpeakingCount++
    }

    override suspend fun listen(onReady: () -> Unit): ListenResult {
        events += "listen"
        delay(readyDelayMs)
        if (callReady) {
            events += "ready"
            onReady()
        }
        return heard.removeFirstOrNull() ?: said.receive()
    }

    /** 지금(또는 다음) 듣기의 인식 결과. */
    fun say(text: String) {
        said.trySend(ListenResult.Heard(text))
    }

    fun fail(code: String) {
        said.trySend(ListenResult.Failed(code))
    }

    override fun stopListening() {
        stopListeningCount++
    }

    override suspend fun beep() {
        events += "beep"
    }
}
