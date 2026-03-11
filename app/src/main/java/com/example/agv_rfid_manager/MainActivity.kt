package com.example.agv_rfid_manager

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.nfc.*
import android.nfc.tech.NfcV
import android.os.*
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

val Context.dataStore by preferencesDataStore(name = "agv_settings")
val Context.RFIDStore by preferencesDataStore(name = "RFID_settings")

val VIB_KEY = booleanPreferencesKey("vib_enabled")
val SOUND_KEY = booleanPreferencesKey("sound_enabled")
val AUTO_VERIFY_KEY = booleanPreferencesKey("auto_verify")
val IS_KOR_KEY = booleanPreferencesKey("is_kor")
val CMD_TYPES_KEY = stringPreferencesKey("cmd_types")

enum class TagStatus { IDLE, WRITING, READ_SUCCESS, WRITE_SUCCESS, ERROR }

private val MONO_STYLE = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)

// --- 다국어 리소스 ---
fun tr(k: String, isKor: Boolean): String = Strings[k]?.get(if (isKor) 1 else 0) ?: k
val Strings = mapOf(
    "tab_main" to arrayOf("NFC MAIN", "NFC 메인"),
    "tab_guide" to arrayOf("Guide", "가이드"),
    "tab_set" to arrayOf("Settings", "설정"),
    "g_title" to arrayOf("How to Use App", "앱 사용 가이드"),
    "g_1" to arrayOf("1. READ", "1. 읽기"),
    "g_1_d" to arrayOf("Bring phone close to the TAG.\n(Auto Read)", "스마트폰을 태그에 가까이 대세요.\n(자동으로 읽습니다)"),
    "g_2" to arrayOf("2. WRITE", "2. 쓰기"),
    "g_2_d" to arrayOf("1) Enter Code\n2) Press [WRITE]\n3) Bring phone to TAG", "1) 코드 입력\n2) [쓰기] 터치\n3) 스마트폰을 태그에 대기"),
    "g_3" to arrayOf("3. CONTINUOUS", "3. 연속 쓰기"),
    "g_3_d" to arrayOf("1) Long Press [WRITE]\n2) Bring phone to many TAGs\n3) Press [STOP] to finish", "1) [쓰기] 길게 터치\n2) 여러 태그에 연속으로 대기\n3) [정지] 터치하여 종료"),
    "g_4" to arrayOf("4. PRESET (Memory)", "4. 프리셋 (저장)"),
    "g_4_d" to arrayOf("• Long Press: Save current code\n• Short Press: Load saved code", "• 길게 터치: 현재 코드 저장\n• 짧게 터치: 저장된 코드 불러오기"),
    "g_5" to arrayOf("5. ADJUST", "5. 숫자 조정"),
    "g_5_d" to arrayOf("Use [+] and [-] buttons to easily change numbers.", "[+] 와 [-] 버튼으로 숫자를 쉽게 조절하세요."),
    "g_6" to arrayOf("6. HISTORY", "6. 작업 기록"),
    "g_6_d" to arrayOf("Tap the 'HISTORY' text to view and clear all operation logs.", "'HISTORY' 글자를 터치하여 기록을 확인하거나 지우세요."),
    "s_title" to arrayOf("App Settings", "앱 설정"),
    "s_pref" to arrayOf("Preferences", "기본 설정"),
    "s_lang" to arrayOf("Language", "언어 설정"),
    "s_lang_d" to arrayOf("Select App Language", "앱의 언어를 선택하세요"),
    "s_cmd" to arrayOf("Command Types", "커맨드 타입 (알파벳)"),
    "s_cmd_d" to arrayOf("Allowed alphabets (e.g. TIOBD)", "사용할 알파벳 입력, 순서 변경 가능 (예: TIOBD)"),
    "s_vib" to arrayOf("Vibration Feedback", "진동 피드백"),
    "s_vib_d" to arrayOf("Long vibration on error, short on success", "성공 시 짧게, 오류 시 길게 진동"),
    "s_snd" to arrayOf("Sound Feedback", "소리 피드백"),
    "s_snd_d" to arrayOf("Play beep sound on success/error", "작업 성공 및 오류 시 비프음 발생"),
    "s_nfc" to arrayOf("NFC Operations", "NFC 설정"),
    "s_verify" to arrayOf("Auto-Verify Write", "태그 입력 후 자동 검증"),
    "s_verify_d" to arrayOf("Read tag automatically after writing", "입력 완료 후 읽은 데이터와 검증 진행"),
    "s_data" to arrayOf("Data Management", "데이터 관리"),
    "s_clear" to arrayOf("Clear All History", "모든 기록 지우기"),
    "s_clear_d" to arrayOf("Delete all operation logs permanently", "모든 작업 기록을 영구적으로 삭제합니다"),
    "s_clr_title" to arrayOf("Reset Everything", "전체 초기화"),
    "s_clr_desc" to arrayOf("All operation logs and settings will be reset. Continue?", "모든 작업 기록이 삭제되며 설정값도 모두 초기화됩니다. 계속하시겠습니까?"),
    "s_ver" to arrayOf("App Version", "앱 버전"),
    "m_title" to arrayOf("AGV RFID Manager", "AGV RFID Manager"),
    "m_on" to arrayOf("🟢 NFC ON", "🟢 NFC 켜짐"),
    "m_off" to arrayOf("🔴 NFC OFF", "🔴 NFC 꺼짐"),
    "m_wait" to arrayOf("WAITING", "대기 중"),
    "m_succ" to arrayOf("SUCCESS", "성공"),
    "m_err" to arrayOf("ERROR", "오류"),
    "m_c_wait" to arrayOf("CONTINUOUS\nWAITING", "연속 쓰기\n대기 중"),
    "m_stop" to arrayOf("STOP CONTINUOUS", "연속 쓰기 종료"),
    "m_write" to arrayOf("WRITE\n(Long press for Continuous Mode)", "쓰기\n(길게 눌러서 연속 모드)"),
    "m_hist" to arrayOf("HISTORY", "히스토리"),
    "m_clr" to arrayOf("CLEAR ALL", "클리어"),
    "l_title" to arrayOf("Operation Logs", "전체 로그"),
    "l_call" to arrayOf("CLEAR ALL", "클리어"),
    "l_empty" to arrayOf("No logs available.", "기록이 없습니다."),
    "d_noff" to arrayOf("NFC OFF", "NFC 꺼짐"),
    "d_ndis" to arrayOf("NFC is disabled.\nGo to settings to enable?", "NFC 기능이 꺼져 있습니다.\n설정으로 이동하시겠습니까?"),
    "d_set" to arrayOf("SETTINGS", "설정 이동"),
    "d_cls" to arrayOf("CLOSE", "닫기"),
    "d_clr_title" to arrayOf("Clear History", "기록 지우기"),
    "d_clr_desc" to arrayOf("Are you sure you want to clear all operation logs?", "모든 작업 기록을 지우시겠습니까?"),
    "g_guide" to arrayOf("Common Tags", "공통 태그"),
    "g_tag" to arrayOf("TAG", "태그"),
    "g_func" to arrayOf("FUNCTION", "기능"),
    "t_ndis" to arrayOf("NFC DISABLED", "NFC 꺼짐"),
    "t_psav" to arrayOf("PRESET SAVED:", "프리셋 저장됨:"),
    "e_comm" to arrayOf("COMM ERR", "통신 오류"),
    "e_time" to arrayOf("TIMEOUT", "시간 초과")
)

