package com.example.agv_rfid_manager.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

enum class GlassLevel { THIN, REGULAR, THICK }

// 배경(hazeSource)을 흐리게 비춰 줄 Haze 상태
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Glass 레이어.
 * - floating = true: hazeSource 밖에 떠 있는 요소(탭바·액션 바·시트) → 뒤 배경을 실제로 blur
 * - floating = false: 스크롤 콘텐츠 안의 카드 → 반투명 색만 사용 (배경이 부드러운 그라데이션이라 blur 불필요)
 * - 저사양 모드: blur 없이 같은 색의 불투명 배경
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    level: GlassLevel = GlassLevel.REGULAR,
    floating: Boolean = false,
): Modifier {
    val c = AppTheme.colors
    val low = AppTheme.lowEffects
    val haze = LocalHazeState.current
    val tint = when (level) {
        GlassLevel.THIN -> c.glassThin
        GlassLevel.REGULAR -> c.glassReg
        GlassLevel.THICK -> c.glassThick
    }
    val clipped = this.clip(shape)
    val filled = when {
        low -> clipped.background(if (level == GlassLevel.THICK) c.oneCard else c.solid)
        floating && haze != null -> clipped.hazeEffect(
            state = haze,
            style = HazeStyle(
                backgroundColor = c.bg,
                tints = listOf(HazeTint(tint)),
                blurRadius = if (level == GlassLevel.THIN) 24.dp else 32.dp,
                noiseFactor = 0f,
            ),
        )
        else -> clipped.background(tint)
    }
    return filled.border(if (low) 1.dp else 0.5.dp, if (low) c.line else c.hair, shape)
}

// 배경: 블루·민트·보라 그라데이션 블롭 (저사양 모드에서는 단색)
@Composable
fun MeshBackground(modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val low = AppTheme.lowEffects
    Canvas(modifier.fillMaxSize()) {
        drawRect(c.bg)
        if (!low) {
            val w = size.width
            val h = size.height
            fun blob(color: Color, x: Float, y: Float, r: Float) {
                drawRect(Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(x, y), radius = r))
            }
            blob(c.blob1, -0.05f * w, 0.06f * h, 1.10f * w)
            blob(c.blob2, 1.08f * w, 0.46f * h, 1.00f * w)
            blob(c.blob3, 0.18f * w, 1.02f * h, 1.15f * w)
        }
    }
}
