@file:Suppress("DEPRECATION")
@file:OptIn(ExperimentalFoundationApi::class)

package com.example.agv_rfid_manager.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LooksOne
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TapAndPlay
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import com.example.agv_rfid_manager.data.HistItem
import com.example.agv_rfid_manager.data.HistKind
import com.example.agv_rfid_manager.data.Keys
import com.example.agv_rfid_manager.data.SettingsState
import com.example.agv_rfid_manager.data.TagActions
import com.example.agv_rfid_manager.data.TagState
import com.example.agv_rfid_manager.data.TagStatus
import com.example.agv_rfid_manager.data.commandDesc
import com.example.agv_rfid_manager.data.dataStore
import com.example.agv_rfid_manager.data.getCommands
import com.example.agv_rfid_manager.data.loadCode
import com.example.agv_rfid_manager.data.parseHistory
import com.example.agv_rfid_manager.ui.components.ActionButton
import com.example.agv_rfid_manager.ui.components.SectionHeader
import com.example.agv_rfid_manager.ui.components.Segmented
import com.example.agv_rfid_manager.ui.components.TintChip
import com.example.agv_rfid_manager.ui.components.rememberTick
import com.example.agv_rfid_manager.ui.theme.AppTheme
import com.example.agv_rfid_manager.ui.theme.GlassLevel
import com.example.agv_rfid_manager.ui.theme.LocalIsKor
import com.example.agv_rfid_manager.ui.theme.glass
import com.example.agv_rfid_manager.ui.theme.mono
import com.example.agv_rfid_manager.ui.theme.t
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class SheetType { CODES, HISTORY }

private val DEFAULT_PRESETS = listOf("0T01", "0T04", "0T07", "0T21", "0T22")

// 화면과 무관하게 항상 동작해야 하는 태그 로직 (연속 쓰기 복귀, 쓰기 대기 시간 초과 등)
@Composable
fun TagEffects(state: TagState, actions: TagActions, settings: SettingsState) {
    val cmdTypes = settings.cmdTypes
    LaunchedEffect(cmdTypes) {
        val list = cmdTypes.map { it.toString() }
        if (state.part2 !in list && list.isNotEmpty()) actions.setPart2(list.first())
    }
    val full = state.fullCode
    LaunchedEffect(full) { if (state.isContinuous) actions.requestWrite(full) }
    LaunchedEffect(state.status, state.isContinuous) {
        if (state.isContinuous) {
            if (state.status == TagStatus.WRITE_SUCCESS || state.status == TagStatus.ERROR) {
                delay(1000)
                if (state.isContinuous) actions.revertToWriting()
            }
        } else if (state.status == TagStatus.WRITING) {
            delay(5000)
            if (state.status == TagStatus.WRITING && !state.isContinuous) actions.timeout()
        }
    }
}

// 태그 탭 본문 (twoPane: 폴드 펼침·태블릿 2단 배치)
@Composable
fun TagContent(
    state: TagState,
    actions: TagActions,
    settings: SettingsState,
    twoPane: Boolean,
    bottomPadding: Dp,
    onOpenSheet: (SheetType) -> Unit,
) {
    val c = AppTheme.colors
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val cmdTypes = remember(settings.cmdTypes) { settings.cmdTypes.map { it.toString() } }
    val fullCode = state.fullCode
    val load: (String) -> Unit = { code -> loadCode(code, actions); focus.clearFocus() }
    val undo: (String) -> Unit = { code -> actions.requestWrite(code) }
    val openNfc: () -> Unit = { context.startActivity(Intent(android.provider.Settings.ACTION_NFC_SETTINGS)) }
    val clearFocusOnTap = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { focus.clearFocus() }) }

    if (twoPane) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp).then(clearFocusOnTap)) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp, bottom = bottomPadding),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    StatusCard(state, onLoad = load, onUndo = undo, onOpenNfc = openNfc, height = 320.dp, big = true)
                    if (settings.showHistory) RecentSection(state.history, onLoad = load) { onOpenSheet(SheetType.HISTORY) }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    InputCard(state, actions, cmdTypes) { onOpenSheet(SheetType.CODES) }
                    PresetRow(fullCode, onLoad = load)
                    Spacer(Modifier.weight(1f))
                    TagActionBar(state, actions, floating = false, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .then(clearFocusOnTap),
        ) {
            Spacer(Modifier.height(12.dp))
            StatusCard(state, onLoad = load, onUndo = undo, onOpenNfc = openNfc, height = 244.dp)
            Spacer(Modifier.height(12.dp))
            InputCard(state, actions, cmdTypes) { onOpenSheet(SheetType.CODES) }
            PresetRow(fullCode, onLoad = load)
            if (settings.showHistory) RecentSection(state.history, onLoad = load) { onOpenSheet(SheetType.HISTORY) }
            Spacer(Modifier.height(bottomPadding))
        }
    }
}