fun getCommands(isKor: Boolean) = listOf(
    "0T01" to if (isKor) "정지" else "Stop", "0T02" to if (isKor) "저속 주행" else "Low Speed",
    "0T03" to if (isKor) "초저속 주행" else "LLow Speed", "0T04" to if (isKor) "로딩" else "Loading",
    "0T05" to if (isKor) "고속 주행" else "Driving Speed", "0T07" to if (isKor) "언로딩" else "Unloading",
    "0T08" to if (isKor) "좌측 분기" else "Left Branch", "0T09" to if (isKor) "우측 분기" else "Right Branch",
    "0T21" to if (isKor) "우회전 90도" else "CW 90 Turn", "0T22" to if (isKor) "좌회전 90도" else "CCW 90 Turn",
    "0T23" to if (isKor) "우회전 180도" else "CW 180 Turn", "0T24" to if (isKor) "좌회전 180도" else "CCW 180 Turn",
    "0T25" to if (isKor) "우회전 90도 FB" else "CW 90 Turn, FB", "0T26" to if (isKor) "좌회전 90도 FB" else "CCW 90 Turn, FB",
    "0T27" to if (isKor) "장애물 센서 OFF" else "OBS OFF", "0T28" to if (isKor) "장애물 센서 ON" else "OBS ON"
)

