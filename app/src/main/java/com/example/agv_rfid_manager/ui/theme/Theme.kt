package com.example.agv_rfid_manager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import com.example.agv_rfid_manager.data.tr

val LocalAppColors = staticCompositionLocalOf { DarkColors }
val LocalLowEffects = staticCompositionLocalOf { false }
val LocalIsKor = staticCompositionLocalOf { true }

object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
    val lowEffects: Boolean
        @Composable @ReadOnlyComposable get() = LocalLowEffects.current
}

// 현재 언어로 문자열 가져오기
@Composable
@ReadOnlyComposable
fun t(key: String): String = tr(key, LocalIsKor.current)

@Composable
fun AppTheme(dark: Boolean, lowEffects: Boolean, isKor: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.blue, background = c.bg, onBackground = c.ink,
            surface = c.solid, onSurface = c.ink, surfaceContainer = c.solid,
            onSurfaceVariant = c.ink2, outline = c.line,
        )
    } else {
        lightColorScheme(
            primary = c.blue, background = c.bg, onBackground = c.ink,
            surface = c.solid, onSurface = c.ink, surfaceContainer = c.solid,
            onSurfaceVariant = c.ink2, outline = c.line,
        )
    }
    CompositionLocalProvider(
        LocalAppColors provides c,
        LocalLowEffects provides lowEffects,
        LocalIsKor provides isKor,
    ) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography) {
            ProvideTextStyle(TextStyle(fontFamily = Pretendard, color = c.ink)) {
                content()
            }
        }
    }
}
