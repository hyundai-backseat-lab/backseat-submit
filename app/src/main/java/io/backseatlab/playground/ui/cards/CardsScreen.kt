package io.backseatlab.playground.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.backseatlab.playground.R
import io.backseatlab.playground.session.CardTab
import io.backseatlab.playground.session.TakeResult

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
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!state.consented) {
            CardText(stringResource(R.string.cards_consent_notice))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                CardButton(stringResource(R.string.cards_agree), onClick = onAgreeClick)
                CardButton(stringResource(R.string.cards_close), outlined = true, onClick = onCloseClick)
            }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CardTab.entries.forEach { tab ->
                CardButton(stringResource(tab.labelRes()), outlined = tab != state.tab) { onTabClick(tab) }
            }
        }
        state.slots.forEach { row -> SlotRow(row, state.recording, onRecordClick, onDeleteClick) }
        state.lastTake?.messageRes()?.let { CardText(stringResource(it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CardButton(stringResource(R.string.cards_delete_all), outlined = true, onClick = onDeleteAllClick)
            CardButton(stringResource(R.string.cards_revoke), outlined = true, onClick = onRevokeClick)
            CardButton(stringResource(R.string.cards_close), onClick = onCloseClick)
        }
    }
}

@Composable
private fun SlotRow(
    row: CardRow,
    recording: String?,
    onRecordClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        CardText(if (row.recorded) stringResource(R.string.cards_recorded, row.label) else row.label)
        val label = if (recording == row.cardId) R.string.cards_stop else R.string.cards_record
        CardButton(stringResource(label)) { onRecordClick(row.cardId) }
        if (row.recorded) {
            CardButton(stringResource(R.string.cards_delete), outlined = true) { onDeleteClick(row.cardId) }
        }
    }
}

@Composable
private fun CardText(text: String) {
    // 기본 줄 높이(24sp)는 28sp 글자보다 작아 여러 줄이면 겹친다
    Text(
        text = text,
        fontSize = 28.sp,
        lineHeight = 40.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun CardButton(
    text: String,
    outlined: Boolean = false,
    onClick: () -> Unit,
) {
    val size = Modifier.sizeIn(minWidth = 120.dp, minHeight = 76.dp)
    if (outlined) {
        OutlinedButton(onClick = onClick, modifier = size) { Text(text = text, fontSize = 28.sp) }
    } else {
        Button(onClick = onClick, modifier = size) { Text(text = text, fontSize = 28.sp) }
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
