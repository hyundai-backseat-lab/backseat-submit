package io.backseatlab.playground.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.backseatlab.playground.session.CarStatus
import io.backseatlab.playground.session.LocationConsent
import io.backseatlab.playground.session.PauseReason
import io.backseatlab.playground.session.Phase
import io.backseatlab.playground.session.PlayStatus
import io.backseatlab.playground.session.SessionController
import io.backseatlab.playground.session.SessionLines
import io.backseatlab.playground.session.SessionResult
import io.backseatlab.playground.session.SessionStatus
import io.backseatlab.playground.session.Signal
import io.backseatlab.playground.session.StartBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** 화면이 그리는 값의 전부. */
data class MainUiState(
    val connected: Boolean,
    val hasUnknown: Boolean,
    val active: List<Signal>,
    val unsupported: List<Signal>,
    val phase: Phase,
    val parked: Boolean,
    val startBlock: StartBlock?,
    val result: SessionResult? = null,
    val play: PlayStatus? = null,
    val pauses: Set<PauseReason> = emptySet(),
    val answerOpen: Boolean = false,
    val difficulties: List<String> = SessionLines.DIFFICULTIES,
    val locationConsented: Boolean = false,
) {
    val inSession: Boolean get() = phase != Phase.IDLE && phase != Phase.ENDED

    /** 게임이 난이도를 물을 때(아직 정하지 않았고 응답 차례가 열려 있음), 주차 중에만. */
    val showDifficulty: Boolean get() = parked && answerOpen && play?.difficulty == null

    /** 일시정지(= 휴식 요청)는 주차 중, 진행 중일 때만. */
    val showPause: Boolean get() = parked && inSession && phase != Phase.RESTING && phase != Phase.PAUSED

    /** 터치 UI(시작)는 주차 중, 이용 전·종료 후에만 보인다. */
    val showStart: Boolean get() = parked && (phase == Phase.IDLE || phase == Phase.ENDED)

    /** 위치정보 이용 동의 버튼(F-11)도 시작 버튼과 같은 때, 동의 전에만. */
    val showLocationConsent: Boolean get() = showStart && !locationConsented
}

/** 컨트롤러는 앱 수명 동안 하나라서 화면이 다시 만들어져도 새 이용이 생기지 않는다. */
class MainViewModel(
    private val session: SessionController,
    private val consent: LocationConsent,
) : ViewModel() {
    private val startBlock = MutableStateFlow<StartBlock?>(null)

    /** 권한 창을 띄운 시작 요청. 그 사이 출발하면 취소되고, 다시 주차해도 새로 눌러야 한다(F-04-02). */
    private var pendingStart = false

    private var pendingConsent = false

    val uiState: StateFlow<MainUiState> =
        combine(session.carStatus, session.status, session.parked, startBlock, ::toUiState)
            .combine(consent.granted) { state, granted -> state.copy(locationConsented = granted) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                toUiState(session.carStatus.value, session.status.value, session.parked.value, null)
                    .copy(locationConsented = consent.granted.value),
            )

    init {
        session.parked
            .onEach { parked ->
                if (!parked) pendingConsent = false
                if (!parked && pendingStart) {
                    pendingStart = false
                    startBlock.value = null
                }
                if (parked && startBlock.value == StartBlock.NOT_PARKED) startBlock.value = null
            }.launchIn(viewModelScope)
    }

    /** 시작 버튼. 마이크 권한을 물어야 하면 true를 돌려준다(화면이 권한 창을 띄운다). 주행 중이면 아무것도 하지 않는다. */
    fun onStartClick(micGranted: Boolean): Boolean {
        val block = session.requestStart(micGranted)
        startBlock.value = block
        pendingStart = block == StartBlock.NO_MIC
        return pendingStart
    }

    fun onMicPermissionResult(granted: Boolean) {
        if (!pendingStart) return
        pendingStart = false
        startBlock.value = session.requestStart(granted)
    }

    /** 위치 동의 버튼. 위치 권한을 물어야 하면 true(화면이 권한 창을 띄운다). 주차 중이 아니면 아무것도 하지 않는다. */
    fun onLocationConsentClick(locationGranted: Boolean): Boolean {
        if (!session.parked.value) return false
        if (locationGranted) consent.grant()
        pendingConsent = !locationGranted
        return pendingConsent
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (pendingConsent && granted) consent.grant()
        pendingConsent = false
    }

    /** 숨은 버튼을 강제로 눌러도 세션이 주차·응답 차례를 다시 확인한다(F-06-02). */
    fun onDifficultyClick(level: String) {
        session.answerFromScreen(level)
    }

    fun onPauseClick() {
        session.restFromScreen()
    }

    fun onScreenShown() = session.setScreenVisible(true)

    fun onScreenLeft() {
        pendingStart = false
        session.setScreenVisible(false)
        session.onScreenLeft()
    }

    private fun toUiState(
        car: CarStatus,
        status: SessionStatus,
        parked: Boolean,
        block: StartBlock?,
    ) = MainUiState(
        connected = car.connected,
        hasUnknown = car.hasUnknown,
        active = car.active,
        unsupported = car.unsupported,
        phase = status.phase,
        parked = parked,
        startBlock = block,
        result = status.result,
        play = status.play,
        pauses = status.pauses,
        answerOpen = status.answerOpen,
    )
}