// ---------- 상태 카드 ----------
private data class StatusLook(
    val color: Color,
    val icon: ImageVector,
    val label: String,
    val sub: String,
    val code: String?,
    val desc: String,
)

@Composable
fun StatusCard(state: TagState, onLoad: (String) -> Unit, onUndo: (String) -> Unit, onOpenNfc: () -> Unit, height: Dp, big: Boolean = false) {
    val c = AppTheme.colors
    val low = AppTheme.lowEffects
    val isKor = LocalIsKor.current
    val s = state.status
    val cont = state.isContinuous
    // NFC가 꺼져 있으면 대기·쓰기 대기 대신 NFC 꺼짐을 우선 표시
    val nfcOff = !state.nfcEnabled && (s == TagStatus.IDLE || s == TagStatus.WRITING)

    val look = when {
        nfcOff -> StatusLook(c.red, Icons.Rounded.Nfc, t("nfc_off"), t("nfc_off_sub"), null, "")
        cont && s == TagStatus.WRITING -> StatusLook(c.indigo, Icons.Rounded.AllInclusive, t("st_cont"), t("st_cont_sub"), state.targetCode, commandDesc(state.targetCode, isKor))
        s == TagStatus.WRITING -> StatusLook(c.orange, Icons.Rounded.Edit, t("st_wait"), t("st_wait_sub"), state.targetCode, commandDesc(state.targetCode, isKor))
        s == TagStatus.READ_SUCCESS -> StatusLook(c.green, Icons.Rounded.CheckCircle, t("st_read"), t("st_read_at").format(state.eventTime), state.currentTag, commandDesc(state.currentTag, isKor))
        s == TagStatus.WRITE_SUCCESS -> StatusLook(c.blue, Icons.Rounded.DoneAll, t("st_done"), state.detail, state.currentTag, commandDesc(state.currentTag, isKor))
        s == TagStatus.ERROR -> StatusLook(c.red, Icons.Rounded.Error, t("st_err"), state.detail, null, "")
        else -> StatusLook(c.blue, Icons.Rounded.Nfc, t("st_idle"), t("st_idle_sub"), null, "")
    }
    val color by animateColorAsState(look.color, animationSpec = if (low) snap() else tween(250), label = "statusColor")

    // 성공 시 코드가 살짝 튀어 오르는 효과
    val scale = remember { Animatable(1f) }
    LaunchedEffect(s, state.eventTime) {
        if (!low && (s == TagStatus.READ_SUCCESS || s == TagStatus.WRITE_SUCCESS)) {
            scale.snapTo(0.94f)
            scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f))
        }
    }

    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .then(if (low) Modifier else Modifier.shadow(16.dp, shape, ambientColor = color, spotColor = color))
            .clip(shape)
            .background(c.solid)
            .border(1.dp, color.copy(alpha = 0.5f), shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(look.icon, null, tint = color, modifier = Modifier.size(21.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(look.label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
                if (look.sub.isNotEmpty()) {
                    Text(look.sub, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        when {
            nfcOff -> {
                Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(t("nfc_off_title"), fontSize = if (big) 34.sp else 28.sp, fontWeight = FontWeight.Bold, color = c.ink, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    TintChip(Icons.Rounded.Settings, t("nfc_open"), color, onOpenNfc)
                }
            }
            s == TagStatus.ERROR -> {
                Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(state.errorTitle, fontSize = if (big) 40.sp else 32.sp, fontWeight = FontWeight.Bold, color = c.ink, textAlign = TextAlign.Center)
                    if (state.targetCode.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(state.targetCode, style = mono(if (big) 22.sp else 18.sp), color = c.ink2)
                    }
                }
            }
            look.code == null -> {
                // 읽기 대기 안내
                Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(
                        modifier = Modifier
                            .size(if (big) 96.dp else 72.dp)
                            .drawBehind {
                                val r = size.minDimension / 2f
                                drawCircle(color.copy(alpha = 0.05f), radius = r + 20.dp.toPx())
                                drawCircle(color.copy(alpha = 0.08f), radius = r + 10.dp.toPx())
                            }
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.TapAndPlay, null, tint = color, modifier = Modifier.size(if (big) 46.dp else 36.dp)) }
                    Spacer(Modifier.height(24.dp))
                    Text(t("st_idle_hint"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink2)
                }
            }
            else -> {
                Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(
                        text = look.code,
                        style = mono(if (big) 92.sp else 72.sp, FontWeight.ExtraBold),
                        letterSpacing = 3.sp,
                        color = c.ink,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
                    )
                    if (look.desc.isNotEmpty()) {
                        Text(look.desc, fontSize = if (big) 17.sp else 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink2)
                    }
                }
                // 상태별 칩: 읽기 → 입력칸으로 불러오기, 쓰기 완료 → 되돌리기
                if (!cont && s == TagStatus.READ_SUCCESS && state.currentTag.length >= 4) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TintChip(Icons.Rounded.Edit, t("chip_load"), color) { onLoad(state.currentTag) }
                    }
                } else if (!cont && s == TagStatus.WRITE_SUCCESS && state.prevCode.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TintChip(Icons.Rounded.Undo, t("chip_undo").format(state.prevCode), color) { onUndo(state.prevCode) }
                    }
                }
            }
        }
    }
}

