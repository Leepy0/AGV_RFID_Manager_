package com.example.agv_rfid_manager.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agv_rfid_manager.data.SettingsState
import com.example.agv_rfid_manager.data.TagActions
import com.example.agv_rfid_manager.data.TagState
import com.example.agv_rfid_manager.data.loadCode
import com.example.agv_rfid_manager.ui.components.ConfirmDialog
import com.example.agv_rfid_manager.ui.components.GlassTabBar
import com.example.agv_rfid_manager.ui.components.noRippleClick
import com.example.agv_rfid_manager.ui.screens.CodeListSheet
import com.example.agv_rfid_manager.ui.screens.GuideContent
import com.example.agv_rfid_manager.ui.screens.HistorySheet
import com.example.agv_rfid_manager.ui.screens.SettingsContent
import com.example.agv_rfid_manager.ui.screens.SheetType
import com.example.agv_rfid_manager.ui.screens.TagActionBar
import com.example.agv_rfid_manager.ui.screens.TagContent
import com.example.agv_rfid_manager.ui.screens.TagEffects
import com.example.agv_rfid_manager.ui.theme.AppTheme
import com.example.agv_rfid_manager.ui.theme.GlassLevel
import com.example.agv_rfid_manager.ui.theme.LocalHazeState
import com.example.agv_rfid_manager.ui.theme.MeshBackground
import com.example.agv_rfid_manager.ui.theme.glass
import com.example.agv_rfid_manager.ui.theme.t
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private val TAB_BAR_HEIGHT = 64.dp
private val ACTION_BAR_HEIGHT = 92.dp  // 10 + 버튼 72 + 10

// 앱 루트: 배경(hazeSource) 위에 탭별 화면, 그 위에 떠 있는 액션 바·탭바·시트
@Composable
fun MainApp(
    tag: TagState,
    actions: TagActions,
    settings: SettingsState,
    autoLow: Boolean,
    onResetAll: () -> Unit,
) {
    val c = AppTheme.colors
    val focus = LocalFocusManager.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<SheetType?>(null) }
    var lastSheet by remember { mutableStateOf(SheetType.CODES) }
    var confirmClear by remember { mutableStateOf(false) }
    val hazeState = remember { HazeState() }

    // 폴드 펼침·태블릿: 너비·높이 모두 600dp 이상이면 2단
    val conf = LocalConfiguration.current
    val twoPane = conf.screenWidthDp >= 600 && conf.screenHeightDp >= 600
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val tabBarBottom = navBottom + 12.dp
    val contentBottom = if (tab == 0 && !twoPane) tabBarBottom + TAB_BAR_HEIGHT + 10.dp + ACTION_BAR_HEIGHT + 12.dp
    else tabBarBottom + TAB_BAR_HEIGHT + 28.dp

    TagEffects(tag, actions, settings)
    // 앱 내 업데이트: 시작 시 1회 확인 (프로세스당)
    LaunchedEffect(sheet) { sheet?.let { lastSheet = it } }
    BackHandler(enabled = sheet != null) { sheet = null }

    val load: (String) -> Unit = { code -> loadCode(code, actions); focus.clearFocus(); sheet = null }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(Modifier.fillMaxSize().background(c.bg)) {
            // blur 대상이 되는 배경 + 화면 본문
            Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
                if (tab != 2) MeshBackground()
                when (tab) {
                    0 -> TagContent(tag, actions, settings, twoPane, contentBottom) { sheet = it }
                    1 -> GuideContent(contentBottom)
                    else -> SettingsContent(settings, autoLow, contentBottom, onResetAll)
                }
                // 떠 있는 탭바 뒤·아래로 비치는 본문을 배경색으로 서서히 가림
                val fadeColor = if (tab == 2) c.oneBg else c.bg
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(tabBarBottom + TAB_BAR_HEIGHT + 28.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, fadeColor.copy(alpha = 0.92f), fadeColor))),
                )
            }

            // 하단 액션 바 (1단 배치일 때만 떠 있음)
            if (tab == 0 && !twoPane) {
                TagActionBar(
                    state = tag,
                    actions = actions,
                    floating = true,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = tabBarBottom + TAB_BAR_HEIGHT + 10.dp),
                )
            }

            GlassTabBar(
                selected = tab,
                onSelect = { tab = it; sheet = null; focus.clearFocus() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = tabBarBottom)
                    .then(if (twoPane) Modifier.width(360.dp) else Modifier.fillMaxWidth().padding(horizontal = 22.dp)),
            )

            // 바텀 시트 (코드 목록·전체 기록)
            AnimatedVisibility(visible = sheet != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)).noRippleClick { sheet = null })
            }
            AnimatedVisibility(
                visible = sheet != null,
                enter = slideInVertically(tween(260)) { it },
                exit = slideOutVertically(tween(220)) { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    Modifier
                        .then(if (twoPane) Modifier.widthIn(max = 640.dp) else Modifier)
                        .fillMaxWidth()
                        .fillMaxHeight(0.74f)
                        .glass(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), GlassLevel.THICK, floating = true)
                        .noRippleClick { }
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                ) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).width(38.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.ink3.copy(alpha = 0.6f)),
                    )
                    Spacer(Modifier.height(12.dp))
                    val showing = sheet ?: lastSheet
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (showing == SheetType.CODES) t("sh_codes") else t("sh_history"),
                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.ink,
                        )
                        Spacer(Modifier.weight(1f))
                        if (showing == SheetType.HISTORY && tag.history.isNotEmpty()) {
                            Text(
                                t("clear_all"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.red,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { confirmClear = true }.padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(c.fill).clickable { sheet = null },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Close, null, tint = c.ink2, modifier = Modifier.size(18.dp)) }
                    }
                    if (showing == SheetType.CODES) CodeListSheet(tag.fullCode, onPick = load)
                    else HistorySheet(tag.history, onLoad = load)
                }
            }
        }
    }

    UpdateDialogs()

    if (confirmClear) {
        ConfirmDialog(
            title = t("d_clr_title"),
            message = t("d_clr_desc"),
            confirmText = t("clear_all"),
            destructive = true,
            onConfirm = { actions.clearHistory(); sheet = null },
            onDismiss = { confirmClear = false },
        )
    }
}
