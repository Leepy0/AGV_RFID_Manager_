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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
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
val AUTO_VERIFY_KEY = booleanPreferencesKey("auto_verify")

enum class TagStatus { IDLE, WRITING, READ_SUCCESS, WRITE_SUCCESS, ERROR }

@Composable
fun MainApp(
    currentTag: String,
    tagStatus: TagStatus,
    targetCode: String,
    historyList: MutableList<String>,
    isNfcEnabled: Boolean,
    isContinuousMode: Boolean,
    onWriteRequested: (String) -> Unit,
    onContinuousToggled: (Boolean) -> Unit,
    onRevertToWriting: () -> Unit,
    onTimeout: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(icon = { Icon(Icons.Default.Nfc, null) }, label = { Text("NFC MAIN") }, selected = selectedTab == 0, onClick = { selectedTab = 0 })
                NavigationBarItem(icon = { Icon(Icons.Default.MenuBook, null) }, label = { Text("Guide") }, selected = selectedTab == 1, onClick = { selectedTab = 1 })
                NavigationBarItem(icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") }, selected = selectedTab == 2, onClick = { selectedTab = 2 })
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> AGVControlScreen(currentTag, tagStatus, targetCode, historyList, isNfcEnabled, isContinuousMode, onWriteRequested, onContinuousToggled, onRevertToWriting, onTimeout)
                1 -> GuideScreen()
                2 -> AppSettingsScreen(historyList)
            }
        }
    }
}

@Composable
fun GuideScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("How to Use App", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            GuideCard(Icons.Default.Nfc, "1. READ", "Bring phone close to the TAG.\n(Auto Read)")
        }
        item {
            GuideCard(Icons.Default.Edit, "2. WRITE", "1) Enter Code\n2) Press [WRITE]\n3) Bring phone to TAG")
        }
        item {
            GuideCard(Icons.Default.AllInclusive, "3. CONTINUOUS", "1) Long Press [WRITE]\n2) Bring phone to many TAGs\n3) Press [STOP] to finish")
        }
        item {
            GuideCard(Icons.Default.Save, "4. PRESET (Memory)", "• Long Press: Save current code\n• Short Press: Load saved code")
        }
        item {
            GuideCard(Icons.Default.UnfoldMore, "5. ADJUST", "Use [+] and [-] buttons to easily change numbers.")
        }
        item {
            GuideCard(Icons.Default.History, "6. HISTORY", "Tap the 'HISTORY' text to view and clear all operation logs.")
        }
    }
}

@Composable
fun GuideCard(icon: ImageVector, title: String, desc: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = title, modifier = Modifier.size(48.dp), tint = Color(0xFF1976D2))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(desc, fontSize = 14.sp, color = Color.DarkGray)
            }
        }
    }
}

