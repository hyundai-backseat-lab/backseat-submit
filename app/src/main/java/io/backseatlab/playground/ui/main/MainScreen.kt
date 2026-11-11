package io.backseatlab.playground.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.backseatlab.playground.R
import io.backseatlab.playground.session.PauseReason
import io.backseatlab.playground.session.Phase
import io.backseatlab.playground.session.SessionResult
import io.backseatlab.playground.session.Signal
import io.backseatlab.playground.session.StartBlock

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
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            // 주행·정차 중 이용: 요약 패널(SC-04)만, 멈춤이면 사유(SC-05). 버튼은 없다
            state.inSession && !state.parked -> SummaryPanel(state)
            state.inSession -> {
                SummaryPanel(state)
                ParkedControls(state, onDifficultyClick, onPauseClick)
            }
            else -> IdleScreen(state, onStartClick, onLocationConsentClick, onCardsClick)
        }
    }
}

/** 난이도·미션 순번·찾은 개수, 미션 제목 한 줄, 상태. 멈춤 중이면 사유. */
@Composable
private fun SummaryPanel(state: MainUiState) {
    val play = state.play
    if (play != null && play.missionNo > 0) {
        BigText(stringResource(R.string.summary_progress, play.difficulty.orEmpty(), play.missionNo, play.found))
        play.missionTitle?.let { BigText(it) }
    }
    BigText(stringResource(state.phase.labelRes()))
    val reason =
        when (state.pauses.firstOrNull()) {
            PauseReason.FOCUS_LOSS -> R.string.pause_other_voice
            PauseReason.TURN -> R.string.pause_turn
            PauseReason.HAZARD -> R.string.pause_hazard
            PauseReason.BRAKE -> R.string.pause_brake
            null -> null
        }
    reason?.let { BigText(stringResource(it)) }
}

/** 주차 중에만: 난이도 버튼(SC-02, 게임이 난이도를 물을 때), 일시정지(= 휴식 요청). */
@Composable
private fun ParkedControls(
    state: MainUiState,
    onDifficultyClick: (String) -> Unit,
    onPauseClick: () -> Unit,
) {
    if (state.showDifficulty) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            state.difficulties.forEach { level -> BigButton(level) { onDifficultyClick(level) } }
        }
    }
    if (state.showPause) BigButton(stringResource(R.string.pause)) { onPauseClick() }
}

@Composable
private fun IdleScreen(
    state: MainUiState,
    onStartClick: () -> Unit,
    onLocationConsentClick: () -> Unit,
    onCardsClick: () -> Unit,
) {
    Text(
        text = stringResource(R.string.app_name),
        fontSize = 48.sp,
        color = MaterialTheme.colorScheme.primary,
    )
    BigText(statusText(state))
    BigText(stringResource(state.phase.labelRes()))
    state.result?.let { if (state.phase == Phase.ENDED) EndResult(it, state.parked) }
    // 주행 중에는 숨길 뿐 아니라 ViewModel·세션에서도 다시 막는다
    if (state.showStart) BigButton(stringResource(R.string.start), onStartClick)
    if (state.showLocationConsent) BigButton(stringResource(R.string.location_consent), onLocationConsentClick)
    // 녹음 화면(SC-08) 입구. 시작 버튼과 같은 때(주차 중, 이용 밖)에만
    if (state.showStart) BigButton(stringResource(R.string.cards_open), onCardsClick)
    val blocked = if (!state.parked) R.string.block_not_parked else state.startBlock?.labelRes()
    blocked?.let { BigText(stringResource(it)) }
    if (state.unsupported.isNotEmpty()) {
        val names = state.unsupported.map { stringResource(it.labelRes()) }.joinToString(", ")
        Text(
            text = stringResource(R.string.status_unsupported, names),
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun BigText(text: String) {
    Text(
        text = text,
        fontSize = 36.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun BigButton(
    text: String,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, modifier = Modifier.sizeIn(minWidth = 200.dp, minHeight = 96.dp)) {
        Text(text = text, fontSize = 36.sp)
    }
}

/** 결과(SC-07)는 주차 중에만 보여 준다. */
@Composable
private fun EndResult(
    result: SessionResult,
    parked: Boolean,
) {
    // 한 Text에 줄바꿈으로 넣으면 기본 줄 높이(24sp)가 글자(36sp)보다 작아 두 줄이 겹친다. 줄마다 따로 그린다
    if (parked) {
        BigText(stringResource(R.string.result_found, result.found))
        BigText(stringResource(R.string.result_played, result.played, result.hints))
    } else {
        BigText(stringResource(R.string.result_after_parking))
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

private fun Phase.labelRes(): Int =
    when (this) {
        Phase.IDLE -> R.string.phase_idle
        Phase.GUIDING -> R.string.phase_guiding
        Phase.LISTENING -> R.string.phase_listening
        Phase.THINKING -> R.string.phase_thinking
        Phase.RESTING -> R.string.phase_resting
        Phase.PAUSED -> R.string.phase_paused
        Phase.ENDED -> R.string.phase_ended
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
