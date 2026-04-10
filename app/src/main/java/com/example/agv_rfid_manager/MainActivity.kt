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
import android.view.WindowManager
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
val SHOW_HISTORY_KEY = booleanPreferencesKey("show_history")
val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
val PART1_KEY = stringPreferencesKey("part1")
val PART2_KEY = stringPreferencesKey("part2")
val PART3_KEY = stringPreferencesKey("part3")

enum class TagStatus { IDLE, WRITING, READ_SUCCESS, WRITE_SUCCESS, ERROR }

private val MONO_STYLE = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)

// --- 다국어 리소스 ---
fun tr(k: String, isKor: Boolean): String = Strings[k]?.get(if (isKor) 1 else 0) ?: k
val Strings = mapOf(
    "tab_main" to arrayOf("NFC MAIN", "NFC 메인"),
    "tab_guide" to arrayOf("Guide", "가이드"),
    "tab_set" to arrayOf("Settings", "설정"),
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
    "m_on" to arrayOf("🟢 NFC ON", "🟢 NFC 켜짐"),
    "m_off" to arrayOf("🔴 NFC OFF", "🔴 NFC 꺼짐"),
    "m_wait" to arrayOf("WAITING", "대기 중"),
    "m_succ" to arrayOf("SUCCESS", "성공"),
    "m_err" to arrayOf("ERROR", "오류"),
    "m_c_wait" to arrayOf("CONTINUOUS\nWAITING", "연속 쓰기\n대기 중"),
    "m_stop" to arrayOf("STOP CONTINUOUS", "연속 쓰기 종료"),
    "m_write" to arrayOf("WRITE\n(Continuous Mode)", "쓰기\n(연속 모드)"),
    "m_cancel" to arrayOf("CANCEL WRITE", "쓰기 취소"),
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

@Composable
fun MainApp(
    currentTag: String, tagStatus: TagStatus, targetCode: String, historyList: List<String>,
    isNfcEnabled: Boolean, isContinuousMode: Boolean, isKor: Boolean, isDarkMode: Boolean, cmdTypesStr: String, showHistory: Boolean,
    part1: TextFieldValue, onPart1Change: (TextFieldValue) -> Unit,
    part2: String, onPart2Change: (String) -> Unit,
    part3: TextFieldValue, onPart3Change: (TextFieldValue) -> Unit,
    onWriteRequested: (String) -> Unit, onContinuousToggled: (Boolean) -> Unit,
    onRevertToWriting: () -> Unit, onCancelWrite: () -> Unit, onTimeout: () -> Unit, onClearHistory: () -> Unit, onResetAll: () -> Unit
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
                0 -> AGVControlScreen(
                    currentTag, tagStatus, targetCode, historyList, isNfcEnabled, isContinuousMode, isKor, isDarkMode, cmdTypesStr.map { it.toString() }, showHistory,
                    part1, onPart1Change, part2, onPart2Change, part3, onPart3Change,
                    onWriteRequested, onContinuousToggled, onRevertToWriting, onCancelWrite, onTimeout, onClearHistory
                )
                1 -> GuideScreen(isKor, isDarkMode)
                2 -> AppSettingsScreen(isKor, isDarkMode, cmdTypesStr, onResetAll)
            }
        }
    }
}

