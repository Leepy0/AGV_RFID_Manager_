package com.example.agv_rfid_manager.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.agv_rfid_manager.data.Keys
import com.example.agv_rfid_manager.data.PerfSetting
import com.example.agv_rfid_manager.data.RFIDStore
import com.example.agv_rfid_manager.data.SettingsState
import com.example.agv_rfid_manager.data.ThemeMode
import com.example.agv_rfid_manager.device.AppUpdater
import com.example.agv_rfid_manager.device.CheckState
import com.example.agv_rfid_manager.device.CrashLogger
import com.example.agv_rfid_manager.ui.components.AppDialog
import com.example.agv_rfid_manager.ui.components.ChoiceDialog
import com.example.agv_rfid_manager.ui.components.ConfirmDialog
import com.example.agv_rfid_manager.ui.components.DialogButtons
import com.example.agv_rfid_manager.ui.components.DialogTitle
import com.example.agv_rfid_manager.ui.components.OneUiSwitch
import com.example.agv_rfid_manager.ui.theme.AppTheme
import com.example.agv_rfid_manager.ui.theme.mono
import com.example.agv_rfid_manager.ui.theme.t
import kotlinx.coroutines.launch

private enum class SettingDialog { THEME, FX, LANG, CMD, CRASH, RESET }

// 설정 화면 (갤럭시 One UI 스타일: 큰 제목 영역 + 둥근 그룹 카드 + 파란 요약 글자)
@Composable
fun SettingsContent(settings: SettingsState, autoLow: Boolean, bottomPadding: Dp, onResetAll: () -> Unit) {
    val c = AppTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<SettingDialog?>(null) }
    var crashText by remember { mutableStateOf("") }

    fun putBool(key: Preferences.Key<Boolean>, v: Boolean) { scope.launch { context.RFIDStore.edit { it[key] = v } } }
    fun putString(key: Preferences.Key<String>, v: String) { scope.launch { context.RFIDStore.edit { it[key] = v } } }

    val version = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
    }
    val versionCode = remember { AppUpdater.currentCode(context) }
    val themeLabel = when (settings.themeMode) {
        ThemeMode.LIGHT -> t("th_light")
        ThemeMode.SYSTEM -> t("th_system")
        else -> t("th_dark")
    }
    val autoLabel = t("fx_auto_d").format(if (autoLow) t("fx_low") else t("fx_full"))
    val fxLabel = when (settings.perfMode) {
        PerfSetting.FULL -> t("fx_full")
        PerfSetting.LOW -> t("fx_low")
        else -> "${t("fx_auto")} · $autoLabel"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.oneBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // 큰 제목 영역 (한 손 조작을 위해 목록은 아래에서 시작)
        Column(
            Modifier.fillMaxWidth().height(176.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(t("s_title"), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Spacer(Modifier.height(8.dp))
            Text("AGV RFID Manager", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.ink2)
        }

        OneGroup(t("s_screen")) {
            OneRow(t("s_theme"), themeLabel, summaryBlue = true) { dialog = SettingDialog.THEME }
            OneDivider()
            OneRow(t("s_fx"), fxLabel, summaryBlue = true) { dialog = SettingDialog.FX }
            OneDivider()
            OneRow(t("s_lang"), if (settings.isKor) "한국어" else "English", summaryBlue = true) { dialog = SettingDialog.LANG }
            OneDivider()
            OneRow(t("s_hist_view"), trailing = { OneUiSwitch(settings.showHistory) }) { putBool(Keys.SHOW_HISTORY, !settings.showHistory) }
        }
        OneGroup(t("s_feedback")) {
            OneRow(t("s_vib"), t("s_vib_d"), trailing = { OneUiSwitch(settings.vib) }) { putBool(Keys.VIB, !settings.vib) }
            OneDivider()
            OneRow(t("s_snd"), t("s_snd_d"), trailing = { OneUiSwitch(settings.sound) }) { putBool(Keys.SOUND, !settings.sound) }
        }
        OneGroup(t("s_nfc")) {
            OneRow(t("s_verify"), t("s_verify_d"), trailing = { OneUiSwitch(settings.autoVerify) }) { putBool(Keys.AUTO_VERIFY, !settings.autoVerify) }
            OneDivider()
            OneRow(t("s_cmd"), settings.cmdTypes, summaryBlue = true) { dialog = SettingDialog.CMD }
        }
        OneGroup(t("s_data")) {
            OneRow(t("s_crash"), t("s_crash_d")) { crashText = CrashLogger.read(context); dialog = SettingDialog.CRASH }
            OneDivider()
            OneRow(t("s_reset"), t("s_reset_d"), titleColor = c.red) { dialog = SettingDialog.RESET }
        }
        OneGroup(t("s_info")) {
            OneRow(t("s_ver"), t("s_ver_v").format(version, versionCode))
            OneDivider()
            val latest = AppUpdater.latest
            val updSummary = when (AppUpdater.check) {
                CheckState.CHECKING -> t("s_upd_chk")
                CheckState.LATEST -> t("s_upd_latest")
                CheckState.AVAILABLE -> latest?.let { t("s_upd_new").format(it.name, it.code) } ?: t("s_upd_d")
                CheckState.FAILED -> t("s_upd_fail")
                CheckState.NONE -> t("s_upd_d")
            }
            OneRow(t("s_upd"), updSummary, summaryBlue = AppUpdater.check == CheckState.AVAILABLE) { AppUpdater.manualCheck(context) }
        }
        Spacer(Modifier.height(bottomPadding))
    }

    when (dialog) {
        SettingDialog.THEME -> ChoiceDialog(
            title = t("s_theme"),
            options = listOf(t("th_light") to null, t("th_dark") to t("th_default"), t("th_system") to t("th_system_d")),
            selected = when (settings.themeMode) { ThemeMode.LIGHT -> 0; ThemeMode.SYSTEM -> 2; else -> 1 },
            onSelect = { i -> putString(Keys.THEME_MODE, listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)[i]) },
            onDismiss = { dialog = null },
        )
        SettingDialog.FX -> ChoiceDialog(
            title = t("s_fx"),
            options = listOf(t("fx_auto") to autoLabel, t("fx_full") to t("fx_full_d"), t("fx_low") to t("fx_low_d")),
            selected = when (settings.perfMode) { PerfSetting.FULL -> 1; PerfSetting.LOW -> 2; else -> 0 },
            onSelect = { i -> putString(Keys.PERF_MODE, listOf(PerfSetting.AUTO, PerfSetting.FULL, PerfSetting.LOW)[i]) },
            onDismiss = { dialog = null },
        )
        SettingDialog.LANG -> ChoiceDialog(
            title = t("s_lang"),
            options = listOf("한국어" to null, "English" to null),
            selected = if (settings.isKor) 0 else 1,
            onSelect = { i -> putBool(Keys.IS_KOR, i == 0) },
            onDismiss = { dialog = null },
        )
        SettingDialog.CMD -> CmdTypeDialog(settings.cmdTypes, onSave = { putString(Keys.CMD_TYPES, it.ifEmpty { "T" }) }, onDismiss = { dialog = null })
        SettingDialog.CRASH -> AppDialog(onDismiss = { dialog = null }) {
            DialogTitle(t("s_crash"))
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.fill)
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
            ) {
                Text(
                    if (crashText.isEmpty()) t("c_empty") else crashText,
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp, color = c.ink2,
                )
            }
            val empty = crashText.isEmpty()
            DialogButtons(
                Triple(t("delete"), if (empty) c.ink3 else c.red) { if (!empty) { CrashLogger.clear(context); crashText = "" } },
                Triple(t("share"), if (empty) c.ink3 else c.oneBlue) {
                    if (!empty) {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "AGV RFID Manager crash log")
                            putExtra(Intent.EXTRA_TEXT, crashText)
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    }
                },
                Triple(t("close"), c.oneBlue) { dialog = null },
            )
        }
        SettingDialog.RESET -> ConfirmDialog(
            title = t("s_reset"),
            message = t("s_reset_q"),
            confirmText = t("s_reset_ok"),
            destructive = true,
            onConfirm = onResetAll,
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun OneGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = AppTheme.colors
    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink2, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp))
    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.oneCard),
        content = content,
    )
}