// ---------- 코드 입력 ----------
@Composable
fun InputCard(state: TagState, actions: TagActions, cmdTypes: List<String>, onOpenList: () -> Unit) {
    val c = AppTheme.colors
    val tick = rememberTick()
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    val latestPart3 by rememberUpdatedState(state.part3)
    val boxShape = RoundedCornerShape(16.dp)
    val boxMod = Modifier.clip(boxShape).background(c.solid).border(1.dp, c.line, boxShape)

    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp))
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("in_title"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink2)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(8.dp)).clickable { onOpenList() }.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Menu, null, tint = c.blue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(t("in_list"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.blue)
                Icon(Icons.Rounded.ChevronRight, null, tint = c.blue, modifier = Modifier.size(18.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(72.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 1번째 자리: 16진수 1글자
            BasicTextField(
                value = state.part1,
                onValueChange = { input ->
                    val f = input.text.filter { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }.uppercase()
                    actions.setPart1(input.copy(text = if (f.isNotEmpty()) f.last().toString() else "", selection = TextRange(if (f.isNotEmpty()) 1 else 0)))
                },
                textStyle = mono(34.sp).copy(color = c.ink, textAlign = TextAlign.Center),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                cursorBrush = SolidColor(c.blue),
                modifier = Modifier.weight(1f).fillMaxHeight().then(boxMod),
                decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() } },
            )
            // 2번째 자리: 커맨드 타입 선택
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().then(boxMod).clickable { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.part2, style = mono(34.sp), color = c.blue)
                    Icon(Icons.Rounded.ExpandMore, null, tint = c.ink3, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    cmdTypes.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, style = mono(24.sp), color = if (option == state.part2) c.blue else c.ink) },
                            onClick = { actions.setPart2(option); menuOpen = false },
                        )
                    }
                }
            }
            // 3·4번째 자리: 번호 (앞 두 칸과 같은 너비)
            BasicTextField(
                value = state.part3,
                onValueChange = { input ->
                    val f = input.text.filter { it.isDigit() }
                    if (f.length <= 2) actions.setPart3(input.copy(text = f))
                },
                textStyle = mono(34.sp).copy(color = c.ink, textAlign = TextAlign.Center),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                cursorBrush = SolidColor(c.blue),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(boxMod)
                    .onFocusChanged { fs ->
                        // 포커스 시 전체 선택 (바로 덮어쓰기)
                        if (fs.isFocused) scope.launch {
                            delay(80)
                            val v = latestPart3
                            actions.setPart3(v.copy(selection = TextRange(0, v.text.length)))
                        }
                    },
                decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() } },
            )
            // 번호 −/+ (장갑 착용 고려 56×72dp)
            StepButton(Icons.Rounded.Remove) {
                tick()
                val n = ((state.part3.text.toIntOrNull() ?: 0) - 1).coerceIn(0, 99)
                actions.setPart3(TextFieldValue(n.toString().padStart(2, '0')))
            }
            StepButton(Icons.Rounded.Add) {
                tick()
                val n = ((state.part3.text.toIntOrNull() ?: 0) + 1).coerceIn(0, 99)
                actions.setPart3(TextFieldValue(n.toString().padStart(2, '0')))
            }
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(c.fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = c.ink, modifier = Modifier.size(28.dp)) }
}

