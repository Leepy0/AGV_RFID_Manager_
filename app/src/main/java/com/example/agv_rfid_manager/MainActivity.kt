package com.example.agv_rfid_manager

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.nfc.*
import android.nfc.tech.Ndef
import android.os.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

// [기능 4] 메모리 슬롯 및 이력 저장을 위한 DataStore
val Context.dataStore by preferencesDataStore(name = "agv_settings")

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null

    // UI 상태 관리
    private var currentTagText = mutableStateOf("READY")
    private var isWriteMode = mutableStateOf(false)
    private var targetWriteCode = mutableStateOf("")
    private var isNfcEnabled = mutableStateOf(false)

    // 최근 작업 기록 (History) 리스트
    private val historyList = mutableStateListOf<String>()
    private val HISTORY_KEY = stringPreferencesKey("history_data")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        val intent = Intent(this, javaClass).apply { addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)

        // 저장된 히스토리 불러오기
        CoroutineScope(Dispatchers.Main).launch {
            val savedHistory = dataStore.data.map { it[HISTORY_KEY] ?: "" }.first()
            if (savedHistory.isNotEmpty()) {
                historyList.addAll(savedHistory.split("|"))
            } else {
                addHistoryEntry("[SYSTEM] BOOT")
            }
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AGVControlScreen(
                        currentTag = currentTagText.value,
                        isWriting = isWriteMode.value,
                        targetCode = targetWriteCode.value,
                        historyList = historyList,
                        onWriteRequested = { code ->
                            targetWriteCode.value = code
                            isWriteMode.value = true
                        },
                        onTimeout = {
                            addHistoryEntry("[TIMEOUT] ${targetWriteCode.value}")
                            isWriteMode.value = false
                        },
                        isNfcEnabled = isNfcEnabled.value
                    )
                }
            }
        }
    }

    private fun addHistoryEntry(entry: String) {
        val timeEntry = "[${getCurrentTime()}] $entry"
        historyList.add(0, timeEntry)
        // 최대 50개까지만 저장
        if (historyList.size > 50) historyList.removeAt(historyList.lastIndex)
        
        CoroutineScope(Dispatchers.IO).launch {
            dataStore.edit { prefs ->
                prefs[HISTORY_KEY] = historyList.joinToString("|")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.let {
            isNfcEnabled.value = it.isEnabled // Update NFC status
            if (!it.isEnabled) Toast.makeText(this, "NFC OFF", Toast.LENGTH_LONG).show()
            it.enableForegroundDispatch(this, pendingIntent, null, null)
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        tag?.let {
            if (isWriteMode.value) writeAndVerifyTag(it, targetWriteCode.value)
            else readTag(it)
        }
    }

    private fun getCurrentTime() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    private fun readTag(tag: Tag) {
        val ndef = Ndef.get(tag) ?: return
        try {
            ndef.connect()
            val payload = ndef.ndefMessage?.records?.get(0)?.payload
            if (payload != null) {
                val langLen = payload[0].toInt() and 63
                val result = String(payload, langLen + 1, payload.size - langLen - 1, Charset.forName("UTF-8"))
                currentTagText.value = result
                addHistoryEntry("[READ] $result")
                playFeedback(true)
            }
            ndef.close()
        } catch (e: Exception) {
            currentTagText.value = "ERROR"
            playFeedback(false)
        }
    }

    private fun writeAndVerifyTag(tag: Tag, data: String) {
        val ndef = Ndef.get(tag)
        try {
            ndef?.let {
                it.connect()
                val textRecord = NdefRecord.createTextRecord("en", data)
                it.writeNdefMessage(NdefMessage(arrayOf(textRecord)))

                val verifyPayload = it.ndefMessage?.records?.get(0)?.payload
                var isVerified = false
                if (verifyPayload != null) {
                    val langLen = verifyPayload[0].toInt() and 63
                    val verifyResult = String(verifyPayload, langLen + 1, verifyPayload.size - langLen - 1, Charset.forName("UTF-8"))
                    isVerified = (verifyResult == data)
                }
                it.close()

                if (isVerified) {
                    currentTagText.value = "$data (OK)"
                    addHistoryEntry("[WRITE] $data")
                    playFeedback(true)
                } else {
                    Toast.makeText(this, "RETRY", Toast.LENGTH_SHORT).show()
                    addHistoryEntry("[VERIFY FAIL] $data")
                    playFeedback(false)
                }
                isWriteMode.value = false
            }
        } catch (e: Exception) {
            Toast.makeText(this, "FAILED", Toast.LENGTH_SHORT).show()
            addHistoryEntry("[WRITE ERROR] $data")
            playFeedback(false)
            isWriteMode.value = false
        }
    }

    private fun playFeedback(isSuccess: Boolean) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(if(isSuccess) 100 else 400, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(if(isSuccess) 100L else 400L)
        }
        val toneType = if(isSuccess) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_CDMA_ABBR_ALERT
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100).startTone(toneType, 200)
    }
}

