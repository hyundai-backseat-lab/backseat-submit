package io.backseatlab.playground.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.backseatlab.playground.R
import io.backseatlab.playground.session.PauseReason
import io.backseatlab.playground.session.Phase
import io.backseatlab.playground.ui.theme.AppCard
import io.backseatlab.playground.ui.theme.BrandBlue
import io.backseatlab.playground.ui.theme.BrandOrange
import io.backseatlab.playground.ui.theme.BrandOrangeSoft
import io.backseatlab.playground.ui.theme.CardBackground
import io.backseatlab.playground.ui.theme.CardShape
import io.backseatlab.playground.ui.theme.DividerLine
import io.backseatlab.playground.ui.theme.HeroLayout
import io.backseatlab.playground.ui.theme.PauseText
import io.backseatlab.playground.ui.theme.Pill
import io.backseatlab.playground.ui.theme.Space
import io.backseatlab.playground.ui.theme.TextSecondary

private val LEVEL_DESCRIPTIONS =
    listOf(R.string.level_easy_desc, R.string.level_normal_desc, R.string.level_hard_desc)

/** 주행·정차 중 요약 패널(SC-04): 상태, 미션 제목 한 줄, 난이도·순번·찾은 개수. 멈춤이면 사유(SC-05). */
@Composable
internal fun DrivingPanel(state: MainUiState) {
    val width = Modifier.widthIn(max = 1200.dp).fillMaxWidth()
    if (state.pauses.isNotEmpty()) {
        PauseCard(state.pauses.first(), big = true, modifier = width)
        return
    }
    AppCard(width) {
        PhaseLine(state.phase, big = true)
        val play = state.play
        if (play != null && play.missionNo > 0) {
            play.missionTitle?.let { Text(it, style = MaterialTheme.typography.headlineLarge) }
            Text(
                stringResource(R.string.summary_progress, play.difficulty.orEmpty(), play.missionNo, play.found),
                style = MaterialTheme.typography.headlineSmall,
                color = TextSecondary,
            )
        }
    }
}

/** 주차 중 이용: 난이도를 묻는 차례면 난이도 카드(SC-02), 아니면 미션 진행(SC-03). */
@Composable
internal fun ParkedSession(
    state: MainUiState,
    onDifficultyClick: (String) -> Unit,
) {
    val size = if (state.showDifficulty) 280.dp else 340.dp
    HeroLayout(mascot = { Rody(state, size) }, modifier = Modifier.widthIn(max = 1400.dp)) {
        when {
            state.showDifficulty -> DifficultyPicker(state.difficulties, onDifficultyClick)
            state.pauses.isNotEmpty() -> PauseCard(state.pauses.first(), big = false, Modifier.fillMaxWidth())
            else -> MissionCard(state)
        }
    }
}

@Composable
private fun MissionCard(state: MainUiState) {
    AppCard(Modifier.fillMaxWidth()) {
        val play = state.play
        if (play != null && play.missionNo > 0) {
            Pill(stringResource(R.string.summary_progress, play.difficulty.orEmpty(), play.missionNo, play.found))
        }
        val title = play?.missionTitle?.takeIf { play.missionNo > 0 } ?: stringResource(R.string.home_title)
        Text(title, style = MaterialTheme.typography.headlineLarge)
        PhaseLine(state.phase, big = false)
    }
}

/** 화면 버튼과 음성 답은 같은 글자로 처리된다. 카드를 누르면 바로 답이 된다. */
@Composable
private fun DifficultyPicker(
    levels: List<String>,
    onClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.l)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pick_title), style = MaterialTheme.typography.headlineMedium)
            Pill(stringResource(R.string.pick_count, levels.size))
        }
        Text(stringResource(R.string.pick_voice), style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            levels.forEachIndexed { i, level ->
                val description = LEVEL_DESCRIPTIONS.getOrNull(i)
                DifficultyCard(level, description, stars = i + 1, total = levels.size, Modifier.weight(1f)) {
                    onClick(level)
                }
            }
        }
    }
}

@Composable
private fun DifficultyCard(
    level: String,
    description: Int?,
    stars: Int,
    total: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 240.dp),
        shape = CardShape,
        color = CardBackground,
        border = BorderStroke(1.dp, DividerLine),
    ) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            val type = MaterialTheme.typography
            Text("★".repeat(stars) + "☆".repeat(total - stars), style = type.titleLarge, color = BrandOrange)
            Text(level, style = type.headlineMedium, color = BrandBlue)
            description?.let { Text(stringResource(it), style = type.bodyMedium, color = TextSecondary) }
        }
    }
}

/** 멈춤(SC-05): 무엇 때문에 멈췄는지와 언제 이어지는지. 조작을 요구하지 않는다. */
@Composable
private fun PauseCard(
    reason: PauseReason,
    big: Boolean,
    modifier: Modifier,
) {
    AppCard(modifier, color = BrandOrangeSoft) {
        Pill(stringResource(reason.causeRes()), container = CardBackground, content = PauseText)
        Text(
            stringResource(R.string.pause_title),
            style = if (big) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
        )
        Text(
            stringResource(reason.resumeRes()),
            style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
            color = PauseText,
        )
    }
}

@Composable
private fun PhaseLine(
    phase: Phase,
    big: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
        val dot = if (phase == Phase.LISTENING) BrandOrange else BrandBlue
        Spacer(Modifier.size(if (big) 20.dp else 16.dp).background(dot, CircleShape))
        Text(
            stringResource(phase.labelRes()),
            style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
            color = BrandBlue,
        )
    }
}

internal fun Phase.labelRes(): Int =
    when (this) {
        Phase.IDLE -> R.string.phase_idle
        Phase.GUIDING -> R.string.phase_guiding
        Phase.LISTENING -> R.string.phase_listening
        Phase.THINKING -> R.string.phase_thinking
        Phase.RESTING -> R.string.phase_resting
        Phase.PAUSED -> R.string.phase_paused
        Phase.ENDED -> R.string.phase_ended
    }

private fun PauseReason.causeRes(): Int =
    when (this) {
        PauseReason.TURN -> R.string.signal_turn
        PauseReason.HAZARD -> R.string.signal_hazard
        PauseReason.BRAKE -> R.string.signal_brake
        PauseReason.FOCUS_LOSS -> R.string.phase_paused
    }

private fun PauseReason.resumeRes(): Int =
    when (this) {
        PauseReason.FOCUS_LOSS -> R.string.pause_other_voice
        PauseReason.TURN -> R.string.pause_turn
        PauseReason.HAZARD -> R.string.pause_hazard
        PauseReason.BRAKE -> R.string.pause_brake
    }