// ---------- 프리셋 ----------
@Composable
fun PresetRow(fullCode: String, onLoad: (String) -> Unit) {
    val c = AppTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick = rememberTick()
    val keys = remember { (1..5).map { Keys.preset(it) } }
    // 저장된 값이 없는 칸은 기본 프리셋으로 표시 (기존: 모두 0T00)
    val presets by remember(context) {
        context.dataStore.data.map { prefs -> keys.mapIndexed { i, k -> prefs[k] ?: DEFAULT_PRESETS[i] } }
    }.collectAsState(initial = DEFAULT_PRESETS)
    var editMode by remember { mutableStateOf(false) }
    // 롱프레스·편집 저장 시 항상 최신 입력값 사용
    val latestCode by rememberUpdatedState(fullCode)
    val savedPrefix = t("t_psav")

    fun save(i: Int) {
        val code = latestCode
        if (code.length == 4) {
            tick()
            scope.launch { context.dataStore.edit { it[keys[i]] = code } }
            Toast.makeText(context, "$savedPrefix $code", Toast.LENGTH_SHORT).show()
        }
    }

    SectionHeader(t("pre_title"), if (editMode) t("pre_done") else t("pre_edit")) { editMode = !editMode }
    if (editMode) {
        Text(t("pre_hint"), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.orange, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEachIndexed { i, code ->
            val selected = code == fullCode
            val shape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .glass(shape)
                    .then(
                        when {
                            editMode -> Modifier.border(1.5.dp, c.orange, shape)
                            // 선택된 프리셋: 파란 틴트 + 테두리 (시안 A)
                            selected -> Modifier.background(c.blue.copy(alpha = 0.14f), shape).border(1.5.dp, c.blue, shape)
                            else -> Modifier
                        },
                    )
                    .combinedClickable(
                        onClick = { if (editMode) save(i) else { tick(); onLoad(code) } },
                        onLongClick = { save(i) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(code, style = mono(17.sp), color = if (selected && !editMode) c.blue else c.ink, maxLines = 1)
            }
        }
    }
}

// ---------- 최근 작업 ----------
@Composable
fun RecentSection(history: List<String>, onLoad: (String) -> Unit, onAll: () -> Unit) {
    val c = AppTheme.colors
    val items = history.asSequence().map { parseHistory(it) }.filter { it.kind != HistKind.SYSTEM }.take(3).toList()
    SectionHeader(t("rec_title"), t("rec_all"), Icons.Rounded.ChevronRight, onAll)
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(horizontal = 14.dp, vertical = 2.dp)) {
        if (items.isEmpty()) {
            Text(t("rec_empty"), fontSize = 14.sp, color = c.ink3, modifier = Modifier.padding(vertical = 14.dp))
        }
        items.forEachIndexed { i, item ->
            HistoryRow(item, onLoad)
            if (i < items.lastIndex) HorizontalDivider(thickness = 0.5.dp, color = c.line)
        }
    }
}

@Composable
fun HistoryRow(item: HistItem, onLoad: (String) -> Unit) {
    val c = AppTheme.colors
    val (color, icon, label) = when (item.kind) {
        HistKind.READ -> Triple(c.green, Icons.Rounded.Nfc, t("h_read"))
        HistKind.WRITE -> Triple(c.blue, Icons.Rounded.Check, t("h_write"))
        HistKind.VERIFY_ERR -> Triple(c.red, Icons.Rounded.Error, t("h_verify"))
        HistKind.TIMEOUT -> Triple(c.red, Icons.Rounded.TimerOff, t("h_time"))
        HistKind.SYSTEM -> Triple(c.ink3, Icons.Rounded.PowerSettingsNew, t("h_boot"))
        HistKind.OTHER -> Triple(c.ink3, Icons.Rounded.Info, "")
    }
    val code = item.code
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(if (code != null) Modifier.clickable { onLoad(code) } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = color, modifier = Modifier.size(17.dp)) }
        Spacer(Modifier.width(11.dp))
        if (label.isNotEmpty()) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.ink2)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = if (item.kind == HistKind.SYSTEM) "" else item.body,
            style = mono(14.sp),
            color = c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(item.time, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.ink3)
    }
}