@Composable
fun AGVControlScreen(
    currentTag: String,
    isWriting: Boolean,
    targetCode: String,
    historyList: List<String>,
    onWriteRequested: (String) -> Unit,
    onTimeout: () -> Unit,
    isNfcEnabled: Boolean
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var inputCode by remember { mutableStateOf("") }

    val presetKeys = remember { (1..5).map { stringPreferencesKey("preset_$it") } }
    val presetsFlow = remember(context) {
        context.dataStore.data.map { prefs ->
            presetKeys.map { key -> prefs[key] ?: "0T00" }
        }
    }

    val presets by presetsFlow.collectAsState(initial = listOf("0T01", "0T21", "0T31", "0T41", "0T51"))
    val regex = Regex("^[0-9][A-Z][0-9]{2}$")

    // 쓰기 모드 타임아웃 (10초)
    LaunchedEffect(isWriting) {
        if (isWriting) {
            delay(10000)
            onTimeout()
        }
    }
// AGVControlScreen Composable 내 Column Modifier 변경 및 Spacer 조정
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // 1. Status Card
        Card(modifier = Modifier.fillMaxWidth().height(100.dp), colors = CardDefaults.cardColors(containerColor = if(isWriting) Color(0xFFFFEB3B) else Color(0xFFE3F2FD))) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = if(isWriting) "WAITING\n($targetCode)" else currentTag,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 32.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp)) // 간격 유지

        // 2. CMD TextField
        OutlinedTextField(
            value = inputCode,
            onValueChange = { input ->
                val filtered = input.filter { it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' }
                if (filtered.length <= 4) inputCode = filtered.uppercase()
            },
            label = { Text("CMD", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            textStyle = LocalTextStyle.current.copy(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center),
            modifier = Modifier.width(160.dp).height(90.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
        )

        Spacer(modifier = Modifier.height(32.dp)) // PRESETS 섹션을 아래로 밀기 위해 간격 증가

        // 3. PRESETS Section
        Text("PRESETS", color = Color.Gray, fontSize = 12.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            presets.forEachIndexed { index, code ->
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp).background(Color.LightGray, shape = MaterialTheme.shapes.small)
                    .pointerInput(code) {
                        detectTapGestures(
                            onTap = { inputCode = code },
                            onLongPress = {
                                if (regex.matches(inputCode)) {
                                    coroutineScope.launch { context.dataStore.edit { prefs -> prefs[presetKeys[index]] = inputCode } }
                                    Toast.makeText(context, "SAVED", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }, contentAlignment = Alignment.Center
                ) { Text(code, fontSize = 14.sp) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp)) // HISTORY 섹션에 더 많은 공간을 주기 위해 간격 감소

        // 5. HISTORY Section
        Text("HISTORY", modifier = Modifier.align(Alignment.Start), fontWeight = FontWeight.Bold)
        LazyColumn(modifier = Modifier.fillMaxWidth().height(130.dp)) { // <-- 높이 조정
            items(historyList) { item ->
                val isClickable = (item.contains("[READ]") || item.contains("[WRITE]") || item.contains("[TIMEOUT]")) &&
                        !item.contains("FAIL") && !item.contains("ERROR")

                Text(
                    item,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isClickable) {
                            val extracted = item.substringAfterLast(" ")
                            if (extracted.length <= 4 && extracted.contains("T")) {
                                inputCode = extracted
                            }
                        }
                        .padding(vertical = 4.dp),
                    color = when {
                        item.contains("FAIL") || item.contains("ERROR") -> Color.Red
                        item.contains("[TIMEOUT]") -> Color.Red
                        item.contains("[WRITE]") -> Color(0xFF1976D2)
                        item.contains("[READ]") -> Color(0xFF388E3C)
                        else -> Color.Gray
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // 4. WRITE Button
        Button(
            onClick = {
                if (regex.matches(inputCode)) onWriteRequested(inputCode)
                else Toast.makeText(context, "ERROR", Toast.LENGTH_SHORT).show()
            },
            enabled = isNfcEnabled, // NFC 상태에 따라 활성화/비활성화
            modifier = Modifier.fillMaxWidth().height(112.dp)
        ) { Text("WRITE", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
    }
}
