package com.example.agv_rfid_manager

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.nfc.*
import android.nfc.tech.NfcV
import android.os.*
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

val Context.dataStore by preferencesDataStore(name = "agv_settings")
val Context.RFIDStore by preferencesDataStore(name = "RFID_settings")

val VIB_KEY = booleanPreferencesKey("vib_enabled")
val SOUND_KEY = booleanPreferencesKey("sound_enabled")
val AUTO_VERIFY_KEY = booleanPreferencesKey("auto_verify")
val IS_KOR_KEY = booleanPreferencesKey("is_kor")
val CMD_TYPES_KEY = stringPreferencesKey("cmd_types")
val SHOW_HISTORY_KEY = booleanPreferencesKey("show_history")
val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
val PART1_KEY = stringPreferencesKey("part1")
val PART2_KEY = stringPreferencesKey("part2")
val PART3_KEY = stringPreferencesKey("part3")

val PARAM_READ_PKT_KEY = stringPreferencesKey("param_read_pkt")
val PARAM_WRITE_PFX_KEY = stringPreferencesKey("param_write_pfx")
val PARAM_SAVED_DATA_KEY = stringPreferencesKey("param_saved_data")

val TUNER_READ_PKT_KEY = stringPreferencesKey("tuner_read_pkt")
val TUNER_WRITE_PFX_KEY = stringPreferencesKey("tuner_write_pfx")
val TUNER_IS_WIDE_KEY = booleanPreferencesKey("tuner_is_wide")
val TUNER_IS_PRO_KEY = booleanPreferencesKey("tuner_is_pro")
val TUNER_PRESET_KEY_PREFIX = "tuner_preset_"

enum class TagStatus { IDLE, WRITING, READ_SUCCESS, WRITE_SUCCESS, ERROR }
private val MONO_STYLE = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
private const val STX = "\u0002"
private const val ETX = "\u0003"

fun tr(k: String, isKor: Boolean): String = Strings[k]?.get(if (isKor) 1 else 0) ?: k
val Strings = mapOf(
    "tab_main" to arrayOf("NFC", "NFC"),
    "tab_param" to arrayOf("PARAM", "파라미터"),
    "tab_tuner" to arrayOf("TUNER", "튜너"),
    "tab_guide" to arrayOf("GUIDE", "가이드"),
    "tab_set" to arrayOf("SET", "설정"),
    "g_tab_app" to arrayOf("APP", "앱 안내"),
    "g_tab_err" to arrayOf("Errors", "에러 코드"),
    "g_tab_param" to arrayOf("Parameter", "파라미터"),
    "g_tba" to arrayOf("To be added in the future.", "향후 추가 예정입니다."),
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
    "s_theme" to arrayOf("Theme Mode", "테마 모드"),
    "s_theme_d" to arrayOf("Select Light or Dark mode", "라이트 또는 다크 모드를 선택하세요"),
    "s_cmd" to arrayOf("Command Types", "커맨드 타입 (알파벳)"),
    "s_cmd_d" to arrayOf("Allowed alphabets (e.g. TIOBD)", "사용할 알파벳 입력, 순서 변경 가능 (예: TIOBD)"),
    "s_hist_view" to arrayOf("Show History on Main", "메인 화면 기록 표시"),
    "s_hist_view_d" to arrayOf("Show operation logs on the main screen", "메인 화면 하단에 작업 기록을 표시합니다"),
    "s_vib" to arrayOf("Vibration Feedback", "진동 피드백"),
    "s_vib_d" to arrayOf("Long vibration on error, short on success", "성공 시 짧게, 오류 시 길게 진동"),
    "s_snd" to arrayOf("Sound Feedback", "소리 피드백"),
    "s_snd_d" to arrayOf("Play beep sound on success/error", "작업 성공 시 비프음 발생"),
    "s_nfc" to arrayOf("NFC Operations", "NFC 설정"),
    "s_verify" to arrayOf("Auto-Verify Write", "태그 입력 후 자동 검증"),
    "s_verify_d" to arrayOf("Read tag automatically after writing", "입력 완료 후 읽은 데이터와 검증 진행"),
    "s_data" to arrayOf("Data Management", "데이터 관리"),
    "s_clear" to arrayOf("Clear All History", "모든 기록 지우기"),
    "s_clear_d" to arrayOf("Delete all operation logs permanently", "모든 작업 기록을 영구적으로 삭제합니다"),
    "s_clr_title" to arrayOf("Reset Everything", "전체 초기화"),
    "s_clr_desc" to arrayOf("All operation logs and settings will be reset. Continue?", "모든 작업 기록이 삭제되며 설정값도 모두 초기화됩니다. 계속하시겠습니까?"),
    "s_crash" to arrayOf("Crash Log", "크래시 로그"),
    "s_crash_d" to arrayOf("View/share logs of unexpected exits and errors", "비정상 종료·오류 기록 확인 및 공유"),
    "c_empty" to arrayOf("No logs recorded.", "기록된 로그가 없습니다."),
    "c_share" to arrayOf("SHARE", "공유"),
    "c_del" to arrayOf("DELETE", "삭제"),
    "s_ver" to arrayOf("App Version", "앱 버전"),
    "s_conn" to arrayOf("Connection", "연결 관리"),
    "s_usb" to arrayOf("USB Serial Info", "USB 시리얼 정보"),
    "s_usb_d" to arrayOf("Check connected USB device status", "연결된 USB 기기의 상태와 정보를 확인합니다"),
    "d_usb_title" to arrayOf("USB Device Info", "USB 기기 정보"),
    "d_usb_none" to arrayOf("No USB serial device connected.", "연결된 USB 시리얼 기기가 없습니다."),
    "m_on" to arrayOf("🟢 NFC ON", "🟢 NFC 켜짐"),
    "m_off" to arrayOf("🔴 NFC OFF", "🔴 NFC 꺼짐"),
    "m_wait" to arrayOf("WAITING", "대기 중"),
    "m_succ" to arrayOf("SUCCESS", "성공"),
    "m_err" to arrayOf("ERROR", "오류"),
    "m_c_wait" to arrayOf("CONTINUOUS\nWAITING", "연속 쓰기\n대기 중"),
    "m_stop" to arrayOf("STOP CONTINUOUS", "연속 쓰기 종료"),
    "m_write" to arrayOf("WRITE\n(Continuous)", "쓰기\n(연속)"),
    "m_cancel" to arrayOf("CANCEL WRITE", "쓰기 취소"),
    "m_undo" to arrayOf("UNDO", "되돌리기"),
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
    "e_time" to arrayOf("TIMEOUT", "시간 초과"),
    "p_read_pkt" to arrayOf("Read Packet", "읽기 패킷"),
    "p_write_pfx" to arrayOf("Write Prefix", "쓰기 접두사"),
    "p_read" to arrayOf("READ", "읽기"),
    "p_write" to arrayOf("WRITE", "쓰기"),
    "p_save" to arrayOf("SAVE", "저장"),
    "p_load" to arrayOf("LOAD", "불러오기"),
    "p_memo" to arrayOf("Memo", "메모"),
    "p_date" to arrayOf("Date", "날짜"),
    "p_no_data" to arrayOf("No saved data", "저장된 데이터 없음"),
    "t_wide" to arrayOf("WIDE(32)", "와이드(32)"),
    "t_norm" to arrayOf("NORM(16)", "일반(16)"),
    "t_pro" to arrayOf("PRO", "프로"),
    "t_easy" to arrayOf("EASY", "이지"),
    "t_grp" to arrayOf("CH", "채널"),
    "t_base" to arrayOf("Base Value", "기준 값"),
    "t_ccnt" to arrayOf("Center Count", "센터 개수"),
    "t_coff" to arrayOf("Center Offset", "센터 오프셋"),
    "t_apply" to arrayOf("APPLY", "적용"),
    "t_batch" to arrayOf("Batch Apply", "일괄 적용")
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

data class AgvError(val code: String, val nameKor: String, val nameEng: String, val actionKor: String, val actionEng: String)

val AGV_ERRORS = listOf(
    AgvError("E202", "좌측 모터 과부하", "Left Motor Overload", "1. 정지 후 재시작\n2. 주행 속도 파라미터 확인 및 조정\n3. 좌측 모터 드라이버 / 모터 / 감속기 점검", "1. Stop and restart\n2. Check/adjust driving speed parameter\n3. Inspect left motor driver/motor/reducer"),
    AgvError("E203", "우측 모터 과부하", "Right Motor Overload", "1. 정지 후 재시작\n2. 주행 속도 파라미터 확인 및 조정\n3. 우측 모터 드라이버 / 모터 / 감속기 점검", "1. Stop and restart\n2. Check/adjust driving speed parameter\n3. Inspect right motor driver/motor/reducer"),
    AgvError("E204", "비상 정지 스위치 감지", "Emergency Stop Detected", "1. 비상 정지 스위치 해제, 정지 후 재시작\n2. 비상 정지 스위치 정상 작동 여부 확인", "1. Release emergency stop, stop and restart\n2. Check if emergency stop switch is working properly"),
    AgvError("E205", "범퍼 센서 감지", "Bumper Sensor Detected", "1. 범퍼 센서 해제, 정지 후 재시작\n2. 범퍼 센서 정상 작동 여부 확인\n3. 범퍼 센서 감도 조절", "1. Clear bumper sensor, stop and restart\n2. Check if bumper sensor is working properly\n3. Adjust bumper sensor sensitivity"),
    AgvError("E257", "주행로 이탈", "Off Track", "1. 주행로 내 AGV 정렬 여부 확인, 정지 후 재시작\n2. 주행로 테이프 육안 검사 (이물질, 오염, 훼손 등 확인)\n3. '가이드 센서' 동작 여부 확인 (테이프 감지 위치에 LED 점등 여부)\n4. '가이드 센서' 감도 조절 실행\n5. 주행 방향 전환 릴레이 혹은 스위칭 보드 정상 작동 여부 확인\n6. 양방향 모두 주행 불가시 메인 보드 점검 실시", "1. Align AGV on track, stop and restart\n2. Visually inspect guide tape (debris, contamination, damage)\n3. Check guide sensor operation (LED on at tape position)\n4. Adjust guide sensor sensitivity\n5. Check direction switching relay/board\n6. Inspect main board if both directions fail"),
    AgvError("E258", "회전 위치 이탈", "Rotation Off Track", "1. 주행로 내 AGV 정렬 여부 확인, 정지 후 재시작\n2. 주행로 테이프 육안 검사 (이물질, 오염, 훼손 등 확인)\n3. '가이드 센서' 동작 여부 확인 (테이프 감지 위치에 LED 점등 여부)\n4. '가이드 센서' 감도 조절 실행\n5. 주행 방향 전환 릴레이 혹은 스위칭 보드 정상 작동 여부 확인\n6. 양방향 모두 주행 불가시 메인 보드 점검 실시", "1. Align AGV on track, stop and restart\n2. Visually inspect guide tape (debris, contamination, damage)\n3. Check guide sensor operation (LED on at tape position)\n4. Adjust guide sensor sensitivity\n5. Check direction switching relay/board\n6. Inspect main board if both directions fail"),
    AgvError("E260", "배터리 고전압", "High Battery Voltage", "1. 배터리 전압 확인 후 파라미터에 오프셋 값 반영\n2. 배터리 / 충전기 정상 작동 여부 확인 (과충전)", "1. Check battery voltage and apply offset to parameter\n2. Check battery/charger operation (overcharge)"),
    AgvError("E280", "배터리 저전압", "Low Battery Voltage", "1. 배터리 교체 / 충전\n2. 배터리 전압 확인 후 파리미터에 오프셋 값 반영", "1. Replace/charge battery\n2. Check battery voltage and apply offset to parameter"),
    AgvError("E281", "전방 장애물 감지", "Front Obstacle Detected", "1. 전방 장애물 제거시 자동 출발\n2. 장애물 감지 센서 정상 작동 여부 확인\n3. 장애물 감지 센서 설정값(감도) 확인\n4. (적외선 타입) 전방에 반사체 여부 확인\n5. (LiDAR 센서) 센서 FND에 마지막 자리 '4' 인지 확인 (센서 오류)", "1. Auto-starts when obstacle is removed\n2. Check obstacle sensor operation\n3. Check obstacle sensor sensitivity\n4. (IR type) Check for reflectors ahead\n5. (LiDAR) Check if last digit on sensor FND is '4' (sensor error)"),
    AgvError("E285", "교통 제어 명령 대기중(ACS)", "Waiting for Traffic Control(ACS)", "1. 교통 제어 명령 수신시 자동 출발\n2. Zigbee 무선 통신 보드 정상 작동 여부 확인\n3. ACS 내 교통 제어 현황 확인\n4. 교통 제어 태그 / AGV ID의 ACS 설정 여부 확인", "1. Auto-starts upon receiving traffic control command\n2. Check Zigbee wireless board operation\n3. Check traffic control status in ACS\n4. Check ACS settings for traffic control tag/AGV ID"),
    AgvError("E290", "위치 오류 (AGV별 전용 설정)", "Position Error (Custom)", "1. 각 호기별 특수 목적 오류 송출\n2. 자재 감지 센서, 목적지 정보, Time Out 등 확인", "1. Custom error output per unit\n2. Check material sensor, destination info, timeouts, etc.")
)

object UsbSerialHelper {
    private var port: UsbSerialPort? = null

    fun connect(usbManager: UsbManager): Boolean {
        if (port != null && port!!.isOpen) return true
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) return false
        val driver = availableDrivers[0]
        val connection = usbManager.openDevice(driver.device) ?: return false
        port = driver.ports[0]
        return try {
            port?.open(connection)
            port?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            true
        } catch (e: Exception) { false }
    }

    fun sendAndReceive(cmd: String, callback: (String?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (port == null || !port!!.isOpen) { withContext(Dispatchers.Main) { callback(null) }; return@launch }
                port?.write(cmd.toByteArray(), 1000)
                delay(100)
                val buffer = ByteArray(1024)
                val len = port?.read(buffer, 2000) ?: 0
                if (len > 0) {
                    val response = String(buffer, 0, len)
                    withContext(Dispatchers.Main) { callback(response) }
                } else { withContext(Dispatchers.Main) { callback(null) } }
            } catch (e: Exception) { withContext(Dispatchers.Main) { callback(null) } }
        }
    }

    fun disconnect() {
        try { port?.close() } catch (e: Exception) {}
        port = null
    }
}

