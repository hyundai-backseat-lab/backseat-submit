package io.backseatlab.playground.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 브랜드 색을 지키려고 기기 배경화면 색(dynamic color)과 다크 테마를 따르지 않는다
private val BrandColorScheme =
    lightColorScheme(
        primary = BrandBlue,
        onPrimary = Color.White,
        primaryContainer = BrandBlueSoft,
        onPrimaryContainer = BrandBlue,
        secondary = BrandOrange,
        background = PageBackground,
        onBackground = TextPrimary,
        surface = PageBackground,
        onSurface = TextPrimary,
        onSurfaceVariant = TextSecondary,
        outline = DividerLine,
    )

@Composable
fun BackseatPlaygroundTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BrandColorScheme,
        typography = Typography,
        content = content,
    )
}
