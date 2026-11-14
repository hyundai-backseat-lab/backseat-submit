package io.backseatlab.playground.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.backseatlab.playground.R

private val Ssurround = FontFamily(Font(R.font.cafe24_ssurround))

// 본문은 시스템 고딕(Noto Sans CJK). 브랜드명·화면 제목만 써라운드
private fun sans(
    weight: FontWeight,
    size: TextUnit,
    line: TextUnit,
) = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = weight, fontSize = size, lineHeight = line)

private fun brand(
    size: TextUnit,
    line: TextUnit,
) = TextStyle(fontFamily = Ssurround, fontSize = size, lineHeight = line)

// 뒷좌석에서 2560×1440 화면을 보는 거리를 기준으로 잡았다. 주행 중 상태 글자는 32sp 이상(driving-ux-check)
val Typography =
    Typography(
        displaySmall = brand(52.sp, 64.sp),
        headlineLarge = brand(52.sp, 64.sp),
        headlineMedium = brand(44.sp, 56.sp),
        headlineSmall = sans(FontWeight.SemiBold, 40.sp, 52.sp),
        titleLarge = sans(FontWeight.SemiBold, 32.sp, 42.sp),
        bodyLarge = sans(FontWeight.Normal, 28.sp, 40.sp),
        bodyMedium = sans(FontWeight.Normal, 26.sp, 36.sp),
        bodySmall = sans(FontWeight.Normal, 20.sp, 28.sp),
        labelLarge = sans(FontWeight.SemiBold, 30.sp, 38.sp),
        labelMedium = sans(FontWeight.Medium, 22.sp, 30.sp),
    )
