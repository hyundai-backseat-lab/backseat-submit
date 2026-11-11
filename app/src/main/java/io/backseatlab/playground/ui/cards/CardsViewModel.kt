package io.backseatlab.playground.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.backseatlab.playground.session.CardSlot
import io.backseatlab.playground.session.CardStudio
import io.backseatlab.playground.session.CardTab
import io.backseatlab.playground.session.TakeResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CardRow(
    val cardId: String,
    val label: String,
    val recorded: Boolean,
)

/** 녹음 화면(SC-08)이 그리는 값의 전부. */
data class CardsUiState(
    val open: Boolean = false,
    val consented: Boolean = false,
    val tab: CardTab = CardTab.EASY,
    val slots: List<CardRow> = emptyList(),
    val recording: String? = null,
    val lastTake: TakeResult? = null,
)

private data class CardsLocalState(
    val open: Boolean = false,
    val tab: CardTab = CardTab.EASY,
    val recording: String? = null,
    val lastTake: TakeResult? = null,
)

/**
 * 녹음 화면은 [available](주차 중이고 이용 전·종료 후)일 때만 열린다.
 * 출발하거나 이용이 시작되면 녹음을 버리고 화면을 닫는다(F-08-1).
 */
class CardsViewModel(
    private val studio: CardStudio,
    private val available: StateFlow<Boolean>,
    private val slots: List<CardSlot>,
) : ViewModel() {
    private val local = MutableStateFlow(CardsLocalState())
    private var take: Job? = null

    val uiState: StateFlow<CardsUiState> =
        combine(local, studio.consentAt, studio.recorded) { l, consentAt, recorded ->
            CardsUiState(
                open = l.open,
                consented = consentAt != null,
                tab = l.tab,
                slots = slots.filter { it.tab == l.tab }.map { CardRow(it.cardId, it.label, it.cardId in recorded) },
                recording = l.recording,
                lastTake = l.lastTake,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, CardsUiState())

    init {
        available.onEach { if (!it) close() }.launchIn(viewModelScope)
    }

    fun onOpenClick() {
        if (available.value) local.update { it.copy(open = true, lastTake = null) }
    }

    fun close() {
        take?.cancel()
        if (local.value.recording != null) studio.stop()
        take = null
        local.update { CardsLocalState(tab = it.tab) }
    }

    fun onTabClick(tab: CardTab) {
        if (local.value.recording == null) local.update { it.copy(tab = tab, lastTake = null) }
    }

    fun onAgreeClick() {
        if (local.value.open) studio.agree()
    }

    fun onRevokeClick() {
        if (local.value.recording == null) studio.revoke()
    }

    /** 녹음 버튼. 같은 카드를 다시 누르면 멈춘다. 마이크 권한을 물어야 하면 true(화면이 권한 창을 띄운다). */
    fun onRecordClick(
        cardId: String,
        micGranted: Boolean,
    ): Boolean {
        val now = local.value
        if (!now.open || !available.value) return false
        when (now.recording) {
            cardId -> studio.stop()
            null -> if (micGranted) start(cardId)
        }
        return now.recording == null && !micGranted
    }

    fun onDeleteClick(cardId: String) {
        if (local.value.recording == null) studio.delete(cardId)
    }

    fun onDeleteAllClick() {
        if (local.value.recording == null) studio.deleteAll()
    }

    private fun start(cardId: String) {
        local.update { it.copy(recording = cardId, lastTake = null) }
        take =
            viewModelScope.launch {
                val result = studio.record(cardId)
                local.update { it.copy(recording = null, lastTake = result) }
            }
    }
}