@Composable
fun MainApp(
    currentTag: String, tagStatus: TagStatus, targetCode: String, historyList: List<String>,
    isNfcEnabled: Boolean, isContinuousMode: Boolean, isKor: Boolean, cmdTypesStr: String,
    onWriteRequested: (String) -> Unit, onContinuousToggled: (Boolean) -> Unit,
    onRevertToWriting: () -> Unit, onTimeout: () -> Unit, onClearHistory: () -> Unit, onResetAll: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(icon = { Icon(Icons.Default.Nfc, null) }, label = { Text(tr("tab_main", isKor)) }, selected = selectedTab == 0, onClick = { selectedTab = 0 })
                NavigationBarItem(icon = { Icon(Icons.Default.MenuBook, null) }, label = { Text(tr("tab_guide", isKor)) }, selected = selectedTab == 1, onClick = { selectedTab = 1 })
                NavigationBarItem(icon = { Icon(Icons.Default.Settings, null) }, label = { Text(tr("tab_set", isKor)) }, selected = selectedTab == 2, onClick = { selectedTab = 2 })
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> AGVControlScreen(currentTag, tagStatus, targetCode, historyList, isNfcEnabled, isContinuousMode, isKor, cmdTypesStr.map { it.toString() }, onWriteRequested, onContinuousToggled, onRevertToWriting, onTimeout, onClearHistory)
                1 -> GuideScreen(isKor)
                2 -> AppSettingsScreen(isKor, cmdTypesStr, onResetAll)
            }
        }
    }
}

@Composable
fun GuideScreen(isKor: Boolean) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(tr("g_title", isKor), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { GuideCard(Icons.Default.Nfc, tr("g_1", isKor), tr("g_1_d", isKor)) }
        item { GuideCard(Icons.Default.Edit, tr("g_2", isKor), tr("g_2_d", isKor)) }
        item { GuideCard(Icons.Default.AllInclusive, tr("g_3", isKor), tr("g_3_d", isKor)) }
        item { GuideCard(Icons.Default.Save, tr("g_4", isKor), tr("g_4_d", isKor)) }
        item { GuideCard(Icons.Default.UnfoldMore, tr("g_5", isKor), tr("g_5_d", isKor)) }
        item { GuideCard(Icons.Default.History, tr("g_6", isKor), tr("g_6_d", isKor)) }
    }
}

@Composable
fun GuideCard(icon: ImageVector, title: String, desc: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = title, modifier = Modifier.size(48.dp), tint = Color(0xFF1976D2))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(desc, fontSize = 14.sp, color = Color.DarkGray)
            }
        }
    }
}

