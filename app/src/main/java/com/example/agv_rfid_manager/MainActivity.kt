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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.nio.charset.Charset

// [기능 4] 메모리 슬롯 영구 저장을 위한 DataStore 초기화
val Context.dataStore by preferencesDataStore(name = "agv_settings")

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null

    // UI 상태 관리
    private var currentTagText = mutableStateOf("Ready to Read")
    private var isWriteMode = mutableStateOf(false)
    private var targetWriteCode = mutableStateOf("")

    // [기능 6] 최근 작업 기록 (History) 리스트
    private val historyList = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        val intent = Intent(this, javaClass).apply { addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AGVControlScreen(
                        currentTag = currentTagText.value,
                        isWriting = isWriteMode.value,
                        historyList = historyList,
                        onWriteRequested = { code ->
                            targetWriteCode.value = code
                            isWriteMode.value = true
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
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

    private fun readTag(tag: Tag) {
        val ndef = Ndef.get(tag) ?: return
        try {
            ndef.connect()
            val payload = ndef.ndefMessage?.records?.get(0)?.payload
            if (payload != null) {
                val langLen = payload[0].toInt() and 63
                val result = String(payload, langLen + 1, payload.size - langLen - 1, Charset.forName("UTF-8"))
                currentTagText.value = result
                historyList.add(0, "[Read] 성공: $result")
                playFeedback(true) // 성공 피드백
            }
            ndef.close()
        } catch (e: Exception) {
            currentTagText.value = "Read Error"
            playFeedback(false) // 실패 피드백
        }
    }

    // [기능 2] 쓰기 직후 자동 검증 (Write & Verify) 로직
    private fun writeAndVerifyTag(tag: Tag, data: String) {
        val ndef = Ndef.get(tag)
        try {
            ndef?.let {
                it.connect()
                // 1. 태그에 쓰기
                val textRecord = NdefRecord.createTextRecord("en", data)
                it.writeNdefMessage(NdefMessage(arrayOf(textRecord)))

                // 2. 폰을 떼지 않은 상태에서 즉시 다시 읽어 검증
                val verifyPayload = it.ndefMessage?.records?.get(0)?.payload
                var isVerified = false
                if (verifyPayload != null) {
                    val langLen = verifyPayload[0].toInt() and 63
                    val verifyResult = String(verifyPayload, langLen + 1, verifyPayload.size - langLen - 1, Charset.forName("UTF-8"))
                    isVerified = (verifyResult == data)
                }
                it.close()

                // 검증 결과에 따른 처리
                if (isVerified) {
                    currentTagText.value = "$data (Verify ✔️)"
                    historyList.add(0, "[Write] 검증 성공: $data")
                    playFeedback(true)
                } else {
                    Toast.makeText(this, "검증 실패! 다시 대주세요.", Toast.LENGTH_SHORT).show()
                    playFeedback(false)
                }
                isWriteMode.value = false
            }
        } catch (e: Exception) {
            Toast.makeText(this, "쓰기 오류. 태그를 너무 빨리 뗐습니다.", Toast.LENGTH_SHORT).show()
            playFeedback(false)
            isWriteMode.value = false
        }
    }

    // [기능 5] 햅틱(진동) 및 비프음 발생
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

// [UI 컴포저블]
@Composable
fun AGVControlScreen(
    currentTag: String,
    isWriting: Boolean,
    historyList: List<String>,
    onWriteRequested: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var inputCode by remember { mutableStateOf("") }
    var showTutorial by remember { mutableStateOf(false) }

// [기능 4] DataStore 영구 메모리 불러오기 (remember를 사용하여 성능 최적화)
    val presetKeys = remember { (1..5).map { stringPreferencesKey("preset_$it") } }
    val presetsFlow = remember(context) {
        context.dataStore.data.map { prefs ->
            presetKeys.map { key -> prefs[key] ?: "0T00" }
        }
    }

    val presets by presetsFlow.collectAsState(initial = listOf("0T01", "0T21", "0T31", "0T41", "0T51"))

    // [기능 1] 정규식 검사기 (숫자1자리 + T + 숫자2자리 제한)
    val regex = Regex("^[0-9]T[0-9]{2}$")

    if (showTutorial) TutorialDialog(onDismiss = { showTutorial = false })

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onClick = { showTutorial = true }, modifier = Modifier.align(Alignment.End)) { Text("매뉴얼 시청") }

        Card(modifier = Modifier.fillMaxWidth().height(100.dp), colors = CardDefaults.cardColors(containerColor = if(isWriting) Color(0xFFFFEB3B) else Color(0xFFE3F2FD))) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(text = if(isWriting) "WAITING TAG..." else currentTag, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = inputCode,
            onValueChange = { if (it.length <= 4) inputCode = it.uppercase() },
            label = { Text("지령 입력 (예: 0T21)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                if (regex.matches(inputCode)) onWriteRequested(inputCode)
                else Toast.makeText(context, "형식 오류! 숫자+T+숫자2자리(예:1T01)를 입력하세요.", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(50.dp)
        ) { Text("태그에 쓰기 (WRITE)", fontSize = 18.sp) }

        Text("메모리 슬롯 (길게 누르면 현재 입력값이 영구 저장됩니다)", color = Color.Gray, fontSize = 12.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            presets.forEachIndexed { index, code ->
                Box(modifier = Modifier.weight(1f).padding(2.dp).background(Color.LightGray, shape = MaterialTheme.shapes.small)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { inputCode = code },
                            onLongPress = {
                                if (regex.matches(inputCode)) {
                                    coroutineScope.launch { context.dataStore.edit { prefs -> prefs[presetKeys[index]] = inputCode } }
                                    Toast.makeText(context, "슬롯 ${index+1} 저장 완료", Toast.LENGTH_SHORT).show()
                                } else Toast.makeText(context, "올바른 형식만 저장할 수 있습니다.", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }, contentAlignment = Alignment.Center
                ) { Text(code, fontSize = 14.sp, modifier = Modifier.padding(12.dp)) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("최근 작업 이력", modifier = Modifier.align(Alignment.Start), fontWeight = FontWeight.Bold)
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(historyList) { item -> Text(item, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp)) }
        }
    }
}

// [기능 6] 동영상 매뉴얼 팝업 (Media3 ExoPlayer)
@Composable
fun TutorialDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build().apply {
        // 실제 영상 구동 시 아래 주석 해제 후 res/raw 폴더에 영상(tutorial.mp4) 추가
        // setMediaItem(androidx.media3.common.MediaItem.fromUri("android.resource://${context.packageName}/raw/tutorial"))
        // prepare(); playWhenReady = true; repeatMode = ExoPlayer.REPEAT_MODE_ALL
    } }
    DisposableEffect(Unit) { onDispose { player.release() } }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().height(400.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("현장 작업 매뉴얼", fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                AndroidView(
                    factory = { PlayerView(it).apply { this.player = player; useController = false } },
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp).background(Color.Black)
                )
                Button(onClick = onDismiss, modifier = Modifier.padding(16.dp)) { Text("닫기") }
            }
        }
    }
}