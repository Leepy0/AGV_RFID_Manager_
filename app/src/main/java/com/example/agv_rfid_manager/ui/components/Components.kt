@file:Suppress("DEPRECATION")

package com.example.agv_rfid_manager.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agv_rfid_manager.ui.theme.AppTheme
import com.example.agv_rfid_manager.ui.theme.GlassLevel
import com.example.agv_rfid_manager.ui.theme.glass
import com.example.agv_rfid_manager.ui.theme.mono
import com.example.agv_rfid_manager.ui.theme.t

// 버튼 터치 햅틱
@Composable
fun rememberTick(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); Unit } }
}

// 리플 없는 클릭 (배경 막기용)
@Composable
fun Modifier.noRippleClick(onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

// 화면 제목 + 오른쪽 요소
@Composable
fun LargeTitle(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, color = AppTheme.colors.ink)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

// 상태 칩 (점 + 문구)
@Composable
fun StatusPill(text: String, dotColor: Color, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .glass(RoundedCornerShape(16.dp), GlassLevel.THIN)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(14.dp).clip(CircleShape).background(dotColor.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor)) }
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.ink)
    }
}

// 섹션 머리말 (왼쪽 제목, 오른쪽 파란 링크)
@Composable
fun SectionHeader(title: String, action: String? = null, actionIcon: ImageVector? = null, onAction: (() -> Unit)? = null) {
    val c = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 2.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink2)
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Row(
                modifier = Modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(8.dp)).clickable { onAction?.invoke() }.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(action, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.blue)
                if (actionIcon != null) Icon(actionIcon, null, tint = c.blue, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// iOS 스타일 세그먼트 컨트롤
@Composable
fun Segmented(
    options: List<Pair<ImageVector?, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    opaque: Boolean = false,
) {
    val c = AppTheme.colors
    val low = AppTheme.lowEffects
    val bg = if (opaque) c.ink.copy(alpha = if (c.isDark) 0.06f else 0.07f).compositeOver(c.solid) else c.fill
    Row(
        modifier = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(12.dp)).background(bg).padding(3.dp),
    ) {
        options.forEachIndexed { i, (icon, label) ->
            val on = i == selected
            val shape = RoundedCornerShape(9.dp)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (on && !low) Modifier.shadow(2.dp, shape) else Modifier)
                    .clip(shape)
                    .background(if (on) c.segOn else Color.Transparent)
                    .clickable { onSelect(i) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(icon, null, tint = if (on) c.ink else c.ink2, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (on) c.ink else c.ink2, maxLines = 1)
            }
        }
    }
}

// 상태 색 칩 버튼 (되돌리기·불러오기)
@Composable
fun TintChip(icon: ImageVector, text: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(color.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// 큰 실행 버튼 (쓰기·취소·연속 종료)
@Composable
fun ActionButton(
    icon: ImageVector,
    text: String,
    color: Color,
    tinted: Boolean = false,
    code: String? = null,
    height: Dp = 56.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    val tick = rememberTick()
    val big = height >= 64.dp
    val bg = if (tinted) color.copy(alpha = 0.16f).compositeOver(c.solid) else color
    val fg = if (tinted) color else Color.White
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(if (big) 22.dp else 19.dp))
            .background(bg)
            .clickable { tick(); onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(if (big) 26.dp else 22.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = fg, fontSize = if (big) 20.sp else 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (code != null) {
            Spacer(Modifier.width(10.dp))
            Text(code, style = mono(if (big) 20.sp else 18.sp), color = fg.copy(alpha = 0.9f))
        }
    }
}

// 떠 있는 Glass 탭바
@Composable
fun GlassTabBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val tick = rememberTick()
    val items = listOf(
        Icons.Rounded.Nfc to t("tab_tag"),
        Icons.Rounded.MenuBook to t("tab_guide"),
        Icons.Rounded.Settings to t("tab_set"),
    )
    Row(
        modifier = modifier
            .height(64.dp)
            .glass(RoundedCornerShape(32.dp), GlassLevel.THIN, floating = true)
            .padding(6.dp),
    ) {
        items.forEachIndexed { i, (icon, label) ->
            val on = i == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (on) c.blue.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { if (!on) tick(); onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, null, tint = if (on) c.blue else c.ink2, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(3.dp))
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (on) c.blue else c.ink2)
            }
        }
    }
}

// One UI 스타일 스위치
@Composable
fun OneUiSwitch(checked: Boolean) {
    val c = AppTheme.colors
    Box(
        modifier = Modifier
            .width(46.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (checked) c.oneBlue else Color.Transparent)
            .then(if (checked) Modifier else Modifier.border(2.dp, c.ink3, RoundedCornerShape(13.dp)))
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(if (checked) Color.White else c.ink3))
    }
}

// One UI 스타일 라디오
@Composable
fun OneUiRadio(selected: Boolean) {
    val c = AppTheme.colors
    Box(
        modifier = Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (selected) c.oneBlue else c.ink3, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(c.oneBlue))
    }
}

// 하단 고정 대화상자 (One UI 스타일)
@Composable
fun AppDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = AppTheme.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier.fillMaxSize().noRippleClick(onDismiss).padding(12.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(c.oneCard)
                    .noRippleClick { }
                    .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 6.dp),
                content = content,
            )
        }
    }
}

@Composable
fun DialogTitle(text: String) {
    Text(text, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.ink)
}

@Composable
fun DialogButtons(vararg buttons: Triple<String, Color, () -> Unit>) {
    val c = AppTheme.colors
    HorizontalDivider(modifier = Modifier.padding(top = 12.dp), thickness = 0.5.dp, color = c.line)
    Row(Modifier.fillMaxWidth()) {
        buttons.forEach { (label, color, action) ->
            Box(
                modifier = Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = action),
                contentAlignment = Alignment.Center,
            ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = color) }
        }
    }
}

// 확인 대화상자
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val c = AppTheme.colors
    AppDialog(onDismiss) {
        DialogTitle(title)
        Spacer(Modifier.height(10.dp))
        Text(message, fontSize = 15.sp, lineHeight = 22.sp, color = c.ink2)
        DialogButtons(
            Triple(t("cancel"), c.oneBlue, onDismiss),
            Triple(confirmText, if (destructive) c.red else c.oneBlue) { onConfirm(); onDismiss() },
        )
    }
}

// 라디오 선택 대화상자
@Composable
fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String?>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = AppTheme.colors
    AppDialog(onDismiss) {
        DialogTitle(title)
        Spacer(Modifier.height(8.dp))
        options.forEachIndexed { i, (label, desc) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(i); onDismiss() }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OneUiRadio(i == selected)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(label, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = c.ink)
                    if (desc != null) Text(desc, fontSize = 13.5.sp, color = c.ink2)
                }
            }
        }
        DialogButtons(Triple(t("cancel"), c.oneBlue, onDismiss))
    }
}
