package io.backseatlab.playground.ui.cards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.backseatlab.playground.R
import io.backseatlab.playground.session.CardTab
import io.backseatlab.playground.session.TakeResult
import io.backseatlab.playground.ui.theme.AppCard
import io.backseatlab.playground.ui.theme.BrandOrange
import io.backseatlab.playground.ui.theme.HeroLayout
import io.backseatlab.playground.ui.theme.PageBackground
import io.backseatlab.playground.ui.theme.PauseText
import io.backseatlab.playground.ui.theme.Pill
import io.backseatlab.playground.ui.theme.PrimaryButton
import io.backseatlab.playground.ui.theme.QuietButton
import io.backseatlab.playground.ui.theme.SecondaryButton
import io.backseatlab.playground.ui.theme.Space
import io.backseatlab.playground.ui.theme.WideLayout

/** 녹음 화면(SC-08). 주차 중, 이용 밖에서만 열린다. 동의 전에는 동의 안내만 보인다. */
@Composable
fun CardsScreen(
    state: CardsUiState,
    onAgreeClick: () -> Unit,
    onTabClick: (CardTab) -> Unit,
    onRecordClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    onDeleteAllClick: () -> Unit,
    onRevokeClick: () -> Unit,
    onCloseClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PageBackground)
                .padding(horizontal = Space.edge, vertical = Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.cards_open), style = MaterialTheme.typography.headlineMedium)
            SecondaryButton(stringResource(R.string.cards_close), onCloseClick)
        }
        if (!state.consented) {
            ConsentBody(onAgreeClick, Modifier.weight(1f))
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            CardTab.entries.forEach { tab ->
                val label = stringResource(tab.labelRes())
                if (tab == state.tab) {
                    PrimaryButton(label, { onTabClick(tab) })
                } else {
                    SecondaryButton(label, { onTabClick(tab) })
                }
            }
        }
        AppCard(Modifier.fillMaxWidth()) {
            SlotGrid(state.slots, state.recording, onRecordClick, onDeleteClick)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.CenterVertically) {
            state.lastTake?.messageRes()?.let { Pill(stringResource(it)) }
            Spacer(Modifier.weight(1f))
            QuietButton(stringResource(R.string.cards_delete_all), onDeleteAllClick)
            QuietButton(stringResource(R.string.cards_revoke), onRevokeClick)
        }
    }
}

@Composable
private fun ConsentBody(
    onAgreeClick: () -> Unit,
    modifier: Modifier,
) {
    val mascot = @Composable { Image(painterResource(R.drawable.rody_listen), null, Modifier.size(300.dp)) }
    HeroLayout(mascot, modifier.widthIn(max = 1400.dp)) {
        AppCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cards_consent_notice), style = MaterialTheme.typography.bodyLarge)
            PrimaryButton(stringResource(R.string.cards_agree), onAgreeClick)
        }
    }
}

/** 넓은 화면은 두 줄로 나눠 스크롤 없이 한 탭의 카드를 모두 보여 준다. */
@Composable
private fun SlotGrid(
    slots: List<CardRow>,
    recording: String?,
    onRecordClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
) {
    BoxWithConstraints {
        val columns = if (maxWidth >= WideLayout) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            slots.chunked(columns).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                    pair.forEach { SlotRow(it, recording, onRecordClick, onDeleteClick, Modifier.weight(1f)) }
                    if (pair.size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SlotRow(
    row: CardRow,
    recording: String?,
    onRecordClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (row.recorded) Pill(stringResource(R.string.cards_recorded_badge))
        if (recording == row.cardId) {
            PrimaryButton(stringResource(R.string.cards_stop), { onRecordClick(row.cardId) }, color = BrandOrange)
        } else {
            SecondaryButton(stringResource(R.string.cards_record), { onRecordClick(row.cardId) })
        }
        if (row.recorded) QuietButton(stringResource(R.string.cards_delete), { onDeleteClick(row.cardId) }, PauseText)
    }
}

private fun CardTab.labelRes(): Int =
    when (this) {
        CardTab.EASY -> R.string.cards_tab_easy
        CardTab.NORMAL -> R.string.cards_tab_normal
        CardTab.HARD -> R.string.cards_tab_hard
        CardTab.VEHICLE -> R.string.cards_tab_vehicle
    }

private fun TakeResult.messageRes(): Int? =
    when (this) {
        TakeResult.SAVED -> R.string.cards_saved
        TakeResult.TRY_LOUDER -> R.string.cards_try_louder
        TakeResult.FAILED -> R.string.cards_failed
        TakeResult.NO_CONSENT -> null
    }