// ---------- 액션 바 (1회/연속 + 쓰기 버튼) ----------
@Composable
fun TagActionBar(state: TagState, actions: TagActions, floating: Boolean, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val tick = rememberTick()
    val fullCode = state.fullCode
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(30.dp), GlassLevel.REGULAR, floating).padding(10.dp)) {
        Segmented(
            options = listOf(Icons.Rounded.LooksOne to t("seg_one"), Icons.Rounded.AllInclusive to t("seg_cont")),
            selected = if (state.contSelected || state.isContinuous) 1 else 0,
            onSelect = { i ->
                tick()
                if (i == 1) {
                    state.contSelected = true
                } else {
                    state.contSelected = false
                    if (state.isContinuous) actions.setContinuous(false)
                }
            },
            opaque = true,
            height = 44.dp,
        )
        Spacer(Modifier.height(10.dp))
        when {
            state.isContinuous -> ActionButton(Icons.Rounded.Stop, t("btn_stop"), c.indigo, height = 72.dp) { actions.setContinuous(false) }
            state.status == TagStatus.WRITING -> ActionButton(Icons.Rounded.Close, t("btn_cancel"), c.orange, tinted = true, height = 72.dp) { actions.cancelWrite() }
            state.contSelected -> ActionButton(Icons.Rounded.AllInclusive, t("btn_cont_start"), c.indigo, code = fullCode, height = 72.dp) {
                actions.setContinuous(true)
                actions.requestWrite(fullCode)
            }
            else -> ActionButton(Icons.Rounded.Edit, t("btn_write"), c.blue, code = fullCode, height = 72.dp) { actions.requestWrite(fullCode) }
        }
    }
}

// ---------- 시트 내용 ----------
@Composable
fun CodeListSheet(fullCode: String, onPick: (String) -> Unit) {
    val c = AppTheme.colors
    val isKor = LocalIsKor.current
    LazyColumn(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.solid.copy(alpha = 0.7f))) {
        val list = getCommands(isKor)
        items(list) { (code, desc) ->
            val sel = code == fullCode
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp).clickable { onPick(code) }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(code, style = mono(18.sp), color = if (sel) c.blue else c.ink, modifier = Modifier.width(72.dp))
                Text(desc, fontSize = 15.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, color = if (sel) c.blue else c.ink, modifier = Modifier.weight(1f))
                if (sel) Icon(Icons.Rounded.Check, null, tint = c.blue, modifier = Modifier.size(22.dp))
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = c.line)
        }
    }
}

@Composable
fun HistorySheet(history: List<String>, onLoad: (String) -> Unit) {
    val c = AppTheme.colors
    val items = remember(history.size, history.firstOrNull()) { history.map { parseHistory(it) } }
    if (items.isEmpty()) {
        Text(t("rec_empty"), fontSize = 15.sp, color = c.ink3, modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.solid.copy(alpha = 0.7f)).padding(horizontal = 14.dp)) {
        items(items) { item ->
            HistoryRow(item, onLoad)
            HorizontalDivider(thickness = 0.5.dp, color = c.line)
        }
    }
}
