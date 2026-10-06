package com.example.agv_rfid_manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agv_rfid_manager.device.AppUpdater
import com.example.agv_rfid_manager.device.UpdStep
import com.example.agv_rfid_manager.ui.components.AppDialog
import com.example.agv_rfid_manager.ui.components.DialogButtons
import com.example.agv_rfid_manager.ui.components.DialogTitle
import com.example.agv_rfid_manager.ui.theme.AppTheme
import com.example.agv_rfid_manager.ui.theme.mono
import com.example.agv_rfid_manager.ui.theme.t

// 앱 내 업데이트 대화상자 (새 버전 안내 → 다운로드 진행 → 설치 허용 → 실패)
@Composable
fun UpdateDialogs() {
    val c = AppTheme.colors
    val context = LocalContext.current
    val curName = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
    }
    val curCode = remember { AppUpdater.currentCode(context) }

    when (val s = AppUpdater.step) {
        UpdStep.None -> Unit

        is UpdStep.Available -> AppDialog(onDismiss = { AppUpdater.dismiss() }) {
            DialogTitle(t("upd_title"))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("v${s.info.name}", style = mono(20.sp), color = c.oneBlue)
                Spacer(Modifier.width(8.dp))
                Text(t("upd_build").format(s.info.code), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink2)
            }
            Spacer(Modifier.height(4.dp))
            Text(t("upd_cur").format(curName, curCode), fontSize = 13.sp, color = c.ink3)
            if (s.info.notes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.fill)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) { Text(s.info.notes, fontSize = 14.sp, lineHeight = 20.sp, color = c.ink2) }
            }
            DialogButtons(
                Triple(t("upd_later"), c.oneBlue) { AppUpdater.dismiss() },
                Triple(t("upd_now"), c.oneBlue) { AppUpdater.download(context, s.info) },
            )
        }

        // 다운로드 중에는 바깥 터치·뒤로가기로 닫히지 않게 (취소 버튼으로만 중단)
        is UpdStep.Downloading -> AppDialog(onDismiss = { }) {
            DialogTitle(t("upd_dl"))
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(c.fill),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(s.progress.coerceIn(0f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(c.oneBlue),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text("${(s.progress * 100).toInt()}%", style = mono(16.sp), color = c.ink, modifier = Modifier.width(52.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(t("upd_dl_d"), fontSize = 14.sp, color = c.ink2)
            DialogButtons(Triple(t("cancel"), c.red) { AppUpdater.dismiss() })
        }

        is UpdStep.NeedPermission -> AppDialog(onDismiss = { AppUpdater.dismiss() }) {
            DialogTitle(t("upd_perm"))
            Spacer(Modifier.height(10.dp))
            Text(t("upd_perm_d"), fontSize = 15.sp, lineHeight = 22.sp, color = c.ink2)
            DialogButtons(
                Triple(t("cancel"), c.oneBlue) { AppUpdater.dismiss() },
                Triple(t("upd_perm_btn"), c.oneBlue) { AppUpdater.openInstallPermission(context) },
            )
        }

        is UpdStep.Failed -> AppDialog(onDismiss = { AppUpdater.dismiss() }) {
            DialogTitle(t("upd_fail"))
            Spacer(Modifier.height(10.dp))
            Text(t("upd_fail_d"), fontSize = 15.sp, lineHeight = 22.sp, color = c.ink2)
            DialogButtons(
                Triple(t("close"), c.oneBlue) { AppUpdater.dismiss() },
                Triple(t("retry"), c.oneBlue) { AppUpdater.download(context, s.info) },
            )
        }
    }
}
