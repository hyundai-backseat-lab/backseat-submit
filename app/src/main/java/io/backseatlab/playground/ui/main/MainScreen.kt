package io.backseatlab.playground.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.backseatlab.playground.R
import io.backseatlab.playground.session.Phase
import io.backseatlab.playground.session.SessionResult
import io.backseatlab.playground.session.Signal
import io.backseatlab.playground.session.StartBlock
import io.backseatlab.playground.ui.theme.AppCard
import io.backseatlab.playground.ui.theme.BrandBlue
import io.backseatlab.playground.ui.theme.BrandOrange
import io.backseatlab.playground.ui.theme.HeroLayout
import io.backseatlab.playground.ui.theme.PageBackground
import io.backseatlab.playground.ui.theme.PauseText
import io.backseatlab.playground.ui.theme.Pill
import io.backseatlab.playground.ui.theme.PrimaryButton
import io.backseatlab.playground.ui.theme.QuietButton
import io.backseatlab.playground.ui.theme.SecondaryButton
import io.backseatlab.playground.ui.theme.Space
import io.backseatlab.playground.ui.theme.StatTile
import io.backseatlab.playground.ui.theme.StatusBadge
import io.backseatlab.playground.ui.theme.TextSecondary

/** 상단 브랜드·차량 상태, 가운데 화면 내용, 하단 버튼(주차 중에만). */
@Composable
fun MainScreen(
    state: MainUiState,
    onStartClick: () -> Unit,
    onDifficultyClick: (String) -> Unit,
    onPauseClick: () -> Unit,
    onLocationConsentClick: () -> Unit,
    onCardsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val complete = state.phase == Phase.ENDED && state.result != null
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(PageBackground)
                .padding(horizontal = Space.edge, vertical = Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        TopBar(state, home = !state.inSession, mascot = !state.inSession && !complete && state.parked)
        // 내용과 버튼을 한 묶음으로 가운데에 둔다
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.xl, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                // 주행·정차 중 이용: 요약 패널(SC-04)만, 멈춤이면 사유(SC-05). 버튼·캐릭터는 없다
                state.inSession && !state.parked -> DrivingPanel(state)
                state.inSession -> ParkedSession(state, onDifficultyClick)
                complete -> CompleteBody(state, requireNotNull(state.result))
                else -> HomeBody(state)
            }
            BottomActions(state, onStartClick, onPauseClick, onLocationConsentClick, onCardsClick)
        }
    }
}

@Composable
private fun TopBar(
    state: MainUiState,
    home: Boolean,
    mascot: Boolean,
) {
    val type = MaterialTheme.typography
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.l), verticalAlignment = Alignment.CenterVertically) {
            if (mascot) Rody(state, size = 200.dp)
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                val brand = if (home) type.displaySmall else type.displaySmall.copy(fontSize = 28.sp)
                Text(stringResource(R.string.app_name), style = brand, color = BrandBlue)
                if (home) Text(stringResource(R.string.brand_tagline), style = type.bodyMedium, color = TextSecondary)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            StatusBadge(statusText(state), on = state.connected && state.active.isNotEmpty())
            if (state.unsupported.isNotEmpty()) {
                val names = state.unsupported.map { stringResource(it.labelRes()) }.joinToString(", ")
                Text(stringResource(R.string.status_unsupported, names), style = type.bodySmall, color = TextSecondary)
            }
        }
    }
}

/** 시작 화면(SC-01). 시작할 수 없으면 이유를 대신 보여 준다. */
@Composable
private fun HomeBody(state: MainUiState) {
    AppCard(Modifier.widthIn(max = 1100.dp).fillMaxWidth()) {
        Pill(stringResource(state.phase.labelRes()))
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
        val blocked = if (!state.parked) R.string.block_not_parked else state.startBlock?.labelRes()
        if (blocked != null) {
            Text(stringResource(blocked), style = MaterialTheme.typography.bodyLarge, color = PauseText)
        } else {
            Text(stringResource(R.string.home_hint), style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        }
    }
}

/** 결과(SC-07)는 주차 중에만 숫자를 보여 준다. */
@Composable
private fun CompleteBody(
    state: MainUiState,
    result: SessionResult,
) {
    val mascot: (@Composable () -> Unit)? = if (state.parked) ({ Rody(state, size = 340.dp) }) else null
    val type = MaterialTheme.typography
    HeroLayout(mascot, Modifier.widthIn(max = 1400.dp)) {
        AppCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.complete_title), style = type.headlineMedium)
            if (!state.parked) {
                Text(stringResource(R.string.result_after_parking), style = type.bodyLarge, color = TextSecondary)
                return@AppCard
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                val tile = Modifier.weight(1f)
                val found = stringResource(R.string.stat_count, result.found)
                StatTile(found, stringResource(R.string.stat_found), tile, BrandOrange)
                val played = stringResource(R.string.stat_count, result.played)
                StatTile(played, stringResource(R.string.stat_played), tile)
                StatTile(stringResource(R.string.stat_times, result.hints), stringResource(R.string.stat_hints), tile)
            }
            Text(stringResource(R.string.complete_bye), style = type.bodyLarge, color = TextSecondary)
        }
    }
}

/** 터치 UI는 주차 중에만(F-06-02). 주행 중에는 숨길 뿐 아니라 ViewModel·세션에서도 다시 막는다. */
@Composable
private fun BottomActions(
    state: MainUiState,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onLocationConsentClick: () -> Unit,
    onCardsClick: () -> Unit,
) {
    if (!state.showStart && !state.showPause) return
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.l, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.showStart) {
            PrimaryButton(stringResource(R.string.start), onStartClick, Modifier.widthIn(min = 400.dp))
            // 녹음 화면(SC-08) 입구. 시작 버튼과 같은 때(주차 중, 이용 밖)에만
            SecondaryButton(stringResource(R.string.cards_open), onCardsClick)
        }
        if (state.showLocationConsent) {
            QuietButton(stringResource(R.string.location_consent), onLocationConsentClick, BrandBlue)
        }
        if (state.showPause) SecondaryButton(stringResource(R.string.pause), onPauseClick)
    }
}

@Composable
private fun statusText(state: MainUiState): String =
    when {
        !state.connected -> stringResource(R.string.status_not_connected)
        state.active.isEmpty() && state.hasUnknown -> stringResource(R.string.status_partial)
        state.active.isEmpty() -> stringResource(R.string.status_no_signal)
        else -> state.active.map { stringResource(it.labelRes()) }.joinToString(" · ")
    }

private fun StartBlock.labelRes(): Int =
    when (this) {
        StartBlock.NOT_PARKED -> R.string.block_not_parked
        StartBlock.WARNING -> R.string.block_warning
        StartBlock.NO_MIC -> R.string.block_no_mic
        StartBlock.VOICE_NOT_READY -> R.string.block_voice
    }

private fun Signal.labelRes(): Int =
    when (this) {
        Signal.TURN -> R.string.signal_turn
        Signal.TURN_LEFT -> R.string.signal_turn_left
        Signal.TURN_RIGHT -> R.string.signal_turn_right
        Signal.HAZARD -> R.string.signal_hazard
        Signal.BRAKE -> R.string.signal_brake
        Signal.PARKING_BRAKE -> R.string.signal_parking_brake
        Signal.WARNING -> R.string.signal_warning
        Signal.WIPER -> R.string.signal_wiper
        Signal.DOOR -> R.string.signal_door
        Signal.SPEED -> R.string.signal_speed
        Signal.GEAR -> R.string.signal_gear
    }