@Composable
fun AppSettingsScreen(historyList: MutableList<String>) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val vibEnabled by remember(context) { context.RFIDStore.data.map { it[VIB_KEY] ?: true } }.collectAsState(initial = true)
    val autoVerifyEnabled by remember(context) { context.RFIDStore.data.map { it[AUTO_VERIFY_KEY] ?: true } }.collectAsState(initial = true)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("App Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Text("Preferences", color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        ListItem(
            headlineContent = { Text("Vibration Feedback") },
            supportingContent = { Text("Long vibration on error, short on success") },
            trailingContent = { Switch(checked = vibEnabled, onCheckedChange = { coroutineScope.launch { context.RFIDStore.edit { prefs -> prefs[VIB_KEY] = it } } }) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("NFC Operations", color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        ListItem(
            headlineContent = { Text("Auto-Verify Write") },
            supportingContent = { Text("Read tag automatically after writing") },
            trailingContent = { Switch(checked = autoVerifyEnabled, onCheckedChange = { coroutineScope.launch { context.RFIDStore.edit { prefs -> prefs[AUTO_VERIFY_KEY] = it } } }) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Data Management", color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        ListItem(
            headlineContent = { Text("Clear All History") },
            supportingContent = { Text("Delete all operation logs permanently") },
            trailingContent = { Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color.Red) },
            modifier = Modifier.clickable { historyList.clear() }
        )
        ListItem(
            headlineContent = { Text("App Version") },
            trailingContent = { Text("v1.5.0", color = Color.Gray) }
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

    // 설정 상태 변수
    private var isVibEnabled = mutableStateOf(true)

    private val historyList = mutableStateListOf<String>()
    private val HISTORY_KEY = stringPreferencesKey("history_data")

    private val nfcStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) {
                val state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF)
                isNfcEnabled.value = (state == NfcAdapter.STATE_ON)
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
            val savedHistory = RFIDStore.data.map { it[HISTORY_KEY] ?: "" }.first()
            if (savedHistory.isNotEmpty()) {
                historyList.addAll(savedHistory.split("|"))
            }
            addHistoryEntry("[SYSTEM] BOOT")

            // 설정 값 실시간 관찰
            RFIDStore.data.collect { prefs ->
                isVibEnabled.value = prefs[VIB_KEY] ?: true
            }
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainApp(
                        currentTag = currentTagText.value,
                        tagStatus = tagStatus.value,
                        targetCode = targetWriteCode.value,
                        historyList = historyList,
                        isNfcEnabled = isNfcEnabled.value,
                        isContinuousMode = isContinuousMode.value,
                        onWriteRequested = { code ->
                            targetWriteCode.value = code
                            tagStatus.value = TagStatus.WRITING
                        },
                        onContinuousToggled = { enabled ->
                            isContinuousMode.value = enabled
                            if (!enabled) {
                                tagStatus.value = TagStatus.IDLE
                                currentTagText.value = "READY"
                            }
                        },
                        onRevertToWriting = {
                            tagStatus.value = TagStatus.WRITING
                        },
                        onTimeout = {
                            addHistoryEntry("[TIMEOUT] ${targetWriteCode.value}")
                            currentTagText.value = "TIMEOUT"
                            tagStatus.value = TagStatus.ERROR
                            targetWriteCode.value = ""
                            playFeedback(false)
                        }
                    )
                }
            }
        }
    }

    private fun addHistoryEntry(entry: String) {
        val timeEntry = "[${getCurrentTime()}] $entry"
        historyList.add(0, timeEntry)
        if (historyList.size > 100) historyList.removeAt(historyList.lastIndex)

        CoroutineScope(Dispatchers.IO).launch {
            RFIDStore.edit { prefs ->
                prefs[HISTORY_KEY] = historyList.joinToString("|")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
        registerReceiver(nfcStateReceiver, filter)

        nfcAdapter?.let {
            isNfcEnabled.value = it.isEnabled
            if (!it.isEnabled) Toast.makeText(this, "NFC DISABLED", Toast.LENGTH_LONG).show()
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
            // 연속 모드이거나 대기 상태일 때 모두 Write 수행
            if (tagStatus.value == TagStatus.WRITING || isContinuousMode.value) {
                writeAndVerifyTag(it, targetWriteCode.value)
            } else {
                readTag(it)
            }
        }
    }

    private fun getCurrentTime() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    private fun readTag(tag: Tag) {
        val nfcV = NfcV.get(tag) ?: return
        try {
            nfcV.connect()
            val uid = tag.id
            val cmd = ByteArray(11).apply {
                this[0] = 0x22.toByte()
                this[1] = 0x20.toByte()
                System.arraycopy(uid, 0, this, 2, 8)
                this[10] = 0x00.toByte()
            }
            val response = nfcV.transceive(cmd)
            if (response != null && response[0].toInt() == 0) {
                val payload = response.copyOfRange(1, response.size)
                val textData = String(payload, Charsets.US_ASCII).replace(Regex("[^A-Za-z0-9]"), "").trim()
                currentTagText.value = textData
                tagStatus.value = TagStatus.READ_SUCCESS
                addHistoryEntry("[READ] Data : $textData")
                playFeedback(true)
            } else {
                tagStatus.value = TagStatus.ERROR
                playFeedback(false)
            }
        } catch (e: Exception) {
            currentTagText.value = "COMM ERR"
            tagStatus.value = TagStatus.ERROR
            playFeedback(false)
        } finally {
            try { nfcV.close() } catch (_: Exception) {}
        }
    }

    private fun writeAndVerifyTag(tag: Tag, data: String) {
        val nfcV = NfcV.get(tag) ?: return
        try {
            nfcV.connect()
            val uid = tag.id
            val blockData = data.toByteArray(Charsets.US_ASCII).let { b ->
                ByteArray(4) { i -> if (i < b.size) b[i] else 0x20.toByte() }
            }

            val cmd = ByteArray(15).apply {
                this[0] = 0x22.toByte()
                this[1] = 0x21.toByte()
                System.arraycopy(uid, 0, this, 2, 8)
                this[10] = 0x00.toByte()
                System.arraycopy(blockData, 0, this, 11, 4)
            }

            val response = nfcV.transceive(cmd)
            if (response != null && response[0].toInt() == 0) {
                currentTagText.value = data
                tagStatus.value = TagStatus.WRITE_SUCCESS
                addHistoryEntry("[WRITE] OK : $data")
                playFeedback(true)
            } else {
                tagStatus.value = TagStatus.ERROR
                playFeedback(false)
            }
        } catch (e: Exception) {
            currentTagText.value = "ERR"
            tagStatus.value = TagStatus.ERROR
            playFeedback(false)
        } finally {
            try { nfcV.close() } catch (_: Exception) {}
        }
    }

    private fun playFeedback(isSuccess: Boolean) {
        if (isVibEnabled.value) {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            // 오류 시 1000ms 진동으로 사용자가 즉시 인지하도록 차별화
            val duration = if (isSuccess) 100L else 1000L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        }

        // 소리는 기본 재생
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100).startTone(
            if (isSuccess) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_CDMA_ABBR_ALERT, 200
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AGVControlScreen(
    currentTag: String,
    tagStatus: TagStatus,
    targetCode: String,
    historyList: MutableList<String>,
    isNfcEnabled: Boolean,
    isContinuousMode: Boolean,
    onWriteRequested: (String) -> Unit,
    onContinuousToggled: (Boolean) -> Unit,
    onRevertToWriting: () -> Unit,
    onTimeout: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var part1 by remember { mutableStateOf(TextFieldValue("0")) }
    var part2 by remember { mutableStateOf("T") }
    var part3 by remember { mutableStateOf(TextFieldValue("00")) }
    var isAlphabetMenuExpanded by remember { mutableStateOf(false) }
    val alphabetOptions = listOf("T", "I", "O", "B", "D")

    val presetKeys = remember { (1..5).map { stringPreferencesKey("preset_$it") } }
    val presets by remember(context) {
        context.dataStore.data.map { prefs ->
            presetKeys.map { prefs[it] ?: "0T00" }
        }
    }.collectAsState(initial = listOf("0T01", "0T04", "0T07", "0T21", "0T22"))

    val monoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    val currentFullCode = "${part1.text}$part2${part3.text.padStart(2, '0')}"
    var showHelpDialog by remember { mutableStateOf(false) }
    var showNfcDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isNfcEnabled) {
        showNfcDialog = !isNfcEnabled
    }

    LaunchedEffect(currentFullCode) {
        if (isContinuousMode) {
            onWriteRequested(currentFullCode)
        }
    }

    fun updateInputParts(code: String) {
        if (code.length >= 4) {
            part1 = TextFieldValue(code[0].toString())
            part2 = code[1].toString()
            part3 = TextFieldValue(code.substring(2, 4))
            focusManager.clearFocus()
        }
    }

    fun adjustPart3(delta: Int) {
        val current = part3.text.toIntOrNull() ?: 0
        val newValue = (current + delta).coerceIn(0, 99)
        val newText = newValue.toString().padStart(2, '0')
        part3 = part3.copy(text = newText, selection = TextRange(newText.length))
    }

    LaunchedEffect(tagStatus, isContinuousMode) {
        if (isContinuousMode) {
            // 연속 모드: 태그 처리 완료 후 잠시 대기했다가 다시 WRITING 상태로 전환
            if (tagStatus == TagStatus.WRITE_SUCCESS || tagStatus == TagStatus.ERROR) {
                delay(1000)
                if (isContinuousMode) onRevertToWriting()
            }
        } else {
            // 단일 쓰기 모드: 5초 Timeout 체크
            if (tagStatus == TagStatus.WRITING) {
                delay(5000)
                if (tagStatus == TagStatus.WRITING) onTimeout()
            }
        }
    }

    val cardBgColor = when {
        isContinuousMode && tagStatus == TagStatus.WRITE_SUCCESS -> Color(0xFFBBDEFB)
        isContinuousMode && tagStatus == TagStatus.ERROR -> Color(0xFFFFCDD2)
        isContinuousMode -> Color(0xFFFFCC80) // 주황색 (연속 모드 대기)
        tagStatus == TagStatus.WRITING -> Color(0xFFFFEB3B)
        tagStatus == TagStatus.READ_SUCCESS -> Color(0xFFC8E6C9)
        tagStatus == TagStatus.WRITE_SUCCESS -> Color(0xFFBBDEFB)
        tagStatus == TagStatus.ERROR -> Color(0xFFFFCDD2)
        else -> Color(0xFFE3F2FD) // IDLE
    }

    val displayText = when {
        isContinuousMode && tagStatus == TagStatus.WRITE_SUCCESS -> "SUCCESS\n($currentTag)"
        isContinuousMode && tagStatus == TagStatus.ERROR -> "ERROR\n($currentTag)"
        isContinuousMode -> "CONTINUOUS\nWAITING\n($targetCode)"
        tagStatus == TagStatus.WRITING -> "WAITING\n($targetCode)"
        else -> currentTag
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Command Guide", fontWeight = FontWeight.Bold) },
            text = {
                val commands = listOf(
                    "0T01" to "Stop", "0T02" to "Low Speed", "0T03" to "LLow Speed",
                    "0T04" to "Loading Flag", "0T05" to "Driving Speed", "0T07" to "Unloading Flag",
                    "0T08" to "Left Branch", "0T09" to "Right Branch", "0T21" to "CW 90 Turn",
                    "0T22" to "CCW 90 Turn", "0T23" to "CW 180 Turn", "0T24" to "CCW 180 Turn",
                    "0T25" to "CW 90 Turn, FB", "0T26" to "CCW 90 Turn, FB",
                    "0T27" to "OBS OFF", "0T28" to "OBS ON"
                )
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Text("TAG", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("FUNCTION", fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                        }
                        HorizontalDivider()
                    }
                    items(commands) { (tag, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    updateInputParts(tag)
                                    showHelpDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(tag, style = monoStyle, fontSize = 28.sp, modifier = Modifier.weight(1f))
                            Text(desc, fontSize = 28.sp, modifier = Modifier.weight(2f), lineHeight = 32.sp)
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) { Text("CLOSE") }
            }
        )
    }

    if (showHistoryDialog) {
        Dialog(onDismissRequest = { showHistoryDialog = false }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Operation Logs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = "CLEAR ALL",
                            color = Color.Red,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { historyList.clear() }
                        )
                    }

                    if (historyList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No logs available.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(historyList) { log ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                                ) {
                                    Text(
                                        text = log,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(12.dp),
                                        color = if (log.contains("ERROR") || log.contains("TIMEOUT")) Color.Red else Color.DarkGray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 32.dp, vertical = 40.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("AGV RFID Manager", fontWeight = FontWeight.Bold, color = Color.Gray)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isNfcEnabled) "🟢 NFC ON" else "🔴 NFC OFF",
                    fontWeight = FontWeight.Bold,
                    color = if (isNfcEnabled) Color(0xFF388E3C) else Color.Red,
                    style = monoStyle,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = "Help",
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp).clickable { showHelpDialog = true }
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().height(270.dp).clickable(enabled = tagStatus == TagStatus.READ_SUCCESS || tagStatus == TagStatus.WRITE_SUCCESS) {
                updateInputParts(currentTag)
            },
            colors = CardDefaults.cardColors(containerColor = cardBgColor)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = displayText,
                    fontSize = if (displayText.contains("CONTINUOUS")) 40.sp else 80.sp,
                    style = monoStyle,
                    textAlign = TextAlign.Center,
                    lineHeight = if (displayText.contains("CONTINUOUS")) 48.sp else 80.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = part1,
                onValueChange = { input ->
                    val filteredText = input.text.filter { it.isDigit() }
                    val newText = if (filteredText.isNotEmpty()) filteredText.last().toString() else ""
                    part1 = input.copy(text = newText, selection = TextRange(newText.length))
                },
                modifier = Modifier
                    .width(64.dp)
                    .height(90.dp)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            coroutineScope.launch {
                                delay(100)
                                part1 = part1.copy(selection = TextRange(0, part1.text.length))
                            }
                        }
                    },
                textStyle = monoStyle.copy(fontSize = 28.sp, textAlign = TextAlign.Center),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                cursorBrush = SolidColor(Color.Black),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxSize().border(1.dp, Color.Gray, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                        innerTextField()
                    }
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(90.dp)
                    .background(Color(0xFFF5F5F5), RoundedCornerShape(4.dp))
                    .pointerInput(part2) {
                        detectTapGestures(
                            onTap = {
                                focusManager.clearFocus()
                                isAlphabetMenuExpanded = true
                            },
                            onDoubleTap = {
                                if (part2 == "I") part2 = "O"
                                else if (part2 == "O") part2 = "I"
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = part2, style = monoStyle, fontSize = 32.sp, color = Color(0xFF1976D2))
                DropdownMenu(expanded = isAlphabetMenuExpanded, onDismissRequest = { isAlphabetMenuExpanded = false }) {
                    alphabetOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, style = monoStyle, fontSize = 24.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                            onClick = { part2 = option; isAlphabetMenuExpanded = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = part3,
                    onValueChange = { input ->
                        val filteredText = input.text.filter { it.isDigit() }
                        if (filteredText.length <= 2) part3 = input.copy(text = filteredText)
                    },
                    modifier = Modifier
                        .width(80.dp)
                        .height(90.dp)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                coroutineScope.launch {
                                    delay(100)
                                    part3 = part3.copy(selection = TextRange(0, part3.text.length))
                                }
                            }
                        },
                    textStyle = monoStyle.copy(fontSize = 28.sp, textAlign = TextAlign.Center),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    cursorBrush = SolidColor(Color.Black),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxSize().border(1.dp, Color.Gray, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                            innerTextField()
                        }
                    }
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.height(90.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(
                        onClick = { adjustPart3(1) },
                        modifier = Modifier.size(32.dp).background(Color(0xFFE0E0E0), RoundedCornerShape(4.dp))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Up", tint = Color.Black)
                    }
                    IconButton(
                        onClick = { adjustPart3(-1) },
                        modifier = Modifier.size(32.dp).background(Color(0xFFE0E0E0), RoundedCornerShape(4.dp))
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Down", tint = Color.Black)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .combinedClickable(
                    onClick = {
                        if (isContinuousMode) {
                            onContinuousToggled(false)
                        } else {
                            onWriteRequested(currentFullCode)
                        }
                    },
                    onLongClick = {
                        if (!isContinuousMode) {
                            onContinuousToggled(true)
                            onWriteRequested(currentFullCode)
                        }
                    }
                ),
            shape = RoundedCornerShape(8.dp),
            color = if (isContinuousMode) Color(0xFFE64A19) else MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (isContinuousMode) "STOP CONTINUOUS" else "WRITE\n(Long press for Continuous)",
                    color = Color.White,
                    fontSize = if (isContinuousMode) 24.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            presets.forEachIndexed { index, code ->
                Box(
                    modifier = Modifier.weight(1f).height(70.dp).padding(2.dp).background(Color(0xFFEEEEEE), RoundedCornerShape(4.dp)).pointerInput(code) {
                        detectTapGestures(
                            onTap = { updateInputParts(code) },
                            onLongPress = {
                                if (currentFullCode.length == 4) {
                                    coroutineScope.launch { context.dataStore.edit { it[presetKeys[index]] = currentFullCode } }
                                    Toast.makeText(context, "PRESET ${index + 1} SAVED: $currentFullCode", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }, contentAlignment = Alignment.Center
                ) { Text(code, style = monoStyle) }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("HISTORY", fontWeight = FontWeight.Bold, color = Color.Blue, modifier = Modifier.clickable { showHistoryDialog = true })
            Text(text = "CLEAR", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Red, modifier = Modifier.clickable { historyList.clear() })
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(historyList) { item ->
                val isClickable = item.contains("OK") || item.contains("Data") || item.contains("TIMEOUT")
                Text(
                    text = item,
                    fontSize = 12.sp,
                    style = monoStyle,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = isClickable) {
                        val extracted = item.split(" ").lastOrNull()?.trim() ?: ""
                        if (extracted.length >= 4) {
                            val code = extracted.takeLast(4)
                            if (code[1].toString() in alphabetOptions) updateInputParts(code)
                        }
                    }.padding(vertical = 4.dp),
                    color = if (item.contains("OK") || item.contains("Data")) Color(0xFF388E3C) else Color.Red
                )
            }
        }
    }

    if (showNfcDialog) {
        AlertDialog(
            onDismissRequest = { showNfcDialog = false },
            title = { Text("NFC OFF", fontWeight = FontWeight.Bold) },
            text = { Text("NFC is disabled.\nGo to settings to enable?") },
            confirmButton = {
                TextButton(onClick = {
                    showNfcDialog = false
                    context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                }) { Text("SETTINGS") }
            },
            dismissButton = {
                TextButton(onClick = { showNfcDialog = false }) { Text("CLOSE") }
            }
        )
    }
}