@Composable
private fun OneDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp, color = AppTheme.colors.line)
}

@Composable
private fun OneRow(
    title: String,
    summary: String? = null,
    summaryBlue: Boolean = false,
    titleColor: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 64.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = titleColor ?: c.ink)
            if (!summary.isNullOrEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(summary, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (summaryBlue) c.oneBlue else c.ink2)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.padding(start = 12.dp))
            trailing()
        }
    }
}

// 커맨드 타입 입력 (영문 대문자, 중복 제거, 순서 유지)
@Composable
private fun CmdTypeDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    var value by remember { mutableStateOf(current) }
    AppDialog(onDismiss) {
        DialogTitle(t("s_cmd"))
        Spacer(Modifier.height(6.dp))
        Text(t("s_cmd_d"), fontSize = 14.sp, color = c.ink2)
        Spacer(Modifier.height(14.dp))
        BasicTextField(
            value = value,
            onValueChange = { input -> value = input.filter { it.isLetter() }.uppercase().toCharArray().distinct().joinToString("") },
            textStyle = mono(24.sp).copy(color = c.ink),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            cursorBrush = SolidColor(c.oneBlue),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.fill)
                .border(1.dp, c.oneBlue.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
        DialogButtons(
            Triple(t("cancel"), c.oneBlue, onDismiss),
            Triple(t("save"), c.oneBlue) { onSave(value); onDismiss() },
        )
    }
}