data class ParamSaveData(val at: String, val id: String, val memo: String, val date: String, val values: List<String>)

@Composable
fun MainApp(
    currentTag: String, tagStatus: TagStatus, targetCode: String, prevCode: String, historyList: List<String>,
    isNfcEnabled: Boolean, isContinuousMode: Boolean, isKor: Boolean, isDarkMode: Boolean, cmdTypesStr: String, showHistory: Boolean,
    part1: TextFieldValue, onPart1Change: (TextFieldValue) -> Unit, part2: String, onPart2Change: (String) -> Unit, part3: TextFieldValue, onPart3Change: (TextFieldValue) -> Unit,
    onWriteRequested: (String) -> Unit, onContinuousToggled: (Boolean) -> Unit, onRevertToWriting: () -> Unit, onCancelWrite: () -> Unit, onTimeout: () -> Unit, onClearHistory: () -> Unit, onResetAll: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = if(isDarkMode) Color(0xFF1E1E1E) else Color.White) {
                NavigationBarItem(icon = { Icon(Icons.Default.Nfc, null) }, label = { Text(tr("tab_main", isKor), fontSize = 10.sp) }, selected = selectedTab == 0, onClick = { selectedTab = 0 })
                NavigationBarItem(icon = { Icon(Icons.Default.ListAlt, null) }, label = { Text(tr("tab_param", isKor), fontSize = 10.sp) }, selected = selectedTab == 1, onClick = { selectedTab = 1 })
                NavigationBarItem(icon = { Icon(Icons.Default.Tune, null) }, label = { Text(tr("tab_tuner", isKor), fontSize = 10.sp) }, selected = selectedTab == 2, onClick = { selectedTab = 2 })
                NavigationBarItem(icon = { Icon(Icons.Default.MenuBook, null) }, label = { Text(tr("tab_guide", isKor), fontSize = 10.sp) }, selected = selectedTab == 3, onClick = { selectedTab = 3 })
                NavigationBarItem(icon = { Icon(Icons.Default.Settings, null) }, label = { Text(tr("tab_set", isKor), fontSize = 10.sp) }, selected = selectedTab == 4, onClick = { selectedTab = 4 })
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> AGVControlScreen(currentTag, tagStatus, targetCode, prevCode, historyList, isNfcEnabled, isContinuousMode, isKor, isDarkMode, cmdTypesStr.map { it.toString() }, showHistory, part1, onPart1Change, part2, onPart2Change, part3, onPart3Change, onWriteRequested, onContinuousToggled, onRevertToWriting, onCancelWrite, onTimeout, onClearHistory)
                1 -> ParamManagerScreen(isKor, isDarkMode)
                2 -> GuideSensorTunerScreen(isKor, isDarkMode)
                3 -> GuideScreen(isKor, isDarkMode)
                4 -> AppSettingsScreen(isKor, isDarkMode, cmdTypesStr, onResetAll)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ParamManagerScreen(isKor: Boolean, isDarkMode: Boolean) {
    val context = LocalContext.current
    val usbManager = remember { context.getSystemService(Context.USB_SERVICE) as UsbManager }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var readPkt by remember { mutableStateOf("REQ_PARAM") }
    var writePfx by remember { mutableStateOf("SET_PARAM") }
    val params = remember { mutableStateListOf(*Array(54) { "0" }) }
    var originalParams by remember { mutableStateOf(List(54) { "0" }) }
    var savedDataList by remember { mutableStateOf(listOf<ParamSaveData>()) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var showLoadDialog by remember { mutableStateOf(false) }
    var showReadConfirmDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        readPkt = prefs[PARAM_READ_PKT_KEY] ?: "REQ_PARAM"
        writePfx = prefs[PARAM_WRITE_PFX_KEY] ?: "SET_PARAM"
        val savedJsonStr = prefs[PARAM_SAVED_DATA_KEY] ?: "[]"
        try {
            val arr = JSONArray(savedJsonStr)
            val list = mutableListOf<ParamSaveData>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val valsArr = obj.getJSONArray("values")
                val vals = List(valsArr.length()) { idx -> valsArr.getString(idx) }
                list.add(ParamSaveData(obj.getString("at"), obj.getString("id"), obj.getString("memo"), obj.getString("date"), vals))
            }
            savedDataList = list
        } catch (e: Exception) {}
    }

    val saveCurrentData = { at: String, id: String, memo: String ->
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val newData = ParamSaveData(at, id, memo, dateStr, params.toList())
        val updatedList = listOf(newData) + savedDataList
        savedDataList = updatedList

        val arr = JSONArray()
        updatedList.forEach {
            val obj = JSONObject().put("at", it.at).put("id", it.id).put("memo", it.memo).put("date", it.date)
            obj.put("values", JSONArray(it.values))
            arr.put(obj)
        }
        coroutineScope.launch { context.dataStore.edit { it[PARAM_SAVED_DATA_KEY] = arr.toString() } }
    }

    val removeData = { dataToRemove: ParamSaveData ->
        val updatedList = savedDataList.filter { it != dataToRemove }
        savedDataList = updatedList
        val arr = JSONArray()
        updatedList.forEach {
            val obj = JSONObject().put("at", it.at).put("id", it.id).put("memo", it.memo).put("date", it.date)
            obj.put("values", JSONArray(it.values))
            arr.put(obj)
        }
        coroutineScope.launch { context.dataStore.edit { it[PARAM_SAVED_DATA_KEY] = arr.toString() } }
    }

    val performRead = {
        if (!UsbSerialHelper.connect(usbManager)) {
            Toast.makeText(context, "USB 연결 실패", Toast.LENGTH_SHORT).show()
        } else {
            isLoading = true
            UsbSerialHelper.sendAndReceive("$STX$readPkt$ETX") { res ->
                isLoading = false
                if (res != null) {
                    val clean = res.replace(STX, "").replace(ETX, "")
                    val parts = clean.split(":")
                    if (parts.size > 1) {
                        parts.drop(1).forEachIndexed { i, v -> if (i < 54) params[i] = v }
                        originalParams = params.toList()
                        saveCurrentData(params[0], params[1], "Auto Saved (Read)")
                        Toast.makeText(context, tr("m_succ", isKor), Toast.LENGTH_SHORT).show()
                    }
                } else { Toast.makeText(context, tr("e_comm", isKor), Toast.LENGTH_SHORT).show() }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp).pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = readPkt, onValueChange = { readPkt = it; coroutineScope.launch { context.dataStore.edit { prefs -> prefs[PARAM_READ_PKT_KEY] = it } } }, label = { Text(tr("p_read_pkt", isKor)) }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = writePfx, onValueChange = { writePfx = it; coroutineScope.launch { context.dataStore.edit { prefs -> prefs[PARAM_WRITE_PFX_KEY] = it } } }, label = { Text(tr("p_write_pfx", isKor)) }, modifier = Modifier.weight(1f), singleLine = true)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = {
                if (params.toList() != originalParams) showReadConfirmDialog = true else performRead()
            }, modifier = Modifier.weight(1f).padding(horizontal = 2.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text(tr("p_read", isKor), maxLines = 1, softWrap = false) }
            Spacer(modifier = Modifier.width(2.dp))
            Button(onClick = {
                if (!UsbSerialHelper.connect(usbManager)) {
                    Toast.makeText(context, "USB 연결 실패", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isLoading = true
                val payload = params.joinToString(":")
                UsbSerialHelper.sendAndReceive("$STX$writePfx:$payload$ETX") { res ->
                    isLoading = false
                    if (res != null) {
                        saveCurrentData(params[0], params[1], "Auto Saved (Write)")
                        originalParams = params.toList()
                        Toast.makeText(context, tr("m_succ", isKor), Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(context, tr("m_err", isKor), Toast.LENGTH_SHORT).show()
                }
            }, modifier = Modifier.weight(1f).padding(horizontal = 2.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text(tr("p_write", isKor), maxLines = 1, softWrap = false) }
            Spacer(modifier = Modifier.width(2.dp))
            Button(onClick = { showSaveDialog = true }, modifier = Modifier.weight(1f).padding(horizontal = 2.dp), contentPadding = PaddingValues(horizontal = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text(tr("p_save", isKor), maxLines = 1, softWrap = false) }
            Spacer(modifier = Modifier.width(2.dp))
            Button(onClick = { showLoadDialog = true }, modifier = Modifier.weight(1f).padding(horizontal = 2.dp), contentPadding = PaddingValues(horizontal = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))) { Text(tr("p_load", isKor), maxLines = 1, softWrap = false) }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
            items(54) { index ->
                var tfValue by remember(params[index]) { mutableStateOf(TextFieldValue(params[index])) }
                OutlinedTextField(
                    value = tfValue,
                    onValueChange = {
                        if(it.text.length <= 4) {
                            tfValue = it
                            params[index] = it.text
                        }
                    },
                    label = { Text("P${index + 1}", maxLines = 1, softWrap = false, fontSize = 12.sp) },
                    modifier = Modifier.padding(4.dp).onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            coroutineScope.launch {
                                delay(50)
                                tfValue = tfValue.copy(selection = TextRange(0, tfValue.text.length))
                            }
                        }
                    },
                    textStyle = TextStyle(fontSize = 14.sp, textAlign = TextAlign.Center),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }
    }

    if (showReadConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showReadConfirmDialog = false },
            title = { Text("확인") },
            text = { Text("변경된 데이터가 있습니다. 다시 읽어 덮어쓰시겠습니까?") },
            confirmButton = { TextButton(onClick = { performRead(); showReadConfirmDialog = false }) { Text("확인") } },
            dismissButton = { TextButton(onClick = { showReadConfirmDialog = false }) { Text("취소") } }
        )
    }

    if (showSaveDialog) {
        var at by remember { mutableStateOf(params[0]) }
        var id by remember { mutableStateOf(params[1]) }
        var memo by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(tr("p_save", isKor)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = at, onValueChange = { at = it }, label = { Text("AT") }, singleLine = true)
                    OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text("ID") }, singleLine = true)
                    OutlinedTextField(value = memo, onValueChange = { memo = it }, label = { Text(tr("p_memo", isKor)) }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { saveCurrentData(at, id, memo); showSaveDialog = false }) { Text(tr("p_save", isKor)) } },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text(tr("d_cls", isKor)) } }
        )
    }

    if (showLoadDialog) {
        var expandedAt by remember { mutableStateOf<Set<String>>(emptySet()) }
        var expandedId by remember { mutableStateOf<Set<String>>(emptySet()) }
        var deleteTarget by remember { mutableStateOf<ParamSaveData?>(null) }

        Dialog(onDismissRequest = { showLoadDialog = false }) {
            Surface(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(tr("p_load", isKor), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (savedDataList.isEmpty()) Text(tr("p_no_data", isKor), color = Color.Gray)
                    else {
                        val numComparator = Comparator<String> { a, b ->
                            val numA = a.toIntOrNull()
                            val numB = b.toIntOrNull()
                            if (numA != null && numB != null) numA.compareTo(numB)
                            else if (numA != null) -1
                            else if (numB != null) 1
                            else a.compareTo(b)
                        }

                        val grouped = remember(savedDataList) {
                            savedDataList
                                .sortedByDescending { it.date }
                                .groupBy { it.at }
                                .toSortedMap(numComparator)
                                .mapValues { (_, itemsInAt) ->
                                    itemsInAt.groupBy { it.id }.toSortedMap(numComparator)
                                }
                        }

                        LazyColumn {
                            grouped.forEach { (at, idMap) ->
                                item {
                                    val isAtExpanded = expandedAt.contains(at)
                                    Row(modifier = Modifier.fillMaxWidth().clickable { expandedAt = if (isAtExpanded) expandedAt - at else expandedAt + at }.padding(8.dp)) {
                                        Icon(if (isAtExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight, null)
                                        Text("AT: $at", fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (expandedAt.contains(at)) {
                                    idMap.forEach { (id, items) ->
                                        val atIdKey = "$at-$id"
                                        item {
                                            val isIdExpanded = expandedId.contains(atIdKey)
                                            Row(modifier = Modifier.fillMaxWidth().padding(start = 24.dp).clickable { expandedId = if (isIdExpanded) expandedId - atIdKey else expandedId + atIdKey }.padding(8.dp)) {
                                                Icon(if (isIdExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight, null)
                                                Text("ID: $id", fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                        if (expandedId.contains(atIdKey)) {
                                            items(items) { data ->
                                                Card(modifier = Modifier.fillMaxWidth().padding(start = 48.dp, bottom = 4.dp).combinedClickable(
                                                    onClick = { data.values.forEachIndexed { i, v -> if (i < 54) params[i] = v }; originalParams = params.toList(); showLoadDialog = false },
                                                    onLongClick = { deleteTarget = data }
                                                )) {
                                                    Column(modifier = Modifier.padding(8.dp)) {
                                                        Text(data.date, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        if(data.memo.isNotEmpty()) Text(data.memo, fontSize = 12.sp, color = Color.Gray)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        deleteTarget?.let { data ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("삭제") },
                text = { Text("해당 데이터를 삭제하시겠습니까?") },
                confirmButton = { TextButton(onClick = { removeData(data); deleteTarget = null }) { Text("삭제", color = Color.Red) } },
                dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("취소") } }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GuideSensorTunerScreen(isKor: Boolean, isDarkMode: Boolean) {
    val context = LocalContext.current
    val usbManager = remember { context.getSystemService(Context.USB_SERVICE) as UsbManager }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var readPkt by remember { mutableStateOf("REQ_TUNER") }
    var writePfx by remember { mutableStateOf("SET_TUNER") }
    var isWideType by remember { mutableStateOf(false) }
    var isProMode by remember { mutableStateOf(false) }

    val sensorValues = remember { mutableStateListOf(*Array(32) { 50f }) }
    var isLoading by remember { mutableStateOf(false) }

    // Easy mode states
    var easyBaseVal by remember { mutableStateOf(50f) }
    var easyCenterCount by remember { mutableStateOf(2f) }
    var easyOffset by remember { mutableStateOf(0f) }

    // Pro mode batch states
    var batchValue by remember { mutableStateOf("50") }

    val presetKeys = remember { (1..5).map { stringPreferencesKey("${TUNER_PRESET_KEY_PREFIX}$it") } }
    val presets by remember(context) { context.dataStore.data.map { prefs -> presetKeys.map { prefs[it] ?: "" } } }.collectAsState(initial = List(5) { "" })

    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        readPkt = prefs[TUNER_READ_PKT_KEY] ?: "REQ_TUNER"
        writePfx = prefs[TUNER_WRITE_PFX_KEY] ?: "SET_TUNER"
        isWideType = prefs[TUNER_IS_WIDE_KEY] ?: false
        isProMode = prefs[TUNER_IS_PRO_KEY] ?: false
    }

    LaunchedEffect(easyBaseVal, easyCenterCount, easyOffset, isWideType) {
        if (!isProMode) {
            val total = if (isWideType) 32 else 16
            val count = easyCenterCount.toInt().coerceIn(2, 8)
            val centerStart = (total - count) / 2
            val centerEnd = centerStart + count
            for (i in 0 until total) {
                sensorValues[i] = if (i in centerStart until centerEnd) (easyBaseVal + easyOffset).coerceIn(10f, 99f) else easyBaseVal.coerceIn(10f, 99f)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp).pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("t_norm", isKor), fontSize = 12.sp)
                Switch(checked = isWideType, onCheckedChange = { isWideType = it; coroutineScope.launch { context.dataStore.edit { p -> p[TUNER_IS_WIDE_KEY] = it } }; easyCenterCount = 2f }, modifier = Modifier.padding(horizontal = 4.dp))
                Text(tr("t_wide", isKor), fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("t_easy", isKor), fontSize = 12.sp)
                Switch(checked = isProMode, onCheckedChange = { isProMode = it; coroutineScope.launch { context.dataStore.edit { p -> p[TUNER_IS_PRO_KEY] = it } } }, modifier = Modifier.padding(horizontal = 4.dp))
                Text(tr("t_pro", isKor), fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = readPkt, onValueChange = { readPkt = it; coroutineScope.launch { context.dataStore.edit { prefs -> prefs[TUNER_READ_PKT_KEY] = it } } }, label = { Text(tr("p_read_pkt", isKor)) }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = writePfx, onValueChange = { writePfx = it; coroutineScope.launch { context.dataStore.edit { prefs -> prefs[TUNER_WRITE_PFX_KEY] = it } } }, label = { Text(tr("p_write_pfx", isKor)) }, modifier = Modifier.weight(1f), singleLine = true)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = {
                if (!UsbSerialHelper.connect(usbManager)) { Toast.makeText(context, "USB 연결 실패", Toast.LENGTH_SHORT).show(); return@Button }
                isLoading = true
                UsbSerialHelper.sendAndReceive("$STX$readPkt$ETX") { res ->
                    isLoading = false
                    if (res != null) {
                        val clean = res.replace(STX, "").replace(ETX, "")
                        val parts = clean.split(":")
                        if (parts.size > 1) {
                            val vals = parts.drop(1)
                            isWideType = vals.size >= 32
                            coroutineScope.launch { context.dataStore.edit { p -> p[TUNER_IS_WIDE_KEY] = isWideType } }
                            val total = if (isWideType) 32 else 16
                            for (i in 0 until total) { if (i < vals.size) sensorValues[i] = (vals[i].toFloatOrNull() ?: 50f).coerceIn(5f, 99f) }
                            if (!isProMode) { easyBaseVal = sensorValues[0].coerceIn(10f, 99f); easyCenterCount = 2f; easyOffset = 0f }
                            Toast.makeText(context, tr("m_succ", isKor), Toast.LENGTH_SHORT).show()
                        }
                    } else Toast.makeText(context, tr("e_comm", isKor), Toast.LENGTH_SHORT).show()
                }
            }, modifier = Modifier.weight(1f)) { Text(tr("p_read", isKor)) }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (!UsbSerialHelper.connect(usbManager)) { Toast.makeText(context, "USB 연결 실패", Toast.LENGTH_SHORT).show(); return@Button }
                isLoading = true
                val totalSensors = if (isWideType) 32 else 16
                val payload = (0 until totalSensors).joinToString(":") { i -> String.format("%03d", sensorValues[i].toInt()) }
                UsbSerialHelper.sendAndReceive("$STX$writePfx:$payload$ETX") { res ->
                    isLoading = false
                    Toast.makeText(context, if (res != null) tr("m_succ", isKor) else tr("m_err", isKor), Toast.LENGTH_SHORT).show()
                }
            }, modifier = Modifier.weight(1f)) { Text(tr("p_write", isKor)) }
        }

        if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))

        if (!isProMode) {
            Column(modifier = Modifier.weight(1f).padding(top = 16.dp)) {
                Text("${tr("t_base", isKor)}: ${easyBaseVal.toInt()}", fontWeight = FontWeight.Bold)
                Slider(value = easyBaseVal, onValueChange = { easyBaseVal = it }, valueRange = 10f..99f)
                Spacer(modifier = Modifier.height(16.dp))
                Text("${tr("t_ccnt", isKor)}: ${easyCenterCount.toInt()}", fontWeight = FontWeight.Bold)
                Slider(value = easyCenterCount, onValueChange = { easyCenterCount = it }, valueRange = 2f..8f, steps = 5)
                Spacer(modifier = Modifier.height(16.dp))
                Column {
                    Text("${tr("t_coff", isKor)}: ${easyOffset.toInt()}", fontWeight = FontWeight.Bold)
                    Slider(value = easyOffset, onValueChange = { easyOffset = it }, valueRange = 0f..50f)
                }
            }
        } else {
            val total = if (isWideType) 32 else 16
            Column(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("t_batch", isKor), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)

                    var batchTfValue by remember(batchValue) { mutableStateOf(TextFieldValue(batchValue)) }
                    OutlinedTextField(
                        value = batchTfValue,
                        onValueChange = {
                            val f = it.text.filter { c -> c.isDigit() }
                            if(f.length <= 2) {
                                batchTfValue = it.copy(text = f)
                                batchValue = f
                            }
                        },
                        modifier = Modifier.width(60.dp).onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                coroutineScope.launch {
                                    delay(50)
                                    batchTfValue = batchTfValue.copy(selection = TextRange(0, batchTfValue.text.length))
                                }
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val v = batchValue.toFloatOrNull()?.coerceIn(5f, 99f) ?: 50f
                        for(i in 0 until total) sensorValues[i] = v
                    }) { Text(tr("t_apply", isKor), maxLines = 1, softWrap = false) }
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(total) { i ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("S${i+1}", modifier = Modifier.width(36.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
                            Slider(
                                value = sensorValues[i],
                                onValueChange = {
                                    sensorValues[i] = it
                                    batchValue = it.toInt().toString()
                                },
                                valueRange = 5f..99f,
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                            )

                            var tfValue by remember(sensorValues[i]) { mutableStateOf(TextFieldValue(sensorValues[i].toInt().toString())) }
                            BasicTextField(
                                value = tfValue,
                                onValueChange = { newStr ->
                                    val f = newStr.text.filter { c -> c.isDigit() }
                                    tfValue = newStr.copy(text = f)
                                    if (f.isNotEmpty()) {
                                        val newVal = f.toFloat().coerceIn(5f, 99f)
                                        sensorValues[i] = newVal
                                        batchValue = newVal.toInt().toString()
                                    }
                                },
                                modifier = Modifier.width(42.dp).background(if(isDarkMode) Color(0xFF424242) else Color(0xFFEEEEEE), RoundedCornerShape(4.dp)).padding(4.dp).onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        coroutineScope.launch {
                                            delay(50)
                                            tfValue = tfValue.copy(selection = TextRange(0, tfValue.text.length))
                                        }
                                    }
                                },
                                textStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center, color = if(isDarkMode) Color.White else Color.Black),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEachIndexed { index, savedData ->
                val isSaved = savedData.isNotEmpty()
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).background(if(isDarkMode) Color(0xFF424242) else Color(0xFFEEEEEE), RoundedCornerShape(4.dp)).border(1.dp, if(isSaved) Color(0xFF4CAF50) else Color.Transparent, RoundedCornerShape(4.dp)).combinedClickable(
                    onClick = {
                        if (isSaved) {
                            val vals = savedData.split(",")
                            isWideType = vals.size > 16
                            coroutineScope.launch { context.dataStore.edit { p -> p[TUNER_IS_WIDE_KEY] = isWideType } }
                            vals.forEachIndexed { i, v -> if (i < 32) sensorValues[i] = v.toFloatOrNull() ?: 50f }
                            if(!isProMode) { easyBaseVal = sensorValues[0].coerceIn(10f, 99f); easyCenterCount = 2f; easyOffset = 0f }
                        }
                    },
                    onLongClick = {
                        val currentData = sensorValues.take(if (isWideType) 32 else 16).joinToString(",") { it.toInt().toString() }
                        coroutineScope.launch { context.dataStore.edit { it[presetKeys[index]] = currentData } }
                        Toast.makeText(context, tr("t_psav", isKor), Toast.LENGTH_SHORT).show()
                    }
                ), contentAlignment = Alignment.Center) { Text("P${index + 1}", color = if(isDarkMode) Color.White else Color.Black, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun GuideScreen(isKor: Boolean, isDarkMode: Boolean) {
    var selectedGuideTab by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
        TabRow(selectedTabIndex = selectedGuideTab) {
            Tab(selected = selectedGuideTab == 0, onClick = { selectedGuideTab = 0 }) { Text(tr("g_tab_app", isKor), modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold) }
            Tab(selected = selectedGuideTab == 1, onClick = { selectedGuideTab = 1 }) { Text(tr("g_tab_err", isKor), modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold) }
            Tab(selected = selectedGuideTab == 2, onClick = { selectedGuideTab = 2 }) { Text(tr("g_tab_param", isKor), modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold) }
        }
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
            when (selectedGuideTab) {
                0 -> BasicGuideContent(isKor, isDarkMode)
                1 -> ErrorGuideContent(isKor, isDarkMode)
                2 -> ParamGuideContent(isKor)
            }
        }
    }
}

@Composable
fun BasicGuideContent(isKor: Boolean, isDarkMode: Boolean) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(tr("g_title", isKor), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black) }
        item { GuideCard(Icons.Default.Nfc, tr("g_1", isKor), tr("g_1_d", isKor), isDarkMode) }
        item { GuideCard(Icons.Default.Edit, tr("g_2", isKor), tr("g_2_d", isKor), isDarkMode) }
        item { GuideCard(Icons.Default.AllInclusive, tr("g_3", isKor), tr("g_3_d", isKor), isDarkMode) }
        item { GuideCard(Icons.Default.Save, tr("g_4", isKor), tr("g_4_d", isKor), isDarkMode) }
        item { GuideCard(Icons.Default.UnfoldMore, tr("g_5", isKor), tr("g_5_d", isKor), isDarkMode) }
        item { GuideCard(Icons.Default.History, tr("g_6", isKor), tr("g_6_d", isKor), isDarkMode) }
    }
}

@Composable
fun ErrorGuideContent(isKor: Boolean, isDarkMode: Boolean) {
    var selectedError by remember { mutableStateOf<AgvError?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(AGV_ERRORS) { err ->
            Card(modifier = Modifier.fillMaxWidth().clickable { selectedError = err }, colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF333333) else Color(0xFFF5F5F5))) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(err.code, fontWeight = FontWeight.Bold, color = if (isDarkMode) Color(0xFFEF5350) else Color.Red, fontSize = 20.sp, modifier = Modifier.width(80.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(if (isKor) err.nameKor else err.nameEng, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (isDarkMode) Color.White else Color.Black)
                }
            }
        }
    }
    selectedError?.let { err ->
        AlertDialog(
            onDismissRequest = { selectedError = null },
            title = { Text("${err.code}: ${if (isKor) err.nameKor else err.nameEng}", fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { (if (isKor) err.actionKor else err.actionEng).split("\n").forEach { action -> Text(action, fontSize = 16.sp, lineHeight = 24.sp, color = if (isDarkMode) Color.LightGray else Color.DarkGray) } } },
            confirmButton = { TextButton(onClick = { selectedError = null }) { Text(tr("d_cls", isKor)) } },
            containerColor = if (isDarkMode) Color(0xFF424242) else MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun ParamGuideContent(isKor: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tr("g_tba", isKor), fontSize = 18.sp, color = Color.Gray, fontWeight = FontWeight.Bold) }
}

@Composable
fun GuideCard(icon: ImageVector, title: String, desc: String, isDarkMode: Boolean) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF333333) else Color(0xFFF5F5F5))) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = title, modifier = Modifier.size(48.dp), tint = if (isDarkMode) Color(0xFF64B5F6) else Color(0xFF1976D2))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isDarkMode) Color.White else Color.Black)
                Text(desc, fontSize = 14.sp, color = if (isDarkMode) Color.LightGray else Color.DarkGray)
            }
        }
    }
}

@Composable
fun AppSettingsScreen(isKor: Boolean, isDarkMode: Boolean, cmdTypesStr: String, onResetAll: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val vibEnabled by remember(context) { context.RFIDStore.data.map { it[VIB_KEY] ?: true } }.collectAsState(initial = true)
    val soundEnabled by remember(context) { context.RFIDStore.data.map { it[SOUND_KEY] ?: true } }.collectAsState(initial = true)
    val autoVerifyEnabled by remember(context) { context.RFIDStore.data.map { it[AUTO_VERIFY_KEY] ?: true } }.collectAsState(initial = true)
    val showHistoryEnabled by remember(context) { context.RFIDStore.data.map { it[SHOW_HISTORY_KEY] ?: true } }.collectAsState(initial = true)
    var tempCmd by remember(cmdTypesStr) { mutableStateOf(cmdTypesStr) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val usbManager = remember { context.getSystemService(Context.USB_SERVICE) as UsbManager }
    var showUsbInfoDialog by remember { mutableStateOf(false) }
    var crashLogText by remember { mutableStateOf<String?>(null) }

    // 크래시 로그 조회 다이얼로그
    crashLogText?.let { log ->
        AlertDialog(
            onDismissRequest = { crashLogText = null },
            title = { Text(tr("s_crash", isKor), fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black) },
            text = {
                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(if (log.isEmpty()) tr("c_empty", isKor) else log, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp, color = if (isDarkMode) Color.LightGray else Color.DarkGray)
                }
            },
            confirmButton = {
                TextButton(enabled = log.isNotEmpty(), onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "AGV RFID Manager crash log")
                        putExtra(Intent.EXTRA_TEXT, log)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }) { Text(tr("c_share", isKor)) }
            },
            dismissButton = {
                Row {
                    TextButton(enabled = log.isNotEmpty(), onClick = { CrashLogger.clear(context); crashLogText = "" }) { Text(tr("c_del", isKor), color = if (log.isNotEmpty()) Color.Red else Color.Gray) }
                    TextButton(onClick = { crashLogText = null }) { Text(tr("d_cls", isKor)) }
                }
            },
            containerColor = if (isDarkMode) Color(0xFF424242) else MaterialTheme.colorScheme.surface
        )
    }

    if (showUsbInfoDialog) {
        AlertDialog(
            onDismissRequest = { showUsbInfoDialog = false },
            title = { Text(tr("d_usb_title", isKor), fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black) },
            text = {
                val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
                if (drivers.isEmpty()) {
                    Text(tr("d_usb_none", isKor), color = if (isDarkMode) Color.LightGray else Color.DarkGray)
                } else {
                    Column {
                        drivers.forEach { driver ->
                            val device = driver.device
                            val vendorId = String.format("%04X", device.vendorId)
                            val productId = String.format("%04X", device.productId)
                            val name = device.productName ?: "Unknown"
                            val manufacturer = device.manufacturerName ?: "Unknown"

                            Text("Name: $name", fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black)
                            Text("Manufacturer: $manufacturer", color = if (isDarkMode) Color.LightGray else Color.DarkGray)
                            Text("VID: $vendorId / PID: $productId", color = if (isDarkMode) Color.LightGray else Color.DarkGray)
                            Text("Port Count: ${driver.ports.size}", color = if (isDarkMode) Color.LightGray else Color.DarkGray)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showUsbInfoDialog = false }) { Text(tr("d_cls", isKor)) } },
            containerColor = if (isDarkMode) Color(0xFF424242) else MaterialTheme.colorScheme.surface
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text(tr("s_clr_title", isKor), fontWeight = FontWeight.Bold) },
            text = { Text(tr("s_clr_desc", isKor)) },
            confirmButton = { TextButton(onClick = { onResetAll(); showClearConfirmDialog = false }) { Text(tr("m_clr", isKor), color = Color.Red, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showClearConfirmDialog = false }) { Text(tr("d_cls", isKor)) } },
            containerColor = if (isDarkMode) Color(0xFF424242) else MaterialTheme.colorScheme.surface
        )
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(tr("s_title", isKor), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black)
        Spacer(modifier = Modifier.height(16.dp))

        Text(tr("s_conn", isKor), color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        ListItem(headlineContent = { Text(tr("s_usb", isKor), color = if (isDarkMode) Color.White else Color.Black) }, supportingContent = { Text(tr("s_usb_d", isKor), color = if (isDarkMode) Color.LightGray else Color.DarkGray) }, trailingContent = { Icon(Icons.Default.Usb, contentDescription = "USB", tint = if (isDarkMode) Color.White else Color.Black) }, modifier = Modifier.clickable { showUsbInfoDialog = true }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = if (isDarkMode) Color(0xFF444444) else Color.LightGray)

        Text(tr("s_pref", isKor), color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        ListItem(headlineContent = { Text(tr("s_theme", isKor), color = if (isDarkMode) Color.White else Color.Black) }, supportingContent = { Text(tr("s_theme_d", isKor), color = if (isDarkMode) Color.LightGray else Color.DarkGray) }, trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { coroutineScope.launch { context.RFIDStore.edit { it[DARK_MODE_KEY] = false } } }, colors = ButtonDefaults.buttonColors(containerColor = if (!isDarkMode) Color(0xFF1976D2) else Color(0xFF555555)), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { Text("LIGHT", fontWeight = FontWeight.Bold, color = if (!isDarkMode) Color.White else Color.LightGray) }
                Button(onClick = { coroutineScope.launch { context.RFIDStore.edit { it[DARK_MODE_KEY] = true } } }, colors = ButtonDefaults.buttonColors(containerColor = if (isDarkMode) Color(0xFF1976D2) else Color.LightGray), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { Text("DARK", fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.DarkGray) }
            }
        }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        ListItem(headlineContent = { Text(tr("s_lang", isKor), color = if (isDarkMode) Color.White else Color.Black) }, supportingContent = { Text(tr("s_lang_d", isKor), color = if (isDarkMode) Color.LightGray else Color.DarkGray) }, trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { coroutineScope.launch { context.RFIDStore.edit { it[IS_KOR_KEY] = false } } }, colors = ButtonDefaults.buttonColors(containerColor = if (!isKor) Color(0xFF1976D2) else (if (isDarkMode) Color(0xFF555555) else Color.LightGray)), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { Text("ENG", fontWeight = FontWeight.Bold, color = if (!isKor) Color.White else (if (isDarkMode) Color.LightGray else Color.DarkGray)) }
                Button(onClick = { coroutineScope.launch { context.RFIDStore.edit { it[IS_KOR_KEY] = true } } }, colors = ButtonDefaults.buttonColors(containerColor = if (isKor) Color(0xFF1976D2) else (if (isDarkMode) Color(0xFF555555) else Color.LightGray)), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { Text("KOR", fontWeight = FontWeight.Bold, color = if (isKor) Color.White else (if (isDarkMode) Color.LightGray else Color.DarkGray)) }
            }
        }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        ListItem(headlineContent = { Text(tr("s_cmd", isKor), color = if (isDarkMode) Color.White else Color.Black) }, supportingContent = { Text(tr("s_cmd_d", isKor), color = if (isDarkMode) Color.LightGray else Color.DarkGray) }, trailingContent = {
            BasicTextField(value = tempCmd, onValueChange = { input -> val f = input.filter { it.isLetter() }.uppercase().toSet().joinToString(""); tempCmd = f; coroutineScope.launch { context.RFIDStore.edit { it[CMD_TYPES_KEY] = f.ifEmpty { "T" } } } }, modifier = Modifier.width(160.dp).background(if (isDarkMode) Color(0xFF424242) else Color(0xFFEEEEEE), RoundedCornerShape(4.dp)).padding(8.dp), singleLine = true, textStyle = TextStyle(textAlign = TextAlign.Start, fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black))
        }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        ListItem(headlineContent = { Text(tr("s_hist_view", isKor), color = if (isDarkMode) Color.White else Color.Black) }, supportingContent = { Text(tr("s_hist_view_d", isKor), color = if (isDarkMode) Color.LightGray else Color.DarkGray) }, trailingContent = { Switch(checked = showHistoryEnabled, onCheckedChange = { coroutineScope.launch { context.RFIDStore.edit { prefs -> prefs[SHOW_HISTORY_KEY] = it } } }) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        ListItem(headlineContent = { Text(tr("s_vib", isKor), color = if (isDarkMode) Color.White else Color.Black) }, supportingContent = { Text(tr("s_vib_d", isKor), color = if (isDarkMode) Color.LightGray else Color.DarkGray) }, trailingContent = { Switch(checked = vibEnabled, onCheckedChange = { coroutineScope.launch { context.RFIDStore.edit { prefs -> prefs[VIB_KEY] = it } } }) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        ListItem(headlineContent = { Text(tr("s_snd", isKor), color = if (isDarkMode) Color.White else Color.Black) }, supportingContent = { Text(tr("s_snd_d", isKor), color = if (isDarkMode) Color.LightGray else Color.DarkGray) }, trailingContent = { Switch(checked = soundEnabled, onCheckedChange = { coroutineScope.launch { context.RFIDStore.edit { prefs -> prefs[SOUND_KEY] = it } } }) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = if (isDarkMode) Color(0xFF444444) else Color.LightGray)
        Text(tr("s_nfc", isKor), color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        ListItem(headlineContent = { Text(tr("s_verify", isKor), color = if (isDarkMode) Color.White else Color.Black) }, supportingContent = { Text(tr("s_verify_d", isKor), color = if (isDarkMode) Color.LightGray else Color.DarkGray) }, trailingContent = { Switch(checked = autoVerifyEnabled, onCheckedChange = { coroutineScope.launch { context.RFIDStore.edit { prefs -> prefs[AUTO_VERIFY_KEY] = it } } }) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = if (isDarkMode) Color(0xFF444444) else Color.LightGray)
        Text(tr("s_data", isKor), color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        ListItem(headlineContent = { Text(tr("s_clear", isKor), color = if (isDarkMode) Color.White else Color.Black) }, supportingContent = { Text(tr("s_clear_d", isKor), color = if (isDarkMode) Color.LightGray else Color.DarkGray) }, trailingContent = { Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color.Red, modifier = Modifier.size(48.dp)) }, modifier = Modifier.clickable { showClearConfirmDialog = true }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        ListItem(headlineContent = { Text(tr("s_crash", isKor), color = if (isDarkMode) Color.White else Color.Black) }, supportingContent = { Text(tr("s_crash_d", isKor), color = if (isDarkMode) Color.LightGray else Color.DarkGray) }, trailingContent = { Icon(Icons.Default.BugReport, contentDescription = "Crash Log", tint = if (isDarkMode) Color.White else Color.Black) }, modifier = Modifier.clickable { crashLogText = CrashLogger.read(context) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
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
    // 직전 쓰기 전의 태그 값 (되돌리기용)
    private var prevWriteCode = mutableStateOf("")

    private var isVibEnabled = mutableStateOf(true)
    private var isSoundEnabled = mutableStateOf(true)
    private var isAutoVerifyEnabled = mutableStateOf(true)
    private var isKor = mutableStateOf(true)
    private var cmdTypesStr = mutableStateOf("TIOBD")
    private var showHistory = mutableStateOf(true)
    private var isDarkMode = mutableStateOf(false)

    private var part1State = mutableStateOf(TextFieldValue("0"))
    private var part2State = mutableStateOf("T")
    private var part3State = mutableStateOf(TextFieldValue("00"))

    private val historyList = mutableStateListOf<String>()
    private val HISTORY_KEY = stringPreferencesKey("history_data")
    private var toneGenerator: ToneGenerator? = null

    private val nfcStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) {
                isNfcEnabled.value = (intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF) == NfcAdapter.STATE_ON)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashLogger.install(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        val intent = Intent(this, javaClass).apply { addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val savedHistory = RFIDStore.data.map { it[HISTORY_KEY] ?: "" }.first()
                if (savedHistory.isNotEmpty()) historyList.addAll(savedHistory.split("|"))
                addHistoryEntry("[SYSTEM] BOOT")

                val prefsOnce = RFIDStore.data.first()
                part1State.value = TextFieldValue(prefsOnce[PART1_KEY] ?: "0")
                part2State.value = prefsOnce[PART2_KEY] ?: "T"
                part3State.value = TextFieldValue(prefsOnce[PART3_KEY] ?: "00")

                RFIDStore.data.collect { prefs ->
                    isVibEnabled.value = prefs[VIB_KEY] ?: true
                    isSoundEnabled.value = prefs[SOUND_KEY] ?: true
                    isAutoVerifyEnabled.value = prefs[AUTO_VERIFY_KEY] ?: true
                    isKor.value = prefs[IS_KOR_KEY] ?: true
                    cmdTypesStr.value = prefs[CMD_TYPES_KEY] ?: "TIOBD"
                    showHistory.value = prefs[SHOW_HISTORY_KEY] ?: true
                    isDarkMode.value = prefs[DARK_MODE_KEY] ?: false
                }
            } catch (e: Exception) { e.printStackTrace() }
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
            val colorScheme = if (isDarkMode.value) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainApp(
                        currentTag = currentTagText.value, tagStatus = tagStatus.value, targetCode = targetWriteCode.value, prevCode = prevWriteCode.value, historyList = historyList, isNfcEnabled = isNfcEnabled.value, isContinuousMode = isContinuousMode.value, isKor = isKor.value, isDarkMode = isDarkMode.value, cmdTypesStr = cmdTypesStr.value, showHistory = showHistory.value,
                        part1 = part1State.value, onPart1Change = { v -> part1State.value = v; CoroutineScope(Dispatchers.IO).launch { RFIDStore.edit { it[PART1_KEY] = v.text } } },
                        part2 = part2State.value, onPart2Change = { v -> part2State.value = v; CoroutineScope(Dispatchers.IO).launch { RFIDStore.edit { it[PART2_KEY] = v } } },
                        part3 = part3State.value, onPart3Change = { v -> part3State.value = v; CoroutineScope(Dispatchers.IO).launch { RFIDStore.edit { it[PART3_KEY] = v.text } } },
                        onWriteRequested = { code -> targetWriteCode.value = code; tagStatus.value = TagStatus.WRITING },
                        onContinuousToggled = { enabled -> isContinuousMode.value = enabled; if (!enabled) { tagStatus.value = TagStatus.IDLE; currentTagText.value = tr("m_wait", isKor.value) } },
                        onRevertToWriting = { tagStatus.value = TagStatus.WRITING },
                        onCancelWrite = { tagStatus.value = TagStatus.IDLE; currentTagText.value = "READY" },
                        onTimeout = { addHistoryEntry("[${tr("e_time", isKor.value)}] ${targetWriteCode.value}"); currentTagText.value = tr("e_time", isKor.value); tagStatus.value = TagStatus.ERROR; targetWriteCode.value = ""; playFeedback(false) },
                        onClearHistory = onClearHistory, onResetAll = onResetAll
                    )
                }
            }
        }
    }

    private fun addHistoryEntry(entry: String) {
        historyList.add(0, "[${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}] $entry")
        if (historyList.size > 100) historyList.removeAt(historyList.lastIndex)
        // 메인 스레드에서 문자열로 복사 후 저장 (IO 스레드에서 리스트 순회 중 수정 → ConcurrentModificationException 방지)
        val snapshot = historyList.joinToString("|")
        CoroutineScope(Dispatchers.IO).launch { RFIDStore.edit { it[HISTORY_KEY] = snapshot } }
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
        tag?.let { if (tagStatus.value == TagStatus.WRITING || isContinuousMode.value) writeAndVerifyTag(it, targetWriteCode.value) else readTag(it) }
    }

    private fun readTag(tag: Tag) {
        val nfcV = NfcV.get(tag) ?: return
        try {
            nfcV.connect()
            val cmd = ByteArray(11).apply { this[0] = 0x22; this[1] = 0x20; System.arraycopy(tag.id, 0, this, 2, 8); this[10] = 0x00 }
            val response = nfcV.transceive(cmd)
            if (response != null && response[0].toInt() == 0) {
                val textData = String(response.copyOfRange(1, response.size), Charsets.US_ASCII).replace(Regex("[^A-Za-z0-9]"), "").trim()
                currentTagText.value = textData; tagStatus.value = TagStatus.READ_SUCCESS; addHistoryEntry("[READ] Data : $textData"); playFeedback(true)
            } else { tagStatus.value = TagStatus.ERROR; playFeedback(false) }
        } catch (e: Exception) {
            // 태그 이탈(TagLost)은 정상 상황이라 제외하고 기록
            if (e !is TagLostException) CrashLogger.write(this, "WARN nfc read", e)
            currentTagText.value = tr("e_comm", isKor.value); tagStatus.value = TagStatus.ERROR; playFeedback(false)
        } finally { try { nfcV.close() } catch (_: Exception) {} }
    }

    private fun writeAndVerifyTag(tag: Tag, data: String) {
        val nfcV = NfcV.get(tag) ?: return
        try {
            nfcV.connect()
            val readCmd = ByteArray(11).apply { this[0] = 0x22; this[1] = 0x20; System.arraycopy(tag.id, 0, this, 2, 8); this[10] = 0x00 }
            val parseBlock = { res: ByteArray -> String(res.copyOfRange(1, res.size), Charsets.US_ASCII).replace(Regex("[^A-Za-z0-9]"), "").trim() }

            // 쓰기 전 기존값 읽기 (실패해도 쓰기는 진행, 태그 이탈은 그대로 오류 처리)
            val oldData: String? = try {
                val r = nfcV.transceive(readCmd)
                if (r != null && r.isNotEmpty() && r[0].toInt() == 0) parseBlock(r) else null
            } catch (e: TagLostException) { throw e } catch (_: Exception) { null }
            // 이력 표기: 기존 → 신규 (마지막 토큰이 신규값이어야 이력 클릭 시 코드 불러오기가 동작함)
            val change = when {
                oldData == null -> data
                oldData == data -> "= $data"
                else -> "${oldData.ifEmpty { "EMPTY" }} → $data"
            }
            // 되돌리기 대상: 정상 4자리 코드이고 값이 바뀐 경우만
            val undoCode = if (oldData != null && oldData.length == 4 && oldData != data) oldData else ""

            val blockData = data.toByteArray(Charsets.US_ASCII).let { b -> ByteArray(4) { i -> if (i < b.size) b[i] else 0x20.toByte() } }
            val cmd = ByteArray(15).apply { this[0] = 0x22; this[1] = 0x21; System.arraycopy(tag.id, 0, this, 2, 8); this[10] = 0x00; System.arraycopy(blockData, 0, this, 11, 4) }
            val response = nfcV.transceive(cmd)

            if (response != null && response[0].toInt() == 0) {
                if (isAutoVerifyEnabled.value) {
                    val readRes = nfcV.transceive(readCmd)
                    if (readRes != null && readRes[0].toInt() == 0) {
                        val readData = parseBlock(readRes)
                        if (readData == data) { currentTagText.value = data; prevWriteCode.value = undoCode; tagStatus.value = TagStatus.WRITE_SUCCESS; addHistoryEntry("[WRITE+VERIFY OK] : $change"); playFeedback(true)
                        } else { currentTagText.value = "VERIFY ERR"; tagStatus.value = TagStatus.ERROR; addHistoryEntry("[VERIFY ERR] : $data != $readData"); playFeedback(false) }
                    } else { currentTagText.value = "VERIFY ERR"; tagStatus.value = TagStatus.ERROR; playFeedback(false) }
                } else { currentTagText.value = data; prevWriteCode.value = undoCode; tagStatus.value = TagStatus.WRITE_SUCCESS; addHistoryEntry("[WRITE OK] : $change"); playFeedback(true) }
            } else { tagStatus.value = TagStatus.ERROR; playFeedback(false) }
        } catch (e: Exception) {
            if (e !is TagLostException) CrashLogger.write(this, "WARN nfc write", e)
            currentTagText.value = tr("m_err", isKor.value); tagStatus.value = TagStatus.ERROR; playFeedback(false)
        } finally { try { nfcV.close() } catch (_: Exception) {} }
    }

    private fun playFeedback(isSuccess: Boolean) {
        // 피드백 실패가 앱 종료로 이어지지 않도록 예외 차단 (catch 블록에서 재호출되는 경우 포함)
        if (isVibEnabled.value) {
            try {
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                val duration = if (isSuccess) 100L else 1000L
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") vibrator.vibrate(duration)
            } catch (e: Exception) { CrashLogger.write(this, "WARN vibrate", e) }
        }
        if (isSoundEnabled.value) {
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

// 비정상 종료(미처리 예외) 시 스택 트레이스를 앱 내부 저장소에 기록
object CrashLogger {
    private const val FILE_NAME = "crash_log.txt"
    private const val MAX_CHARS = 100_000
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try { write(appContext, "CRASH [${thread.name}]", e) } catch (_: Throwable) {}
            // 기존 핸들러로 넘겨서 정상적인 종료 처리
            defaultHandler?.uncaughtException(thread, e)
        }
    }

    @Synchronized
    fun write(context: Context, title: String, e: Throwable) {
        try {
            val file = java.io.File(context.filesDir, FILE_NAME)
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val ver = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (_: Exception) { "?" }
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            val entry = "===== $time $title =====\nv$ver / ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}\n$sw\n"
            val old = if (file.exists()) file.readText() else ""
            // 최신 기록이 위로, 최대 크기 유지
            file.writeText((entry + old).take(MAX_CHARS))
        } catch (_: Throwable) {}
    }

    fun read(context: Context): String {
        val file = java.io.File(context.filesDir, FILE_NAME)
        return try { if (file.exists()) file.readText() else "" } catch (_: Exception) { "" }
    }

    fun clear(context: Context) {
        try { java.io.File(context.filesDir, FILE_NAME).delete() } catch (_: Exception) {}
    }
}

@Composable
fun StyledBasicTextField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, modifier: Modifier, coroutineScope: CoroutineScope, isDarkMode: Boolean, keyboardType: KeyboardType = KeyboardType.NumberPassword) {
    val bg = if (isDarkMode) Color(0xFF424242) else Color(0xFFF5F5F5)
    val textCol = if (isDarkMode) Color.White else Color.Black
    BasicTextField(value = value, onValueChange = onValueChange, modifier = modifier.onFocusChanged { focusState -> if (focusState.isFocused) coroutineScope.launch { delay(100); onValueChange(value.copy(selection = TextRange(0, value.text.length))) } }, textStyle = MONO_STYLE.copy(fontSize = 28.sp, textAlign = TextAlign.Center, color = textCol), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), cursorBrush = SolidColor(textCol), decorationBox = { innerTextField -> Box(modifier = Modifier.fillMaxSize().background(bg, RoundedCornerShape(4.dp)).border(1.dp, if(isDarkMode) Color.DarkGray else Color.Gray, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) { innerTextField() } })
}

@Composable
fun HistoryItemRow(item: String, cmdTypes: List<String>, isDarkMode: Boolean, onClickCode: (String) -> Unit) {
    val isError = item.contains("ERR") || item.contains("TIMEOUT") || item.contains("오류") || item.contains("초과")
    val isSuccess = item.contains("OK") || item.contains("Data")
    val isClickable = isError || isSuccess
    val textColor = when { isError -> if (isDarkMode) Color(0xFFEF5350) else Color.Red; isSuccess -> if (isDarkMode) Color(0xFF66BB6A) else Color(0xFF388E3C); else -> if (isDarkMode) Color.LightGray else Color.DarkGray }
    Text(text = item, fontSize = 16.sp, fontWeight = FontWeight.Bold, style = MONO_STYLE, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth().clickable(enabled = isClickable) { val ex = item.split(" ").lastOrNull()?.trim() ?: ""; if (ex.length >= 4) { val code = ex.takeLast(4); if (code[1].toString() in cmdTypes) onClickCode(code) } }.padding(vertical = 4.dp), color = textColor)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AGVControlScreen(currentTag: String, tagStatus: TagStatus, targetCode: String, prevCode: String, historyList: List<String>, isNfcEnabled: Boolean, isContinuousMode: Boolean, isKor: Boolean, isDarkMode: Boolean, cmdTypes: List<String>, showHistory: Boolean, part1: TextFieldValue, onPart1Change: (TextFieldValue) -> Unit, part2: String, onPart2Change: (String) -> Unit, part3: TextFieldValue, onPart3Change: (TextFieldValue) -> Unit, onWriteRequested: (String) -> Unit, onContinuousToggled: (Boolean) -> Unit, onRevertToWriting: () -> Unit, onCancelWrite: () -> Unit, onTimeout: () -> Unit, onClearHistory: () -> Unit) {
    val context = LocalContext.current; val coroutineScope = rememberCoroutineScope(); val focusManager = LocalFocusManager.current; var isAlphabetMenuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(cmdTypes) { if (part2 !in cmdTypes && cmdTypes.isNotEmpty()) onPart2Change(cmdTypes.first()) }
    val presetKeys = remember { (1..5).map { stringPreferencesKey("preset_$it") } }
    val presets by remember(context) { context.dataStore.data.map { prefs -> presetKeys.map { prefs[it] ?: "0T00" } } }.collectAsState(initial = listOf("0T01", "0T04", "0T07", "0T21", "0T22"))
    val currentFullCode = "${part1.text}$part2${part3.text.padStart(2, '0')}"
    // pointerInput 내부 롱프레스에서 항상 최신 입력값을 참조하도록 함 (이전 값 캡처 방지)
    val latestFullCode by rememberUpdatedState(currentFullCode)
    var showHelpDialog by remember { mutableStateOf(false) }; var showNfcDialog by remember { mutableStateOf(false) }; var showHistoryDialog by remember { mutableStateOf(false) }; var showClearConfirmDialog by remember { mutableStateOf(false) }
    LaunchedEffect(isNfcEnabled) { showNfcDialog = !isNfcEnabled }
    LaunchedEffect(currentFullCode) { if (isContinuousMode) onWriteRequested(currentFullCode) }
    LaunchedEffect(tagStatus, isContinuousMode) { if (isContinuousMode) { if (tagStatus == TagStatus.WRITE_SUCCESS || tagStatus == TagStatus.ERROR) { delay(1000); if (isContinuousMode) onRevertToWriting() } } else { if (tagStatus == TagStatus.WRITING) { delay(5000); if (tagStatus == TagStatus.WRITING) onTimeout() } } }
    fun updateInputParts(code: String) { if (code.length >= 4) { onPart1Change(TextFieldValue(code[0].toString())); onPart2Change(code[1].toString()); onPart3Change(TextFieldValue(code.substring(2, 4))); focusManager.clearFocus() } }
    val cardBgColor = when { isContinuousMode && tagStatus == TagStatus.WRITE_SUCCESS -> if (isDarkMode) Color(0xFF0D47A1) else Color(0xFFBBDEFB); isContinuousMode && tagStatus == TagStatus.ERROR -> if (isDarkMode) Color(0xFFB71C1C) else Color(0xFFFFCDD2); isContinuousMode -> if (isDarkMode) Color(0xFFE65100) else Color(0xFFFFCC80); tagStatus == TagStatus.WRITING -> if (isDarkMode) Color(0xFFF57F17) else Color(0xFFFFEB3B); tagStatus == TagStatus.READ_SUCCESS -> if (isDarkMode) Color(0xFF1B5E20) else Color(0xFFC8E6C9); tagStatus == TagStatus.WRITE_SUCCESS -> if (isDarkMode) Color(0xFF0D47A1) else Color(0xFFBBDEFB); tagStatus == TagStatus.ERROR -> if (isDarkMode) Color(0xFFB71C1C) else Color(0xFFFFCDD2); else -> if (isDarkMode) Color(0xFF1E3A8A) else Color(0xFFE3F2FD) }
    val cardTextColor = if (isDarkMode) Color.White else Color.Black
    val displayText = when { isContinuousMode && tagStatus == TagStatus.WRITE_SUCCESS -> "${tr("m_succ", isKor)}\n($currentTag)"; isContinuousMode && tagStatus == TagStatus.ERROR -> "${tr("m_err", isKor)}\n($currentTag)"; isContinuousMode -> "${tr("m_c_wait", isKor)}\n($targetCode)"; tagStatus == TagStatus.WRITING -> "${tr("m_wait", isKor)}\n($targetCode)"; else -> currentTag }

    if (showClearConfirmDialog) AlertDialog(onDismissRequest = { showClearConfirmDialog = false }, title = { Text(tr("d_clr_title", isKor), fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black) }, text = { Text(tr("d_clr_desc", isKor), color = if (isDarkMode) Color.LightGray else Color.DarkGray) }, confirmButton = { TextButton(onClick = { onClearHistory(); showClearConfirmDialog = false; showHistoryDialog = false }) { Text(tr("m_clr", isKor), color = if (isDarkMode) Color(0xFFEF5350) else Color.Red, fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = { showClearConfirmDialog = false }) { Text(tr("d_cls", isKor)) } }, containerColor = if (isDarkMode) Color(0xFF424242) else MaterialTheme.colorScheme.surface)
    if (showHelpDialog) AlertDialog(onDismissRequest = { showHelpDialog = false }, title = { Text(tr("g_guide", isKor), fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black) }, text = { LazyColumn(modifier = Modifier.fillMaxWidth()) { item { Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) { Text(tr("g_tag", isKor), fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.LightGray else Color.DarkGray, modifier = Modifier.weight(1f)); Text(tr("g_func", isKor), fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.LightGray else Color.DarkGray, modifier = Modifier.weight(2f)) }; HorizontalDivider(color = if(isDarkMode) Color.DarkGray else Color.LightGray) }; items(getCommands(isKor)) { (tag, desc) -> Row(modifier = Modifier.fillMaxWidth().clickable { updateInputParts(tag); showHelpDialog = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text(tag, style = MONO_STYLE, fontSize = 28.sp, color = if (isDarkMode) Color.White else Color.Black, modifier = Modifier.weight(1f)); Text(desc, fontSize = 28.sp, color = if (isDarkMode) Color.White else Color.Black, modifier = Modifier.weight(2f), lineHeight = 32.sp) }; HorizontalDivider(thickness = 0.5.dp, color = if (isDarkMode) Color.DarkGray else Color.LightGray) } } }, confirmButton = { TextButton(onClick = { showHelpDialog = false }) { Text(tr("d_cls", isKor)) } }, containerColor = if (isDarkMode) Color(0xFF424242) else MaterialTheme.colorScheme.surface)
    if (showHistoryDialog) Dialog(onDismissRequest = { showHistoryDialog = false }) { Surface(shape = RoundedCornerShape(12.dp), color = if (isDarkMode) Color(0xFF424242) else MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) { Column(modifier = Modifier.padding(16.dp)) { Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(tr("l_title", isKor), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black); Text(text = tr("l_call", isKor), color = if (isDarkMode) Color(0xFFEF5350) else Color.Red, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { showClearConfirmDialog = true }) }; if (historyList.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tr("l_empty", isKor), color = Color.Gray) } else LazyColumn(modifier = Modifier.fillMaxSize()) { items(historyList) { log -> Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF333333) else Color(0xFFF5F5F5))) { Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) { HistoryItemRow(log, cmdTypes, isDarkMode) { code -> updateInputParts(code); showHistoryDialog = false } } } } } } } }

    // 상단 여백 제거 (Scaffold innerPadding에 상태바 inset이 이미 포함됨 → statusBarsPadding 중복 제거)
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 0.dp, bottom = 20.dp).pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { Row(verticalAlignment = Alignment.CenterVertically) { Text(text = if (isNfcEnabled) tr("m_on", isKor) else tr("m_off", isKor), fontWeight = FontWeight.Bold, color = if (isNfcEnabled) (if(isDarkMode) Color(0xFF66BB6A) else Color(0xFF388E3C)) else (if(isDarkMode) Color(0xFFEF5350) else Color.Red), style = MONO_STYLE, fontSize = 20.sp, maxLines = 1, softWrap = false); Spacer(modifier = Modifier.width(12.dp)); Icon(Icons.Default.HelpOutline, contentDescription = "Help", tint = Color.Gray, modifier = Modifier.size(36.dp).clickable { showHelpDialog = true }) } }
        Card(modifier = Modifier.fillMaxWidth().height(270.dp).clickable(enabled = tagStatus == TagStatus.READ_SUCCESS || tagStatus == TagStatus.WRITE_SUCCESS) { updateInputParts(currentTag) }, colors = CardDefaults.cardColors(containerColor = cardBgColor)) { Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(text = displayText, color = cardTextColor, fontSize = if (displayText.contains("\n")) 40.sp else 80.sp, style = MONO_STYLE, textAlign = TextAlign.Center, lineHeight = if (displayText.contains("\n")) 48.sp else 80.sp)
            // 쓰기 성공 후 기존값으로 되돌리기 (해당 값으로 쓰기 대기 진입 → 같은 태그에 다시 대기)
            if (!isContinuousMode && tagStatus == TagStatus.WRITE_SUCCESS && prevCode.isNotEmpty()) {
                TextButton(onClick = { onWriteRequested(prevCode) }, modifier = Modifier.align(Alignment.BottomCenter)) {
                    Icon(Icons.Default.Undo, contentDescription = null, tint = cardTextColor, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${tr("m_undo", isKor)} ($prevCode)", color = cardTextColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        } }
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth().height(90.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            StyledBasicTextField(value = part1, onValueChange = { input -> val f = input.text.filter { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }.uppercase(); onPart1Change(input.copy(text = if (f.isNotEmpty()) f.last().toString() else "", selection = TextRange(if (f.isNotEmpty()) 1 else 0))) }, modifier = Modifier.weight(1f).fillMaxHeight(), coroutineScope = coroutineScope, isDarkMode = isDarkMode, keyboardType = KeyboardType.Text)
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().border(1.dp, if(isDarkMode) Color.DarkGray else Color.Gray, RoundedCornerShape(4.dp)).background(if(isDarkMode) Color(0xFF424242) else Color(0xFFF5F5F5), RoundedCornerShape(4.dp)).clickable { focusManager.clearFocus(); coroutineScope.launch { delay(50); isAlphabetMenuExpanded = true } }, contentAlignment = Alignment.Center) { Text(text = part2, style = MONO_STYLE, fontSize = 32.sp, color = if(isDarkMode) Color(0xFF64B5F6) else Color(0xFF1976D2), textAlign = TextAlign.Center); DropdownMenu(expanded = isAlphabetMenuExpanded, onDismissRequest = { isAlphabetMenuExpanded = false }) { cmdTypes.forEach { option -> DropdownMenuItem(text = { Text(option, style = MONO_STYLE, fontSize = 24.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }, onClick = { onPart2Change(option); isAlphabetMenuExpanded = false }) } } }
            Spacer(modifier = Modifier.width(8.dp))
            Row(modifier = Modifier.weight(1.6f).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) { StyledBasicTextField(value = part3, onValueChange = { input -> val f = input.text.filter { it.isDigit() }; if (f.length <= 2) onPart3Change(input.copy(text = f)) }, modifier = Modifier.weight(1f).fillMaxHeight(), coroutineScope = coroutineScope, isDarkMode = isDarkMode); Spacer(modifier = Modifier.width(8.dp)); Column(modifier = Modifier.width(44.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) { Box(modifier = Modifier.fillMaxWidth().height(40.dp).background(if(isDarkMode) Color(0xFF424242) else Color(0xFFE0E0E0), RoundedCornerShape(4.dp)).clickable { onPart3Change(part3.copy(text = ((part3.text.toIntOrNull() ?: 0) + 1).coerceIn(0, 99).toString().padStart(2, '0'))) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, contentDescription = "Up", tint = if(isDarkMode) Color.White else Color.Black) }; Box(modifier = Modifier.fillMaxWidth().height(40.dp).background(if(isDarkMode) Color(0xFF424242) else Color(0xFFE0E0E0), RoundedCornerShape(4.dp)).clickable { onPart3Change(part3.copy(text = ((part3.text.toIntOrNull() ?: 0) - 1).coerceIn(0, 99).toString().padStart(2, '0'))) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Remove, contentDescription = "Down", tint = if(isDarkMode) Color.White else Color.Black) } } }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Surface(modifier = Modifier.fillMaxWidth().height(90.dp).combinedClickable(onClick = { if (isContinuousMode) { onContinuousToggled(false) } else if (tagStatus == TagStatus.WRITING) { onCancelWrite() } else { onWriteRequested(currentFullCode) } }, onLongClick = { if (!isContinuousMode) { onContinuousToggled(true); onWriteRequested(currentFullCode) } }), shape = RoundedCornerShape(8.dp), color = if (isContinuousMode || tagStatus == TagStatus.WRITING) (if(isDarkMode) Color(0xFFD84315) else Color(0xFFE64A19)) else (if(isDarkMode) Color(0xFF0D47A1) else MaterialTheme.colorScheme.primary)) { Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp).fillMaxSize()) { val btnText = when { isContinuousMode -> tr("m_stop", isKor); tagStatus == TagStatus.WRITING -> tr("m_cancel", isKor); else -> tr("m_write", isKor) }; Text(text = btnText, color = Color.White, fontSize = if (isContinuousMode || tagStatus == TagStatus.WRITING) 20.sp else 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) } }
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { presets.forEachIndexed { index, code -> Box(modifier = Modifier.weight(1f).aspectRatio(1f).background(if(isDarkMode) Color(0xFF424242) else Color(0xFFEEEEEE), RoundedCornerShape(4.dp)).pointerInput(code) { detectTapGestures(onTap = { updateInputParts(code) }, onLongPress = { val codeToSave = latestFullCode; if (codeToSave.length == 4) { coroutineScope.launch { context.dataStore.edit { it[presetKeys[index]] = codeToSave } }; Toast.makeText(context, "${tr("t_psav", isKor)} $codeToSave", Toast.LENGTH_SHORT).show() } }) }, contentAlignment = Alignment.Center) { Text(code, color = if(isDarkMode) Color.White else Color.Black, style = MONO_STYLE, fontSize = 18.sp, textAlign = TextAlign.Center) } } }
        if (showHistory) { Spacer(modifier = Modifier.height(28.dp)); HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp), color = if (isDarkMode) Color(0xFF444444) else Color.LightGray); Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(tr("m_hist", isKor), fontWeight = FontWeight.Bold, color = if(isDarkMode) Color(0xFF90CAF9) else Color.Blue, fontSize = 20.sp, modifier = Modifier.clickable { showHistoryDialog = true }); Text(text = tr("m_clr", isKor), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if(isDarkMode) Color(0xFFEF5350) else Color.Red, modifier = Modifier.clickable { showClearConfirmDialog = true }) }; Column(modifier = Modifier.fillMaxWidth()) { historyList.take(5).forEach { item -> HistoryItemRow(item, cmdTypes, isDarkMode, onClickCode = { updateInputParts(it) }) } } }
        Spacer(modifier = Modifier.height(20.dp))
    }
    if (showNfcDialog) AlertDialog(onDismissRequest = { showNfcDialog = false }, title = { Text(tr("d_noff", isKor), fontWeight = FontWeight.Bold, color = if(isDarkMode) Color.White else Color.Black) }, text = { Text(tr("d_ndis", isKor), color = if(isDarkMode) Color.LightGray else Color.DarkGray) }, confirmButton = { TextButton(onClick = { showNfcDialog = false; context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) { Text(tr("d_set", isKor)) } }, dismissButton = { TextButton(onClick = { showNfcDialog = false }) { Text(tr("d_cls", isKor)) } }, containerColor = if (isDarkMode) Color(0xFF424242) else MaterialTheme.colorScheme.surface)
}