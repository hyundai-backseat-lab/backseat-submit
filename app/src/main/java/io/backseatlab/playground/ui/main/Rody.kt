package io.backseatlab.playground.ui.main

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.backseatlab.playground.R
import io.backseatlab.playground.session.Phase
import io.backseatlab.playground.session.Signal
import io.backseatlab.playground.ui.theme.BrandBlue
import kotlinx.coroutines.delay

private const val CHEER_MS = 3000L
private const val BOUNCE_DP = -24f
private const val NUDGE_DP = 12f

private enum class Pose { WAVE, LISTEN, GUIDE, QUIET, REST, CHEER, LEFT, RIGHT, STOP }

private fun poseOf(
    state: MainUiState,
    cheering: Boolean,
): Pose {
    val ended = state.phase == Phase.ENDED && (state.result?.found ?: 0) > 0
    val turn = turnPose(state.active)
    return when {
        state.pauses.isNotEmpty() || state.phase == Phase.PAUSED -> Pose.QUIET
        state.phase == Phase.RESTING -> Pose.REST
        cheering -> Pose.CHEER
        turn != null -> turn
        state.showStart && state.startBlock != null -> Pose.STOP
        ended -> Pose.CHEER
        state.phase == Phase.LISTENING -> Pose.LISTEN
        state.phase == Phase.GUIDING -> Pose.GUIDE
        else -> Pose.WAVE
    }
}

private fun turnPose(active: List<Signal>): Pose? =
    when {
        Signal.TURN_LEFT in active -> Pose.LEFT
        Signal.TURN_RIGHT in active -> Pose.RIGHT
        else -> null
    }

/** 마스코트 로디. 놀이 상태마다 포즈가 바뀐다. 주행 중에는 부르지 않는다(F-06-01). */
@Composable
fun Rody(
    state: MainUiState,
    size: Dp = 260.dp,
) {
    val found = state.play?.found ?: 0
    var lastFound by remember { mutableIntStateOf(found) }
    var cheering by remember { mutableStateOf(false) }
    LaunchedEffect(found) {
        val up = found > lastFound
        lastFound = found
        cheering = up
        if (up) {
            delay(CHEER_MS)
            cheering = false
        }
    }

    val pose = poseOf(state, cheering)
    Box(contentAlignment = Alignment.TopEnd) {
        Image(
            painter = painterResource(pose.drawable()),
            contentDescription = null,
            modifier = Modifier.size(size).motion(pose, state.parked),
        )
        if (pose == Pose.QUIET) Text(stringResource(R.string.rody_quiet), fontSize = 40.sp, color = BrandBlue)
    }
}

private fun Pose.drawable(): Int =
    when (this) {
        Pose.WAVE -> R.drawable.rody_wave
        Pose.LISTEN -> R.drawable.rody_listen
        Pose.GUIDE -> R.drawable.rody_guide
        Pose.QUIET -> R.drawable.rody_quiet
        Pose.REST -> R.drawable.rody_rest
        Pose.CHEER -> R.drawable.rody_cheer
        Pose.LEFT -> R.drawable.rody_left
        Pose.RIGHT -> R.drawable.rody_right
        Pose.STOP -> R.drawable.rody_stop
    }

// 정답이면 위아래로, 방향지시등이면 가리키는 쪽으로 흔든다
private fun Pose.swing(): Pair<Float, Float>? =
    when (this) {
        Pose.CHEER -> 0f to BOUNCE_DP
        Pose.LEFT -> -NUDGE_DP to 0f
        Pose.RIGHT -> NUDGE_DP to 0f
        else -> null
    }

@Composable
private fun Modifier.motion(
    pose: Pose,
    parked: Boolean,
): Modifier {
    val (dx, dy) = pose.swing()?.takeIf { parked } ?: return this
    val t by rememberInfiniteTransition(label = "motion").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse),
        label = "t",
    )
    return graphicsLayer {
        translationX = dx * t * density
        translationY = dy * t * density
    }
}