@Composable
fun GuideScreen(isKor: Boolean, isDarkMode: Boolean) {
    var selectedGuideTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
        TabRow(selectedTabIndex = selectedGuideTab) {
            Tab(selected = selectedGuideTab == 0, onClick = { selectedGuideTab = 0 }) {
                Text(tr("g_tab_app", isKor), modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
            }
            Tab(selected = selectedGuideTab == 1, onClick = { selectedGuideTab = 1 }) {
                Text(tr("g_tab_err", isKor), modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
            }
            Tab(selected = selectedGuideTab == 2, onClick = { selectedGuideTab = 2 }) {
                Text(tr("g_tab_param", isKor), modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp)) {
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
    val cardBg = if (isDarkMode) Color(0xFF333333) else Color(0xFFF5F5F5)
    val errColor = if (isDarkMode) Color(0xFFEF5350) else Color.Red
    val textColor = if (isDarkMode) Color.White else Color.Black

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(AGV_ERRORS) { err ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { selectedError = err },
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(err.code, fontWeight = FontWeight.Bold, color = errColor, fontSize = 20.sp, modifier = Modifier.width(80.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(if (isKor) err.nameKor else err.nameEng, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor)
                }
            }
        }
    }

    selectedError?.let { err ->
        AlertDialog(
            onDismissRequest = { selectedError = null },
            title = { Text("${err.code}: ${if (isKor) err.nameKor else err.nameEng}", fontWeight = FontWeight.Bold, color = textColor) },
            text = {
                val actions = (if (isKor) err.actionKor else err.actionEng).split("\n")
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    actions.forEach { action ->
                        Text(action, fontSize = 16.sp, lineHeight = 24.sp, color = if (isDarkMode) Color.LightGray else Color.DarkGray)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedError = null }) { Text(tr("d_cls", isKor)) }
            },
            containerColor = if (isDarkMode) Color(0xFF424242) else MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun ParamGuideContent(isKor: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(tr("g_tba", isKor), fontSize = 18.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GuideCard(icon: ImageVector, title: String, desc: String, isDarkMode: Boolean) {
    val cardBg = if (isDarkMode) Color(0xFF333333) else Color(0xFFF5F5F5)
    val titleColor = if (isDarkMode) Color.White else Color.Black
    val descColor = if (isDarkMode) Color.LightGray else Color.DarkGray
    val iconTint = if (isDarkMode) Color(0xFF64B5F6) else Color(0xFF1976D2)

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardBg)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = title, modifier = Modifier.size(48.dp), tint = iconTint)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = titleColor)
                Text(desc, fontSize = 14.sp, color = descColor)
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

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text(tr("s_clr_title", isKor), fontWeight = FontWeight.Bold) },
            text = { Text(tr("s_clr_desc", isKor)) },
            confirmButton = {
                TextButton(onClick = { onResetAll(); showClearConfirmDialog = false }) { Text(tr("m_clr", isKor), color = Color.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirmDialog = false }) { Text(tr("d_cls", isKor)) } },
            containerColor = if (isDarkMode) Color(0xFF424242) else MaterialTheme.colorScheme.surface
        )
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 16.dp)) {
        Text(tr("s_title", isKor), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black)
        Spacer(modifier = Modifier.height(16.dp))

        Text(tr("s_pref", isKor), color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        ListItem(
            headlineContent = { Text(tr("s_theme", isKor), color = if (isDarkMode) Color.White else Color.Black) },
            supportingContent = { Text(tr("s_theme_d", isKor), color = if (isDarkMode) Color.LightGray else Color.DarkGray) },
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { coroutineScope.launch { context.RFIDStore.edit { it[DARK_MODE_KEY] = false } } },
                        colors = ButtonDefaults.buttonColors(containerColor = if (!isDarkMode) Color(0xFF1976D2) else Color(0xFF555555)),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) { Text("LIGHT", fontWeight = FontWeight.Bold, color = if (!isDarkMode) Color.White else Color.LightGray) }
                    Button(
                        onClick = { coroutineScope.launch { context.RFIDStore.edit { it[DARK_MODE_KEY] = true } } },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isDarkMode) Color(0xFF1976D2) else Color.LightGray),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) { Text("DARK", fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.DarkGray) }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        ListItem(
            headlineContent = { Text(tr("s_lang", isKor), color = if (isDarkMode) Color.White else Color.Black) },
            supportingContent = { Text(tr("s_lang_d", isKor), color = if (isDarkMode) Color.LightGray else Color.DarkGray) },
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { coroutineScope.launch { context.RFIDStore.edit { it[IS_KOR_KEY] = false } } },
                        colors = ButtonDefaults.buttonColors(containerColor = if (!isKor) Color(0xFF1976D2) else (if (isDarkMode) Color(0xFF555555) else Color.LightGray)),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) { Text("ENG", fontWeight = FontWeight.Bold, color = if (!isKor) Color.White else (if (isDarkMode) Color.LightGray else Color.DarkGray)) }
                    Button(
                        onClick = { coroutineScope.launch { context.RFIDStore.edit { it[IS_KOR_KEY] = true } } },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isKor) Color(0xFF1976D2) else (if (isDarkMode) Color(0xFF555555) else Color.LightGray)),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) { Text("KOR", fontWeight = FontWeight.Bold, color = if (isKor) Color.White else (if (isDarkMode) Color.LightGray else Color.DarkGray)) }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        ListItem(
            headlineContent = { Text(tr("s_cmd", isKor), color = if (isDarkMode) Color.White else Color.Black) },
            supportingContent = { Text(tr("s_cmd_d", isKor), color = if (isDarkMode) Color.LightGray else Color.DarkGray) },
            trailingContent = {
                BasicTextField(
                    value = tempCmd,
                    onValueChange = { input ->
                        val f = input.filter { it.isLetter() }.uppercase().toSet().joinToString("")
                        tempCmd = f
                        coroutineScope.launch { context.RFIDStore.edit { it[CMD_TYPES_KEY] = f.ifEmpty { "T" } } }
                    },
                    modifier = Modifier.width(160.dp).background(if (isDarkMode) Color(0xFF424242) else Color(0xFFEEEEEE), RoundedCornerShape(4.dp)).padding(8.dp),
                    singleLine = true, textStyle = TextStyle(textAlign = TextAlign.Start, fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black)
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        ListItem(
            headlineContent = { Text(tr("s_hist_view", isKor), color = if (isDarkMode) Color.White else Color.Black) },
            supportingContent = { Text(tr("s_hist_view_d", isKor), color = if (isDarkMode) Color.LightGray else Color.DarkGray) },
            trailingContent = { Switch(checked = showHistoryEnabled, onCheckedChange = { coroutineScope.launch { context.RFIDStore.edit { prefs -> prefs[SHOW_HISTORY_KEY] = it } } }) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        ListItem(
            headlineContent = { Text(tr("s_vib", isKor), color = if (isDarkMode) Color.White else Color.Black) },
            supportingContent = { Text(tr("s_vib_d", isKor), color = if (isDarkMode) Color.LightGray else Color.DarkGray) },
            trailingContent = { Switch(checked = vibEnabled, onCheckedChange = { coroutineScope.launch { context.RFIDStore.edit { prefs -> prefs[VIB_KEY] = it } } }) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        ListItem(
            headlineContent = { Text(tr("s_snd", isKor), color = if (isDarkMode) Color.White else Color.Black) },
            supportingContent = { Text(tr("s_snd_d", isKor), color = if (isDarkMode) Color.LightGray else Color.DarkGray) },
            trailingContent = { Switch(checked = soundEnabled, onCheckedChange = { coroutineScope.launch { context.RFIDStore.edit { prefs -> prefs[SOUND_KEY] = it } } }) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = if (isDarkMode) Color(0xFF444444) else Color.LightGray)

        Text(tr("s_nfc", isKor), color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        ListItem(
            headlineContent = { Text(tr("s_verify", isKor), color = if (isDarkMode) Color.White else Color.Black) },
            supportingContent = { Text(tr("s_verify_d", isKor), color = if (isDarkMode) Color.LightGray else Color.DarkGray) },
            trailingContent = { Switch(checked = autoVerifyEnabled, onCheckedChange = { coroutineScope.launch { context.RFIDStore.edit { prefs -> prefs[AUTO_VERIFY_KEY] = it } } }) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = if (isDarkMode) Color(0xFF444444) else Color.LightGray)

        Text(tr("s_data", isKor), color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        ListItem(
            headlineContent = { Text(tr("s_clear", isKor), color = if (isDarkMode) Color.White else Color.Black) },
            supportingContent = { Text(tr("s_clear_d", isKor), color = if (isDarkMode) Color.LightGray else Color.DarkGray) },
            trailingContent = { Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color.Red, modifier = Modifier.size(48.dp)) },
            modifier = Modifier.clickable { showClearConfirmDialog = true },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
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
    private var showHistory = mutableStateOf(true)
    private var isDarkMode = mutableStateOf(false)

    // 유지 상태 변수 (앱을 종료하거나 탭을 넘겨도 값 보존)
    private var part1State = mutableStateOf(TextFieldValue("0"))
    private var part2State = mutableStateOf("T")
    private var part3State = mutableStateOf(TextFieldValue("00"))

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

        val updatePart1: (TextFieldValue) -> Unit = { v ->
            part1State.value = v
            CoroutineScope(Dispatchers.IO).launch { RFIDStore.edit { it[PART1_KEY] = v.text } }
        }
        val updatePart2: (String) -> Unit = { v ->
            part2State.value = v
            CoroutineScope(Dispatchers.IO).launch { RFIDStore.edit { it[PART2_KEY] = v } }
        }
        val updatePart3: (TextFieldValue) -> Unit = { v ->
            part3State.value = v
            CoroutineScope(Dispatchers.IO).launch { RFIDStore.edit { it[PART3_KEY] = v.text } }
        }

        val onCancelWrite: () -> Unit = {
            tagStatus.value = TagStatus.IDLE
            currentTagText.value = "READY"
        }

        setContent {
            val colorScheme = if (isDarkMode.value) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainApp(
                        currentTag = currentTagText.value, tagStatus = tagStatus.value, targetCode = targetWriteCode.value,
                        historyList = historyList, isNfcEnabled = isNfcEnabled.value, isContinuousMode = isContinuousMode.value,
                        isKor = isKor.value, isDarkMode = isDarkMode.value, cmdTypesStr = cmdTypesStr.value, showHistory = showHistory.value,
                        part1 = part1State.value, onPart1Change = updatePart1,
                        part2 = part2State.value, onPart2Change = updatePart2,
                        part3 = part3State.value, onPart3Change = updatePart3,
                        onWriteRequested = { code -> targetWriteCode.value = code; tagStatus.value = TagStatus.WRITING },
                        onContinuousToggled = { enabled ->
                            isContinuousMode.value = enabled
                            if (!enabled) { tagStatus.value = TagStatus.IDLE; currentTagText.value = tr("m_wait", isKor.value) }
                        },
                        onRevertToWriting = { tagStatus.value = TagStatus.WRITING },
                        onCancelWrite = onCancelWrite,
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

@Composable
fun StyledBasicTextField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, modifier: Modifier, coroutineScope: CoroutineScope, isDarkMode: Boolean, keyboardType: KeyboardType = KeyboardType.NumberPassword) {
    val bg = if (isDarkMode) Color(0xFF424242) else Color(0xFFF5F5F5)
    val textCol = if (isDarkMode) Color.White else Color.Black

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged { focusState ->
            if (focusState.isFocused) {
                coroutineScope.launch { delay(100); onValueChange(value.copy(selection = TextRange(0, value.text.length))) }
            }
        },
        textStyle = MONO_STYLE.copy(fontSize = 28.sp, textAlign = TextAlign.Center, color = textCol),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        cursorBrush = SolidColor(textCol),
        decorationBox = { innerTextField -> Box(modifier = Modifier.fillMaxSize().background(bg, RoundedCornerShape(4.dp)).border(1.dp, if(isDarkMode) Color.DarkGray else Color.Gray, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) { innerTextField() } }
    )
}

@Composable
fun HistoryItemRow(item: String, cmdTypes: List<String>, isDarkMode: Boolean, onClickCode: (String) -> Unit) {
    val isError = item.contains("ERR") || item.contains("TIMEOUT") || item.contains("오류") || item.contains("초과")
    val isSuccess = item.contains("OK") || item.contains("Data")
    val isClickable = isError || isSuccess

    val textColor = when {
        isError -> if (isDarkMode) Color(0xFFEF5350) else Color.Red
        isSuccess -> if (isDarkMode) Color(0xFF66BB6A) else Color(0xFF388E3C)
        else -> if (isDarkMode) Color.LightGray else Color.DarkGray
    }

    Text(
        text = item,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        style = MONO_STYLE,
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isClickable) {
                val ex = item.split(" ").lastOrNull()?.trim() ?: ""
                if (ex.length >= 4) {
                    val code = ex.takeLast(4)
                    if (code[1].toString() in cmdTypes) onClickCode(code)
                }
            }
            .padding(vertical = 4.dp),
        color = textColor
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AGVControlScreen(
    currentTag: String, tagStatus: TagStatus, targetCode: String, historyList: List<String>,
    isNfcEnabled: Boolean, isContinuousMode: Boolean, isKor: Boolean, isDarkMode: Boolean, cmdTypes: List<String>, showHistory: Boolean,
    part1: TextFieldValue, onPart1Change: (TextFieldValue) -> Unit,
    part2: String, onPart2Change: (String) -> Unit,
    part3: TextFieldValue, onPart3Change: (TextFieldValue) -> Unit,
    onWriteRequested: (String) -> Unit, onContinuousToggled: (Boolean) -> Unit,
    onRevertToWriting: () -> Unit, onCancelWrite: () -> Unit, onTimeout: () -> Unit, onClearHistory: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var isAlphabetMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(cmdTypes) {
        if (part2 !in cmdTypes && cmdTypes.isNotEmpty()) onPart2Change(cmdTypes.first())
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
            onPart1Change(TextFieldValue(code[0].toString()))
            onPart2Change(code[1].toString())
            onPart3Change(TextFieldValue(code.substring(2, 4)))
            focusManager.clearFocus()
        }
    }

    val cardBgColor = when {
        isContinuousMode && tagStatus == TagStatus.WRITE_SUCCESS -> if (isDarkMode) Color(0xFF0D47A1) else Color(0xFFBBDEFB)
        isContinuousMode && tagStatus == TagStatus.ERROR -> if (isDarkMode) Color(0xFFB71C1C) else Color(0xFFFFCDD2)
        isContinuousMode -> if (isDarkMode) Color(0xFFE65100) else Color(0xFFFFCC80)
        tagStatus == TagStatus.WRITING -> if (isDarkMode) Color(0xFFF57F17) else Color(0xFFFFEB3B)
        tagStatus == TagStatus.READ_SUCCESS -> if (isDarkMode) Color(0xFF1B5E20) else Color(0xFFC8E6C9)
        tagStatus == TagStatus.WRITE_SUCCESS -> if (isDarkMode) Color(0xFF0D47A1) else Color(0xFFBBDEFB)
        tagStatus == TagStatus.ERROR -> if (isDarkMode) Color(0xFFB71C1C) else Color(0xFFFFCDD2)
        else -> if (isDarkMode) Color(0xFF1E3A8A) else Color(0xFFE3F2FD)
    }

    val cardTextColor = if (isDarkMode) Color.White else Color.Black

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
            title = { Text(tr("d_clr_title", isKor), fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black) },
            text = { Text(tr("d_clr_desc", isKor), color = if (isDarkMode) Color.LightGray else Color.DarkGray) },
            confirmButton = {
                TextButton(onClick = {
                    onClearHistory()
                    showClearConfirmDialog = false
                    showHistoryDialog = false
                }) { Text(tr("m_clr", isKor), color = if (isDarkMode) Color(0xFFEF5350) else Color.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirmDialog = false }) { Text(tr("d_cls", isKor)) } },
            containerColor = if (isDarkMode) Color(0xFF424242) else MaterialTheme.colorScheme.surface
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text(tr("g_guide", isKor), fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item { Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) { Text(tr("g_tag", isKor), fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.LightGray else Color.DarkGray, modifier = Modifier.weight(1f)); Text(tr("g_func", isKor), fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.LightGray else Color.DarkGray, modifier = Modifier.weight(2f)) }; HorizontalDivider(color = if(isDarkMode) Color.DarkGray else Color.LightGray) }
                    items(getCommands(isKor)) { (tag, desc) ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { updateInputParts(tag); showHelpDialog = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(tag, style = MONO_STYLE, fontSize = 28.sp, color = if (isDarkMode) Color.White else Color.Black, modifier = Modifier.weight(1f))
                            Text(desc, fontSize = 28.sp, color = if (isDarkMode) Color.White else Color.Black, modifier = Modifier.weight(2f), lineHeight = 32.sp)
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = if (isDarkMode) Color.DarkGray else Color.LightGray)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHelpDialog = false }) { Text(tr("d_cls", isKor)) } },
            containerColor = if (isDarkMode) Color(0xFF424242) else MaterialTheme.colorScheme.surface
        )
    }

    if (showHistoryDialog) {
        Dialog(onDismissRequest = { showHistoryDialog = false }) {
            Surface(shape = RoundedCornerShape(12.dp), color = if (isDarkMode) Color(0xFF424242) else MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("l_title", isKor), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black)
                        Text(text = tr("l_call", isKor), color = if (isDarkMode) Color(0xFFEF5350) else Color.Red, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { showClearConfirmDialog = true })
                    }
                    if (historyList.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tr("l_empty", isKor), color = Color.Gray) }
                    else LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(historyList) { log ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF333333) else Color(0xFFF5F5F5))) {
                                Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                                    HistoryItemRow(log, cmdTypes, isDarkMode) { code ->
                                        updateInputParts(code)
                                        showHistoryDialog = false
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 32.dp, vertical = 20.dp).pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = if (isNfcEnabled) tr("m_on", isKor) else tr("m_off", isKor), fontWeight = FontWeight.Bold, color = if (isNfcEnabled) (if(isDarkMode) Color(0xFF66BB6A) else Color(0xFF388E3C)) else (if(isDarkMode) Color(0xFFEF5350) else Color.Red), style = MONO_STYLE, fontSize = 20.sp, maxLines = 1, softWrap = false)
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Default.HelpOutline, contentDescription = "Help", tint = Color.Gray, modifier = Modifier.size(36.dp).clickable { showHelpDialog = true })
            }
        }

        Card(modifier = Modifier.fillMaxWidth().height(270.dp).clickable(enabled = tagStatus == TagStatus.READ_SUCCESS || tagStatus == TagStatus.WRITE_SUCCESS) { updateInputParts(currentTag) }, colors = CardDefaults.cardColors(containerColor = cardBgColor)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(16.dp)) { Text(text = displayText, color = cardTextColor, fontSize = if (displayText.contains("\n")) 40.sp else 80.sp, style = MONO_STYLE, textAlign = TextAlign.Center, lineHeight = if (displayText.contains("\n")) 48.sp else 80.sp) }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth().height(90.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            StyledBasicTextField(
                value = part1,
                onValueChange = { input -> val f = input.text.filter { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }.uppercase(); onPart1Change(input.copy(text = if (f.isNotEmpty()) f.last().toString() else "", selection = TextRange(if (f.isNotEmpty()) 1 else 0))) },
                modifier = Modifier.weight(1f).fillMaxHeight(), coroutineScope = coroutineScope, isDarkMode = isDarkMode, keyboardType = KeyboardType.Text
            )
            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().border(1.dp, if(isDarkMode) Color.DarkGray else Color.Gray, RoundedCornerShape(4.dp)).background(if(isDarkMode) Color(0xFF424242) else Color(0xFFF5F5F5), RoundedCornerShape(4.dp)).clickable { focusManager.clearFocus(); coroutineScope.launch { delay(50); isAlphabetMenuExpanded = true } },
                contentAlignment = Alignment.Center
            ) {
                Text(text = part2, style = MONO_STYLE, fontSize = 32.sp, color = if(isDarkMode) Color(0xFF64B5F6) else Color(0xFF1976D2), textAlign = TextAlign.Center)
                DropdownMenu(expanded = isAlphabetMenuExpanded, onDismissRequest = { isAlphabetMenuExpanded = false }) {
                    cmdTypes.forEach { option -> DropdownMenuItem(text = { Text(option, style = MONO_STYLE, fontSize = 24.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }, onClick = { onPart2Change(option); isAlphabetMenuExpanded = false }) }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(modifier = Modifier.weight(1.6f).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                StyledBasicTextField(value = part3, onValueChange = { input -> val f = input.text.filter { it.isDigit() }; if (f.length <= 2) onPart3Change(input.copy(text = f)) }, modifier = Modifier.weight(1f).fillMaxHeight(), coroutineScope = coroutineScope, isDarkMode = isDarkMode)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.width(44.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                    Box(modifier = Modifier.fillMaxWidth().height(40.dp).background(if(isDarkMode) Color(0xFF424242) else Color(0xFFE0E0E0), RoundedCornerShape(4.dp)).clickable { onPart3Change(part3.copy(text = ((part3.text.toIntOrNull() ?: 0) + 1).coerceIn(0, 99).toString().padStart(2, '0'))) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, contentDescription = "Up", tint = if(isDarkMode) Color.White else Color.Black) }
                    Box(modifier = Modifier.fillMaxWidth().height(40.dp).background(if(isDarkMode) Color(0xFF424242) else Color(0xFFE0E0E0), RoundedCornerShape(4.dp)).clickable { onPart3Change(part3.copy(text = ((part3.text.toIntOrNull() ?: 0) - 1).coerceIn(0, 99).toString().padStart(2, '0'))) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Remove, contentDescription = "Down", tint = if(isDarkMode) Color.White else Color.Black) }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().height(90.dp).combinedClickable(
                onClick = {
                    if (isContinuousMode) {
                        onContinuousToggled(false)
                    } else if (tagStatus == TagStatus.WRITING) {
                        onCancelWrite()
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
            color = if (isContinuousMode || tagStatus == TagStatus.WRITING) (if(isDarkMode) Color(0xFFD84315) else Color(0xFFE64A19)) else (if(isDarkMode) Color(0xFF0D47A1) else MaterialTheme.colorScheme.primary)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp).fillMaxSize()) {
                val btnText = when {
                    isContinuousMode -> tr("m_stop", isKor)
                    tagStatus == TagStatus.WRITING -> tr("m_cancel", isKor)
                    else -> tr("m_write", isKor)
                }
                Text(text = btnText, color = Color.White, fontSize = if (isContinuousMode || tagStatus == TagStatus.WRITING) 20.sp else 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEachIndexed { index, code ->
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).background(if(isDarkMode) Color(0xFF424242) else Color(0xFFEEEEEE), RoundedCornerShape(4.dp)).pointerInput(code) { detectTapGestures(onTap = { updateInputParts(code) }, onLongPress = { if (currentFullCode.length == 4) { coroutineScope.launch { context.dataStore.edit { it[presetKeys[index]] = currentFullCode } }; Toast.makeText(context, "${tr("t_psav", isKor)} $currentFullCode", Toast.LENGTH_SHORT).show() } }) }, contentAlignment = Alignment.Center) { Text(code, color = if(isDarkMode) Color.White else Color.Black, style = MONO_STYLE, fontSize = 18.sp, textAlign = TextAlign.Center) }
            }
        }

        if (showHistory) {
            Spacer(modifier = Modifier.height(28.dp))
            HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp), color = if (isDarkMode) Color(0xFF444444) else Color.LightGray)

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("m_hist", isKor), fontWeight = FontWeight.Bold, color = if(isDarkMode) Color(0xFF90CAF9) else Color.Blue, fontSize = 20.sp, modifier = Modifier.clickable { showHistoryDialog = true })
                Text(text = tr("m_clr", isKor), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if(isDarkMode) Color(0xFFEF5350) else Color.Red, modifier = Modifier.clickable { showClearConfirmDialog = true })
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                historyList.take(5).forEach { item ->
                    HistoryItemRow(item, cmdTypes, isDarkMode, onClickCode = { updateInputParts(it) })
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }

    if (showNfcDialog) {
        AlertDialog(
            onDismissRequest = { showNfcDialog = false }, title = { Text(tr("d_noff", isKor), fontWeight = FontWeight.Bold, color = if(isDarkMode) Color.White else Color.Black) }, text = { Text(tr("d_ndis", isKor), color = if(isDarkMode) Color.LightGray else Color.DarkGray) },
            confirmButton = { TextButton(onClick = { showNfcDialog = false; context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) { Text(tr("d_set", isKor)) } },
            dismissButton = { TextButton(onClick = { showNfcDialog = false }) { Text(tr("d_cls", isKor)) } },
            containerColor = if (isDarkMode) Color(0xFF424242) else MaterialTheme.colorScheme.surface
        )
    }
}