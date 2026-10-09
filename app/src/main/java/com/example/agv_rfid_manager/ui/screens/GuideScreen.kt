@file:Suppress("DEPRECATION")

package com.example.agv_rfid_manager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agv_rfid_manager.data.AGV_ERRORS
import com.example.agv_rfid_manager.data.AgvError
import com.example.agv_rfid_manager.ui.components.AppDialog
import com.example.agv_rfid_manager.ui.components.DialogButtons
import com.example.agv_rfid_manager.ui.components.DialogTitle
import com.example.agv_rfid_manager.ui.components.LargeTitle
import com.example.agv_rfid_manager.ui.components.Segmented
import com.example.agv_rfid_manager.ui.theme.AppTheme
import com.example.agv_rfid_manager.ui.theme.LocalIsKor
import com.example.agv_rfid_manager.ui.theme.glass
import com.example.agv_rfid_manager.ui.theme.mono
import com.example.agv_rfid_manager.ui.theme.t

// 가이드 탭: 앱 안내 / 에러 코드
@Composable
fun GuideContent(bottomPadding: Dp) {
    val c = AppTheme.colors
    val isKor = LocalIsKor.current
    var sub by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<AgvError?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        LargeTitle(t("tab_guide"))
        Spacer(Modifier.height(6.dp))
        Segmented(
            options = listOf(null to t("g_tab_app"), null to t("g_tab_err")),
            selected = sub,
            onSelect = { sub = it },
            height = 48.dp,
        )
        Spacer(Modifier.height(12.dp))
        val listPadding = PaddingValues(bottom = bottomPadding)
        if (sub == 0) {
            val guides = listOf(
                Triple(Icons.Rounded.Nfc, "g_1", "g_1_d"),
                Triple(Icons.Rounded.Edit, "g_2", "g_2_d"),
                Triple(Icons.Rounded.AllInclusive, "g_3", "g_3_d"),
                Triple(Icons.Rounded.Save, "g_4", "g_4_d"),
                Triple(Icons.Rounded.Undo, "g_5", "g_5_d"),
                Triple(Icons.Rounded.History, "g_6", "g_6_d"),
            )
            LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(guides) { (icon, title, desc) -> GuideCard(icon, t(title), t(desc)) }
            }
        } else {
            LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AGV_ERRORS) { err ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(20.dp))
                            .clickable { selected = err }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(err.code, style = mono(20.sp), color = c.red, modifier = Modifier.width(76.dp))
                        Text(if (isKor) err.nameKor else err.nameEng, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ChevronRight, null, tint = c.ink3)
                    }
                }
            }
        }
    }

    selected?.let { err ->
        AppDialog(onDismiss = { selected = null }) {
            DialogTitle("${err.code}  ${if (isKor) err.nameKor else err.nameEng}")
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (if (isKor) err.actionKor else err.actionEng).split("\n").forEach { line ->
                    Text(line, fontSize = 16.sp, lineHeight = 23.sp, color = c.ink2)
                }
            }
            DialogButtons(Triple(t("close"), c.oneBlue) { selected = null })
        }
    }
}

@Composable
private fun GuideCard(icon: ImageVector, title: String, desc: String) {
    val c = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.blue.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = c.blue, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Spacer(Modifier.height(4.dp))
            Text(desc, fontSize = 14.sp, lineHeight = 20.sp, color = c.ink2)
        }
    }
}