@Composable
fun AppSettingsScreen(isKor: Boolean, cmdTypesStr: String, onResetAll: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val vibEnabled by remember(context) { context.RFIDStore.data.map { it[VIB_KEY] ?: true } }.collectAsState(initial = true)
    val soundEnabled by remember(context) { context.RFIDStore.data.map { it[SOUND_KEY] ?: true } }.collectAsState(initial = true)
    val autoVerifyEnabled by remember(context) { context.RFIDStore.data.map { it[AUTO_VERIFY_KEY] ?: true } }.collectAsState(initial = true)

    var tempCmd by remember(cmdTypesStr) { mutableStateOf(cmdTypesStr) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text(tr("s_clr_title", isKor), fontWeight = FontWeight.Bold) },
            text = { Text(tr("s_clr_desc", isKor)) },
            confirmButton = {
                TextButton(onClick = {
                    onResetAll()
                    showClearConfirmDialog = false
                }) { Text(tr("m_clr", isKor), color = Color.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirmDialog = false }) { Text(tr("d_cls", isKor)) } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(tr("s_title", isKor), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Text(tr("s_pref", isKor), color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        ListItem(
            headlineContent = { Text(tr("s_lang", isKor)) },
            supportingContent = { Text(tr("s_lang_d", isKor)) },
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { coroutineScope.launch { context.RFIDStore.edit { it[IS_KOR_KEY] = false } } },
                        colors = ButtonDefaults.buttonColors(containerColor = if (!isKor) Color(0xFF1976D2) else Color.LightGray),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) { Text("ENG", fontWeight = FontWeight.Bold, color = if (!isKor) Color.White else Color.DarkGray) }
                    Button(
                        onClick = { coroutineScope.launch { context.RFIDStore.edit { it[IS_KOR_KEY] = true } } },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isKor) Color(0xFF1976D2) else Color.LightGray),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) { Text("KOR", fontWeight = FontWeight.Bold, color = if (isKor) Color.White else Color.DarkGray) }
                }
            }
        )
        ListItem(
            headlineContent = { Text(tr("s_cmd", isKor)) },
            supportingContent = { Text(tr("s_cmd_d", isKor)) },
            trailingContent = {
                BasicTextField(
                    value = tempCmd,
                    onValueChange = { input ->
                        val f = input.filter { it.isLetter() }.uppercase()
                        tempCmd = f
                        coroutineScope.launch { context.RFIDStore.edit { it[CMD_TYPES_KEY] = f.ifEmpty { "T" } } }
                    },
                    modifier = Modifier.width(80.dp).background(Color(0xFFEEEEEE), RoundedCornerShape(4.dp)).padding(8.dp),
                    singleLine = true, textStyle = TextStyle(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                )
            }
        )
        ListItem(
            headlineContent = { Text(tr("s_vib", isKor)) },
            supportingContent = { Text(tr("s_vib_d", isKor)) },
            trailingContent = { Switch(checked = vibEnabled, onCheckedChange = { coroutineScope.launch { context.RFIDStore.edit { prefs -> prefs[VIB_KEY] = it } } }) }
        )
        ListItem(
            headlineContent = { Text(tr("s_snd", isKor)) },
            supportingContent = { Text(tr("s_snd_d", isKor)) },
            trailingContent = { Switch(checked = soundEnabled, onCheckedChange = { coroutineScope.launch { context.RFIDStore.edit { prefs -> prefs[SOUND_KEY] = it } } }) }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(tr("s_nfc", isKor), color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        ListItem(
            headlineContent = { Text(tr("s_verify", isKor)) },
            supportingContent = { Text(tr("s_verify_d", isKor)) },
            trailingContent = { Switch(checked = autoVerifyEnabled, onCheckedChange = { coroutineScope.launch { context.RFIDStore.edit { prefs -> prefs[AUTO_VERIFY_KEY] = it } } }) }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(tr("s_data", isKor), color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        ListItem(
            headlineContent = { Text(tr("s_clear", isKor)) },
            supportingContent = { Text(tr("s_clear_d", isKor)) },
            trailingContent = { Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color.Red) },
            modifier = Modifier.clickable { showClearConfirmDialog = true }
        )
    }
}

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var currentTagText = mutableStateOf("READY")
    private var tagStatus = mutableStateOf(TagStatus.IDLE)
    private var targetWriteCode = mutableStateOf("")
    private var isNfcEnabled = mutableStateOf(false)
    private var isContinuousMode = mutableStateOf(false)

    private var isVibEnabled = mutableStateOf(true)
    private var isSoundEnabled = mutableStateOf(true)
    private var isAutoVerifyEnabled = mutableStateOf(true)
    private var isKor = mutableStateOf(true)
    private var cmdTypesStr = mutableStateOf("TIOBD")

    private val historyList = mutableStateListOf<String>()
    private val HISTORY_KEY = stringPreferencesKey("history_data")

    private val nfcStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) {
                isNfcEnabled.value = (intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF) == NfcAdapter.STATE_ON)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        val intent = Intent(this, javaClass).apply { addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val savedHistory = RFIDStore.data.map { it[HISTORY_KEY] ?: "" }.first()
                if (savedHistory.isNotEmpty()) historyList.addAll(savedHistory.split("|"))
                addHistoryEntry("[SYSTEM] BOOT")

                RFIDStore.data.collect { prefs ->
                    isVibEnabled.value = prefs[VIB_KEY] ?: true
                    isSoundEnabled.value = prefs[SOUND_KEY] ?: true
                    isAutoVerifyEnabled.value = prefs[AUTO_VERIFY_KEY] ?: true
                    isKor.value = prefs[IS_KOR_KEY] ?: true
                    cmdTypesStr.value = prefs[CMD_TYPES_KEY] ?: "TIOBD"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val onClearHistory: () -> Unit = {
            historyList.clear()
            CoroutineScope(Dispatchers.IO).launch { RFIDStore.edit { it[HISTORY_KEY] = "" } }
        }

        val onResetAll: () -> Unit = {
            historyList.clear()
            CoroutineScope(Dispatchers.IO).launch {
                RFIDStore.edit { it.clear() }
                dataStore.edit { it.clear() }
            }
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainApp(
                        currentTag = currentTagText.value, tagStatus = tagStatus.value, targetCode = targetWriteCode.value,
                        historyList = historyList, isNfcEnabled = isNfcEnabled.value, isContinuousMode = isContinuousMode.value,
                        isKor = isKor.value, cmdTypesStr = cmdTypesStr.value,
                        onWriteRequested = { code -> targetWriteCode.value = code; tagStatus.value = TagStatus.WRITING },
                        onContinuousToggled = { enabled ->
                            isContinuousMode.value = enabled
                            if (!enabled) { tagStatus.value = TagStatus.IDLE; currentTagText.value = tr("m_wait", isKor.value) }
                        },
                        onRevertToWriting = { tagStatus.value = TagStatus.WRITING },
                        onTimeout = {
                            addHistoryEntry("[${tr("e_time", isKor.value)}] ${targetWriteCode.value}")
                            currentTagText.value = tr("e_time", isKor.value)
                            tagStatus.value = TagStatus.ERROR
                            targetWriteCode.value = ""
                            playFeedback(false)
                        },
                        onClearHistory = onClearHistory,
                        onResetAll = onResetAll
                    )
                }
            }
        }
    }

    private fun addHistoryEntry(entry: String) {
        historyList.add(0, "[${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}] $entry")
        if (historyList.size > 100) historyList.removeAt(historyList.lastIndex)
        CoroutineScope(Dispatchers.IO).launch { RFIDStore.edit { it[HISTORY_KEY] = historyList.joinToString("|") } }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(nfcStateReceiver, IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED))
        nfcAdapter?.let {
            isNfcEnabled.value = it.isEnabled
            if (!it.isEnabled) Toast.makeText(this, tr("t_ndis", isKor.value), Toast.LENGTH_LONG).show()
            it.enableForegroundDispatch(this, pendingIntent, null, null)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(nfcStateReceiver)
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag: Tag? = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        tag?.let {
            if (tagStatus.value == TagStatus.WRITING || isContinuousMode.value) writeAndVerifyTag(it, targetWriteCode.value) else readTag(it)
        }
    }

    private fun readTag(tag: Tag) {
        val nfcV = NfcV.get(tag) ?: return
        try {
            nfcV.connect()
            val cmd = ByteArray(11).apply {
                this[0] = 0x22; this[1] = 0x20
                System.arraycopy(tag.id, 0, this, 2, 8)
                this[10] = 0x00
            }
            val response = nfcV.transceive(cmd)
            if (response != null && response[0].toInt() == 0) {
                val textData = String(response.copyOfRange(1, response.size), Charsets.US_ASCII).replace(Regex("[^A-Za-z0-9]"), "").trim()
                currentTagText.value = textData
                tagStatus.value = TagStatus.READ_SUCCESS
                addHistoryEntry("[READ] Data : $textData")
                playFeedback(true)
            } else {
                tagStatus.value = TagStatus.ERROR
                playFeedback(false)
            }
        } catch (e: Exception) {
            currentTagText.value = tr("e_comm", isKor.value)
            tagStatus.value = TagStatus.ERROR
            playFeedback(false)
        } finally { try { nfcV.close() } catch (_: Exception) {} }
    }

    private fun writeAndVerifyTag(tag: Tag, data: String) {
        val nfcV = NfcV.get(tag) ?: return
        try {
            nfcV.connect()
            val blockData = data.toByteArray(Charsets.US_ASCII).let { b -> ByteArray(4) { i -> if (i < b.size) b[i] else 0x20.toByte() } }
            val cmd = ByteArray(15).apply {
                this[0] = 0x22; this[1] = 0x21
                System.arraycopy(tag.id, 0, this, 2, 8)
                this[10] = 0x00
                System.arraycopy(blockData, 0, this, 11, 4)
            }
            val response = nfcV.transceive(cmd)

            if (response != null && response[0].toInt() == 0) {
                if (isAutoVerifyEnabled.value) {
                    // Auto-Verify 옵션이 켜져있을 경우 다시 읽어와서 검증
                    val readCmd = ByteArray(11).apply {
                        this[0] = 0x22; this[1] = 0x20
                        System.arraycopy(tag.id, 0, this, 2, 8)
                        this[10] = 0x00
                    }
                    val readRes = nfcV.transceive(readCmd)
                    if (readRes != null && readRes[0].toInt() == 0) {
                        val readData = String(readRes.copyOfRange(1, readRes.size), Charsets.US_ASCII).replace(Regex("[^A-Za-z0-9]"), "").trim()
                        if (readData == data) {
                            currentTagText.value = data
                            tagStatus.value = TagStatus.WRITE_SUCCESS
                            addHistoryEntry("[WRITE+VERIFY OK] : $data")
                            playFeedback(true)
                        } else {
                            currentTagText.value = "VERIFY ERR"
                            tagStatus.value = TagStatus.ERROR
                            addHistoryEntry("[VERIFY ERR] : $data != $readData")
                            playFeedback(false)
                        }
                    } else {
                        currentTagText.value = "VERIFY ERR"
                        tagStatus.value = TagStatus.ERROR
                        playFeedback(false)
                    }
                } else {
                    currentTagText.value = data
                    tagStatus.value = TagStatus.WRITE_SUCCESS
                    addHistoryEntry("[WRITE OK] : $data")
                    playFeedback(true)
                }
            } else {
                tagStatus.value = TagStatus.ERROR
                playFeedback(false)
            }
        } catch (e: Exception) {
            currentTagText.value = tr("m_err", isKor.value)
            tagStatus.value = TagStatus.ERROR
            playFeedback(false)
        } finally { try { nfcV.close() } catch (_: Exception) {} }
    }

    private fun playFeedback(isSuccess: Boolean) {
        if (isVibEnabled.value) {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            val duration = if (isSuccess) 100L else 1000L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") vibrator.vibrate(duration)
        }
        if (isSoundEnabled.value) {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100).startTone(if (isSuccess) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_CDMA_ABBR_ALERT, 200)
        }
    }
}

