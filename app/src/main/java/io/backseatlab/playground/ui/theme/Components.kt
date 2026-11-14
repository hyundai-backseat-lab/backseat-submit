package io.backseatlab.playground.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// 터치 영역은 driving-ux-check 기준 76dp 이상
private val ButtonHeight = 96.dp
private val ButtonShape = RoundedCornerShape(24.dp)
val CardShape = RoundedCornerShape(24.dp)

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    color: Color = CardBackground,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, shape = CardShape, color = color, border = BorderStroke(1.dp, DividerLine)) {
        Column(Modifier.padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.m), content = content)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = BrandBlue,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonHeight).widthIn(min = 260.dp),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = Space.xl),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonHeight).widthIn(min = 200.dp),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = BrandBlueSoft, contentColor = BrandBlue),
        contentPadding = PaddingValues(horizontal = Space.xl),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** 덜 중요한 동작(위치 동의, 삭제 등). 글자만 보이지만 터치 영역은 같다. */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    color: Color = TextSecondary,
) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = ButtonHeight), shape = ButtonShape) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

/** 우측 상단 차량 상태 표시. 주 정보가 아니라 작게 둔다. */
@Composable
fun StatusBadge(
    text: String,
    on: Boolean,
) {
    Row(
        modifier =
            Modifier
                .background(CardBackground, CircleShape)
                .padding(horizontal = Space.m, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Spacer(Modifier.size(14.dp).background(if (on) SignalOn else SignalOff, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}

@Composable
fun Pill(
    text: String,
    container: Color = BrandBlueSoft,
    content: Color = BrandBlue,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = Modifier.background(container, CircleShape).padding(horizontal = Space.m, vertical = Space.xs),
    )
}

/** 결과 숫자 하나. */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = BrandBlue,
) {
    Column(
        modifier =
            modifier
                .background(PageBackground, CardShape)
                .padding(vertical = Space.l, horizontal = Space.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = accent, textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary, textAlign = TextAlign.Center)
    }
}

/** 마스코트(왼쪽)와 내용(오른쪽). 좁은 화면에서는 위아래로 쌓는다. 마스코트가 없으면 내용만. */
@Composable
fun HeroLayout(
    mascot: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (maxWidth >= WideLayout) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.edge),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                mascot?.invoke()
                Column(Modifier.weight(1f)) { content() }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(Space.l),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                mascot?.invoke()
                content()
            }
        }
    }
}
