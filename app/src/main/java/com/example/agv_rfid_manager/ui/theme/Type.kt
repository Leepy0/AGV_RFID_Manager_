package com.example.agv_rfid_manager.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.example.agv_rfid_manager.R

// UI 폰트: Pretendard (한글 2,350자 서브셋)
val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

// 코드 폰트: JetBrains Mono (0/O, 1/I 구분)
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    Font(R.font.jetbrains_mono_extrabold, FontWeight.ExtraBold),
)

fun mono(size: TextUnit, weight: FontWeight = FontWeight.Bold) =
    TextStyle(fontFamily = JetBrainsMono, fontWeight = weight, fontSize = size)

private val base = Typography()

val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Pretendard),
    displayMedium = base.displayMedium.copy(fontFamily = Pretendard),
    displaySmall = base.displaySmall.copy(fontFamily = Pretendard),
    headlineLarge = base.headlineLarge.copy(fontFamily = Pretendard),
    headlineMedium = base.headlineMedium.copy(fontFamily = Pretendard),
    headlineSmall = base.headlineSmall.copy(fontFamily = Pretendard),
    titleLarge = base.titleLarge.copy(fontFamily = Pretendard),
    titleMedium = base.titleMedium.copy(fontFamily = Pretendard),
    titleSmall = base.titleSmall.copy(fontFamily = Pretendard),
    bodyLarge = base.bodyLarge.copy(fontFamily = Pretendard),
    bodyMedium = base.bodyMedium.copy(fontFamily = Pretendard),
    bodySmall = base.bodySmall.copy(fontFamily = Pretendard),
    labelLarge = base.labelLarge.copy(fontFamily = Pretendard),
    labelMedium = base.labelMedium.copy(fontFamily = Pretendard),
    labelSmall = base.labelSmall.copy(fontFamily = Pretendard),
)