// --- 중복 코드 제거를 위한 재사용 컴포저블 ---
@Composable
fun StyledBasicTextField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, width: Dp, coroutineScope: CoroutineScope, keyboardType: KeyboardType = KeyboardType.NumberPassword) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.width(width).height(90.dp).onFocusChanged { focusState ->
            if (focusState.isFocused) {
                coroutineScope.launch { delay(100); onValueChange(value.copy(selection = TextRange(0, value.text.length))) }
            }
        },
        textStyle = MONO_STYLE.copy(fontSize = 28.sp, textAlign = TextAlign.Center),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        cursorBrush = SolidColor(Color.Black),
        decorationBox = { innerTextField -> Box(modifier = Modifier.fillMaxSize().border(1.dp, Color.Gray, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) { innerTextField() } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AGVControlScreen(
    currentTag: String, tagStatus: TagStatus, targetCode: String, historyList: List<String>,
    isNfcEnabled: Boolean, isContinuousMode: Boolean, isKor: Boolean, cmdTypes: List<String>,
    onWriteRequested: (String) -> Unit, onContinuousToggled: (Boolean) -> Unit,
    onRevertToWriting: () -> Unit, onTimeout: () -> Unit, onClearHistory: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var part1 by remember { mutableStateOf(TextFieldValue("0")) }
    var part2 by remember { mutableStateOf("T") }
    var part3 by remember { mutableStateOf(TextFieldValue("00")) }
    var isAlphabetMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(cmdTypes) {
        if (part2 !in cmdTypes && cmdTypes.isNotEmpty()) {
            part2 = cmdTypes.first()
        }
    }

    val presetKeys = remember { (1..5).map { stringPreferencesKey("preset_$it") } }
    val presets by remember(context) { context.dataStore.data.map { prefs -> presetKeys.map { prefs[it] ?: "0T00" } } }.collectAsState(initial = listOf("0T01", "0T04", "0T07", "0T21", "0T22"))

    val currentFullCode = "${part1.text}$part2${part3.text.padStart(2, '0')}"
    var showHelpDialog by remember { mutableStateOf(false) }
    var showNfcDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isNfcEnabled) { showNfcDialog = !isNfcEnabled }
    LaunchedEffect(currentFullCode) { if (isContinuousMode) onWriteRequested(currentFullCode) }
    LaunchedEffect(tagStatus, isContinuousMode) {
        if (isContinuousMode) {
            if (tagStatus == TagStatus.WRITE_SUCCESS || tagStatus == TagStatus.ERROR) { delay(1000); if (isContinuousMode) onRevertToWriting() }
        } else {
            if (tagStatus == TagStatus.WRITING) { delay(5000); if (tagStatus == TagStatus.WRITING) onTimeout() }
        }
    }

    fun updateInputParts(code: String) {
        if (code.length >= 4) {
            part1 = TextFieldValue(code[0].toString()); part2 = code[1].toString(); part3 = TextFieldValue(code.substring(2, 4))
            focusManager.clearFocus()
        }
    }

    val cardBgColor = when {
        isContinuousMode && tagStatus == TagStatus.WRITE_SUCCESS -> Color(0xFFBBDEFB)
        isContinuousMode && tagStatus == TagStatus.ERROR -> Color(0xFFFFCDD2)
        isContinuousMode -> Color(0xFFFFCC80)
        tagStatus == TagStatus.WRITING -> Color(0xFFFFEB3B)
        tagStatus == TagStatus.READ_SUCCESS -> Color(0xFFC8E6C9)
        tagStatus == TagStatus.WRITE_SUCCESS -> Color(0xFFBBDEFB)
        tagStatus == TagStatus.ERROR -> Color(0xFFFFCDD2)
        else -> Color(0xFFE3F2FD)
    }

    val displayText = when {
        isContinuousMode && tagStatus == TagStatus.WRITE_SUCCESS -> "${tr("m_succ", isKor)}\n($currentTag)"
        isContinuousMode && tagStatus == TagStatus.ERROR -> "${tr("m_err", isKor)}\n($currentTag)"
        isContinuousMode -> "${tr("m_c_wait", isKor)}\n($targetCode)"
        tagStatus == TagStatus.WRITING -> "${tr("m_wait", isKor)}\n($targetCode)"
        else -> currentTag
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text(tr("d_clr_title", isKor), fontWeight = FontWeight.Bold) },
            text = { Text(tr("d_clr_desc", isKor)) },
            confirmButton = {
                TextButton(onClick = {
                    onClearHistory()
                    showClearConfirmDialog = false
                }) { Text(tr("m_clr", isKor), color = Color.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirmDialog = false }) { Text(tr("d_cls", isKor)) } }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text(tr("g_guide", isKor), fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item { Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) { Text(tr("g_tag", isKor), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(tr("g_func", isKor), fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f)) }; HorizontalDivider() }
                    items(getCommands(isKor)) { (tag, desc) ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { updateInputParts(tag); showHelpDialog = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(tag, style = MONO_STYLE, fontSize = 28.sp, modifier = Modifier.weight(1f))
                            Text(desc, fontSize = 28.sp, modifier = Modifier.weight(2f), lineHeight = 32.sp)
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHelpDialog = false }) { Text(tr("d_cls", isKor)) } }
        )
    }

    if (showHistoryDialog) {
        Dialog(onDismissRequest = { showHistoryDialog = false }) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("l_title", isKor), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(text = tr("l_call", isKor), color = Color.Red, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { showClearConfirmDialog = true })
                    }
                    if (historyList.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tr("l_empty", isKor), color = Color.Gray) }
                    else LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(historyList) { log ->
                            val isClickable = log.contains("OK") || log.contains("Data") || log.contains("TIMEOUT") || log.contains("초과") || log.contains("VERIFY")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable(enabled = isClickable) {
                                        val ex = log.split(" ").lastOrNull()?.trim() ?: ""
                                        if (ex.length >= 4) {
                                            val code = ex.takeLast(4)
                                            if (code[1].toString() in cmdTypes) {
                                                updateInputParts(code)
                                                showHistoryDialog = false
                                            }
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                            ) { Text(text = log, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.padding(12.dp), color = if (log.contains("ERROR") || log.contains("TIMEOUT") || log.contains("오류") || log.contains("초과") || log.contains("VERIFY ERR")) Color.Red else Color.DarkGray) }
                        }
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp, vertical = 40.dp).pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tr("m_title", isKor), fontWeight = FontWeight.Bold, color = Color.Gray)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = if (isNfcEnabled) tr("m_on", isKor) else tr("m_off", isKor), fontWeight = FontWeight.Bold, color = if (isNfcEnabled) Color(0xFF388E3C) else Color.Red, style = MONO_STYLE, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Default.HelpOutline, contentDescription = "Help", tint = Color.Gray, modifier = Modifier.size(24.dp).clickable { showHelpDialog = true })
            }
        }

        Card(modifier = Modifier.fillMaxWidth().height(270.dp).clickable(enabled = tagStatus == TagStatus.READ_SUCCESS || tagStatus == TagStatus.WRITE_SUCCESS) { updateInputParts(currentTag) }, colors = CardDefaults.cardColors(containerColor = cardBgColor)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { Text(text = displayText, fontSize = if (displayText.contains("\n")) 40.sp else 80.sp, style = MONO_STYLE, textAlign = TextAlign.Center, lineHeight = if (displayText.contains("\n")) 48.sp else 80.sp) }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            StyledBasicTextField(
                value = part1,
                onValueChange = { input ->
                    val f = input.text.filter { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }.uppercase()
                    part1 = input.copy(text = if (f.isNotEmpty()) f.last().toString() else "", selection = TextRange(if (f.isNotEmpty()) 1 else 0))
                },
                width = 64.dp, coroutineScope = coroutineScope, keyboardType = KeyboardType.Text
            )
            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .width(72.dp).height(90.dp).background(Color(0xFFF5F5F5), RoundedCornerShape(4.dp))
                    .clickable {
                        focusManager.clearFocus()
                        coroutineScope.launch { delay(50); isAlphabetMenuExpanded = true }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = part2, style = MONO_STYLE, fontSize = 32.sp, color = Color(0xFF1976D2))
                DropdownMenu(expanded = isAlphabetMenuExpanded, onDismissRequest = { isAlphabetMenuExpanded = false }) {
                    cmdTypes.forEach { option -> DropdownMenuItem(text = { Text(option, style = MONO_STYLE, fontSize = 24.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }, onClick = { part2 = option; isAlphabetMenuExpanded = false }) }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StyledBasicTextField(value = part3, onValueChange = { input -> val f = input.text.filter { it.isDigit() }; if (f.length <= 2) part3 = input.copy(text = f) }, width = 80.dp, coroutineScope = coroutineScope)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.height(90.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = { part3 = part3.copy(text = ((part3.text.toIntOrNull() ?: 0) + 1).coerceIn(0, 99).toString().padStart(2, '0')) }, modifier = Modifier.size(32.dp).background(Color(0xFFE0E0E0), RoundedCornerShape(4.dp))) { Icon(Icons.Default.Add, contentDescription = "Up", tint = Color.Black) }
                    IconButton(onClick = { part3 = part3.copy(text = ((part3.text.toIntOrNull() ?: 0) - 1).coerceIn(0, 99).toString().padStart(2, '0')) }, modifier = Modifier.size(32.dp).background(Color(0xFFE0E0E0), RoundedCornerShape(4.dp))) { Icon(Icons.Default.Remove, contentDescription = "Down", tint = Color.Black) }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Surface(modifier = Modifier.fillMaxWidth().height(90.dp).combinedClickable(onClick = { if (isContinuousMode) onContinuousToggled(false) else onWriteRequested(currentFullCode) }, onLongClick = { if (!isContinuousMode) { onContinuousToggled(true); onWriteRequested(currentFullCode) } }), shape = RoundedCornerShape(8.dp), color = if (isContinuousMode) Color(0xFFE64A19) else MaterialTheme.colorScheme.primary) {
            Box(contentAlignment = Alignment.Center) { Text(text = if (isContinuousMode) tr("m_stop", isKor) else tr("m_write", isKor), color = Color.White, fontSize = if (isContinuousMode) 24.sp else 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
        }
        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            presets.forEachIndexed { index, code ->
                Box(modifier = Modifier.weight(1f).height(70.dp).padding(2.dp).background(Color(0xFFEEEEEE), RoundedCornerShape(4.dp)).pointerInput(code) { detectTapGestures(onTap = { updateInputParts(code) }, onLongPress = { if (currentFullCode.length == 4) { coroutineScope.launch { context.dataStore.edit { it[presetKeys[index]] = currentFullCode } }; Toast.makeText(context, "${tr("t_psav", isKor)} $currentFullCode", Toast.LENGTH_SHORT).show() } }) }, contentAlignment = Alignment.Center) { Text(code, style = MONO_STYLE) }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(tr("m_hist", isKor), fontWeight = FontWeight.Bold, color = Color.Blue, modifier = Modifier.clickable { showHistoryDialog = true })
            Text(text = tr("m_clr", isKor), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Red, modifier = Modifier.clickable { showClearConfirmDialog = true })
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(historyList) { item ->
                val isClickable = item.contains("OK") || item.contains("Data") || item.contains("TIMEOUT") || item.contains("초과") || item.contains("VERIFY")
                Text(text = item, fontSize = 12.sp, style = MONO_STYLE, modifier = Modifier.fillMaxWidth().clickable(enabled = isClickable) { val ex = item.split(" ").lastOrNull()?.trim() ?: ""; if (ex.length >= 4) { val code = ex.takeLast(4); if (code[1].toString() in cmdTypes) updateInputParts(code) } }.padding(vertical = 4.dp), color = if (item.contains("ERROR") || item.contains("TIMEOUT") || item.contains("오류") || item.contains("초과") || item.contains("VERIFY ERR")) Color.Red else Color.DarkGray)
            }
        }
    }

    if (showNfcDialog) {
        AlertDialog(
            onDismissRequest = { showNfcDialog = false }, title = { Text(tr("d_noff", isKor), fontWeight = FontWeight.Bold) }, text = { Text(tr("d_ndis", isKor)) },
            confirmButton = { TextButton(onClick = { showNfcDialog = false; context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) { Text(tr("d_set", isKor)) } },
            dismissButton = { TextButton(onClick = { showNfcDialog = false }) { Text(tr("d_cls", isKor)) } }
        )
    }
}