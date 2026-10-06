package com.example.agv_rfid_manager

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.NfcV
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.TextFieldValue
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import com.example.agv_rfid_manager.data.Keys
import com.example.agv_rfid_manager.data.PerfSetting
import com.example.agv_rfid_manager.data.RFIDStore
import com.example.agv_rfid_manager.data.SettingsState
import com.example.agv_rfid_manager.data.TagActions
import com.example.agv_rfid_manager.data.TagState
import com.example.agv_rfid_manager.data.TagStatus
import com.example.agv_rfid_manager.data.ThemeMode
import com.example.agv_rfid_manager.data.dataStore
import com.example.agv_rfid_manager.data.tr
import com.example.agv_rfid_manager.device.CrashLogger
import com.example.agv_rfid_manager.device.PerfMode
import com.example.agv_rfid_manager.ui.MainApp
import com.example.agv_rfid_manager.ui.theme.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), TagActions {
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var toneGenerator: ToneGenerator? = null
    private var autoLow = false

    private val tagState = TagState()
    private val settings = SettingsState()

    private val isKor get() = settings.isKor

    private val nfcStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) {
                tagState.nfcEnabled = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF) == NfcAdapter.STATE_ON
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashLogger.install(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        autoLow = PerfMode.detectLowEnd(this)

        val intent = Intent(this, javaClass).apply { addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)

        lifecycleScope.launch {
            try {
                val prefs = RFIDStore.data.first()
                val saved = prefs[Keys.HISTORY] ?: ""
                if (saved.isNotEmpty()) tagState.history.addAll(saved.split("|"))
                addHistoryEntry("[SYSTEM] BOOT")
                tagState.part1 = TextFieldValue(prefs[Keys.PART1] ?: "0")
                tagState.part2 = prefs[Keys.PART2] ?: "T"
                tagState.part3 = TextFieldValue(prefs[Keys.PART3] ?: "00")

                RFIDStore.data.collect { p ->
                    settings.vib = p[Keys.VIB] ?: true
                    settings.sound = p[Keys.SOUND] ?: true
                    settings.autoVerify = p[Keys.AUTO_VERIFY] ?: true
                    settings.isKor = p[Keys.IS_KOR] ?: true
                    settings.cmdTypes = p[Keys.CMD_TYPES] ?: "TIOBD"
                    settings.showHistory = p[Keys.SHOW_HISTORY] ?: true
                    // 테마: 새 값 우선, 없으면 v1.x 다크 모드 값을 옮기고, 둘 다 없으면 다크
                    settings.themeMode = p[Keys.THEME_MODE]
                        ?: p[Keys.DARK_MODE]?.let { if (it) ThemeMode.DARK else ThemeMode.LIGHT }
                        ?: ThemeMode.DARK
                    settings.perfMode = p[Keys.PERF_MODE] ?: PerfSetting.AUTO
                }
            } catch (e: Exception) {
                CrashLogger.write(this@MainActivity, "WARN prefs", e)
            }
        }

        setContent {
            val dark = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                else -> true
            }
            val low = when (settings.perfMode) {
                PerfSetting.LOW -> true
                PerfSetting.FULL -> false
                else -> autoLow
            }
            // 상태바·내비게이션바 아이콘 색을 테마에 맞춤
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            AppTheme(dark = dark, lowEffects = low, isKor = settings.isKor) {
                MainApp(tag = tagState, actions = this@MainActivity, settings = settings, autoLow = autoLow, onResetAll = ::resetAll)
            }
        }
    }

    // ---------- TagActions ----------
    override fun setPart1(v: TextFieldValue) {
        tagState.part1 = v
        lifecycleScope.launch { RFIDStore.edit { it[Keys.PART1] = v.text } }
    }

    override fun setPart2(v: String) {
        tagState.part2 = v
        lifecycleScope.launch { RFIDStore.edit { it[Keys.PART2] = v } }
    }

    override fun setPart3(v: TextFieldValue) {
        tagState.part3 = v
        lifecycleScope.launch { RFIDStore.edit { it[Keys.PART3] = v.text } }
    }

    override fun requestWrite(code: String) {
        tagState.targetCode = code
        tagState.status = TagStatus.WRITING
    }

    override fun setContinuous(on: Boolean) {
        tagState.isContinuous = on
        if (!on) tagState.status = TagStatus.IDLE
    }

    override fun revertToWriting() {
        tagState.status = TagStatus.WRITING
    }

    override fun cancelWrite() {
        tagState.status = TagStatus.IDLE
    }

    override fun timeout() {
        addHistoryEntry("[${tr("e_time", isKor)}] ${tagState.targetCode}")
        tagState.errorTitle = tr("err_time", isKor)
        tagState.detail = tr("err_retry", isKor)
        tagState.status = TagStatus.ERROR
        playFeedback(false)
    }

    override fun clearHistory() {
        tagState.history.clear()
        lifecycleScope.launch { RFIDStore.edit { it[Keys.HISTORY] = "" } }
    }

    private fun resetAll() {
        tagState.history.clear()
        lifecycleScope.launch {
            RFIDStore.edit { it.clear() }
            dataStore.edit { it.clear() }
        }
    }

    private fun now(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    private fun addHistoryEntry(entry: String) {
        tagState.history.add(0, "[${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}] $entry")
        if (tagState.history.size > 100) tagState.history.removeAt(tagState.history.lastIndex)
        // 메인 스레드에서 문자열로 복사 후 저장 (다른 스레드에서 리스트 순회 방지)
        val snapshot = tagState.history.joinToString("|")
        lifecycleScope.launch { RFIDStore.edit { it[Keys.HISTORY] = snapshot } }
    }

    // ---------- NFC ----------
    override fun onResume() {
        super.onResume()
        registerReceiver(nfcStateReceiver, IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED))
        nfcAdapter?.let {
            tagState.nfcEnabled = it.isEnabled
            if (!it.isEnabled) Toast.makeText(this, tr("t_ndis", isKor), Toast.LENGTH_LONG).show()
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
        val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        tag?.let {
            if (tagState.status == TagStatus.WRITING || tagState.isContinuous) writeAndVerifyTag(it, tagState.targetCode) else readTag(it)
        }
    }

    private fun parseBlock(res: ByteArray): String =
        String(res.copyOfRange(1, res.size), Charsets.US_ASCII).replace(Regex("[^A-Za-z0-9]"), "").trim()

    private fun readCommand(tag: Tag) = ByteArray(11).apply {
        this[0] = 0x22; this[1] = 0x20; System.arraycopy(tag.id, 0, this, 2, 8); this[10] = 0x00
    }

    private fun setError(title: String, detail: String) {
        tagState.errorTitle = title
        tagState.detail = detail
        tagState.eventTime = now()
        tagState.status = TagStatus.ERROR
        playFeedback(false)
    }

    private fun readTag(tag: Tag) {
        val nfcV = NfcV.get(tag) ?: return
        try {
            nfcV.connect()
            val response = nfcV.transceive(readCommand(tag))
            if (response != null && response.isNotEmpty() && response[0].toInt() == 0) {
                val textData = parseBlock(response)
                tagState.currentTag = textData
                tagState.eventTime = now()
                tagState.status = TagStatus.READ_SUCCESS
                addHistoryEntry("[READ] Data : $textData")
                playFeedback(true)
            } else {
                setError(tr("err_read", isKor), tr("err_retry", isKor))
            }
        } catch (e: Exception) {
            // 태그 이탈(TagLost)은 정상 상황이라 제외하고 기록
            if (e !is TagLostException) CrashLogger.write(this, "WARN nfc read", e)
            setError(tr("err_comm", isKor), if (e is TagLostException) tr("err_lost", isKor) else tr("err_retry", isKor))
        } finally {
            try { nfcV.close() } catch (_: Exception) {}
        }
    }

    private fun writeAndVerifyTag(tag: Tag, data: String) {
        val nfcV = NfcV.get(tag) ?: return
        try {
            nfcV.connect()
            val readCmd = readCommand(tag)

            // 쓰기 전 기존값 읽기 (실패해도 쓰기는 진행, 태그 이탈은 그대로 오류 처리)
            val oldData: String? = try {
                val r = nfcV.transceive(readCmd)
                if (r != null && r.isNotEmpty() && r[0].toInt() == 0) parseBlock(r) else null
            } catch (e: TagLostException) {
                throw e
            } catch (_: Exception) {
                null
            }
            // 이력 표기: 기존 → 신규 (마지막 토큰이 신규값이어야 이력 클릭 시 코드 불러오기가 동작함)
            val change = when {
                oldData == null -> data
                oldData == data -> "= $data"
                else -> "${oldData.ifEmpty { "EMPTY" }} → $data"
            }
            // 되돌리기 대상: 정상 4자리 코드이고 값이 바뀐 경우만
            val undoCode = if (oldData != null && oldData.length == 4 && oldData != data) oldData else ""

            val blockData = data.toByteArray(Charsets.US_ASCII).let { b -> ByteArray(4) { i -> if (i < b.size) b[i] else 0x20.toByte() } }
            val cmd = ByteArray(15).apply {
                this[0] = 0x22; this[1] = 0x21; System.arraycopy(tag.id, 0, this, 2, 8); this[10] = 0x00; System.arraycopy(blockData, 0, this, 11, 4)
            }
            val response = nfcV.transceive(cmd)

            if (response != null && response.isNotEmpty() && response[0].toInt() == 0) {
                if (settings.autoVerify) {
                    val readRes = nfcV.transceive(readCmd)
                    if (readRes != null && readRes.isNotEmpty() && readRes[0].toInt() == 0) {
                        val readData = parseBlock(readRes)
                        if (readData == data) {
                            writeDone(data, undoCode, "$change · ${tr("verify_ok", isKor)}", "[WRITE+VERIFY OK] : $change")
                        } else {
                            addHistoryEntry("[VERIFY ERR] : $data != $readData")
                            setError(tr("err_verify", isKor), "$data ≠ ${readData.ifEmpty { "EMPTY" }}")
                        }
                    } else {
                        setError(tr("err_verify", isKor), tr("err_retry", isKor))
                    }
                } else {
                    writeDone(data, undoCode, change, "[WRITE OK] : $change")
                }
            } else {
                setError(tr("err_write", isKor), tr("err_retry", isKor))
            }
        } catch (e: Exception) {
            if (e !is TagLostException) CrashLogger.write(this, "WARN nfc write", e)
            setError(tr("err_comm", isKor), if (e is TagLostException) tr("err_lost", isKor) else tr("err_retry", isKor))
        } finally {
            try { nfcV.close() } catch (_: Exception) {}
        }
    }

    private fun writeDone(data: String, undoCode: String, detail: String, history: String) {
        tagState.currentTag = data
        tagState.prevCode = undoCode
        tagState.detail = detail
        tagState.eventTime = now()
        tagState.status = TagStatus.WRITE_SUCCESS
        addHistoryEntry(history)
        playFeedback(true)
    }

    // ---------- 피드백 ----------
    private fun playFeedback(isSuccess: Boolean) {
        // 피드백 실패가 앱 종료로 이어지지 않도록 예외 차단
        if (settings.vib) {
            try {
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                val duration = if (isSuccess) 100L else 1000L
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                else @Suppress("DEPRECATION") vibrator.vibrate(duration)
            } catch (e: Exception) {
                CrashLogger.write(this, "WARN vibrate", e)
            }
        }
        if (settings.sound) {
            try {
                // ToneGenerator 1개 재사용 (매번 생성하면 오디오 트랙이 누적되어 생성자에서 RuntimeException 발생)
                val tg = toneGenerator ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100).also { toneGenerator = it }
                tg.startTone(if (isSuccess) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_CDMA_ABBR_ALERT, 200)
            } catch (e: Exception) {
                CrashLogger.write(this, "WARN tone", e)
                try { toneGenerator?.release() } catch (_: Exception) {}
                toneGenerator = null
            }
        }
    }

    override fun onDestroy() {
        try { toneGenerator?.release() } catch (_: Exception) {}
        toneGenerator = null
        super.onDestroy()
    }
}
