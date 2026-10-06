package com.example.agv_rfid_manager.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue

enum class TagStatus { IDLE, WRITING, READ_SUCCESS, WRITE_SUCCESS, ERROR }

// 태그 화면 상태 (MainActivity가 소유하고 NFC 콜백에서 갱신)
class TagState {
    var status by mutableStateOf(TagStatus.IDLE)
    var currentTag by mutableStateOf("")      // 마지막으로 읽거나 쓴 코드
    var targetCode by mutableStateOf("")      // 쓰기 대상 코드
    var prevCode by mutableStateOf("")        // 쓰기 전 기존값 (되돌리기용)
    var detail by mutableStateOf("")          // 부제: 변경 내역·오류 사유
    var errorTitle by mutableStateOf("")      // 오류 제목
    var eventTime by mutableStateOf("")       // 읽기/쓰기 시각 (HH:mm)
    var isContinuous by mutableStateOf(false) // 연속 쓰기 진행 중
    var contSelected by mutableStateOf(false) // 액션 바에서 연속 쓰기 모드 선택
    var nfcEnabled by mutableStateOf(false)
    var part1 by mutableStateOf(TextFieldValue("0"))
    var part2 by mutableStateOf("T")
    var part3 by mutableStateOf(TextFieldValue("00"))
    val history = mutableStateListOf<String>()

    val fullCode: String get() = "${part1.text}$part2${part3.text.padStart(2, '0')}"
}

// 설정값 (RFIDStore를 구독해 갱신)
class SettingsState {
    var vib by mutableStateOf(true)
    var sound by mutableStateOf(true)
    var autoVerify by mutableStateOf(true)
    var isKor by mutableStateOf(true)
    var cmdTypes by mutableStateOf("TIOBD")
    var showHistory by mutableStateOf(true)
    var themeMode by mutableStateOf(ThemeMode.DARK)
    var perfMode by mutableStateOf(PerfSetting.AUTO)
}

// 태그 화면에서 호출하는 동작
interface TagActions {
    fun setPart1(v: TextFieldValue)
    fun setPart2(v: String)
    fun setPart3(v: TextFieldValue)
    fun requestWrite(code: String)
    fun setContinuous(on: Boolean)
    fun revertToWriting()
    fun cancelWrite()
    fun timeout()
    fun clearHistory()
}

// 4자리 코드를 입력칸 3개로 나눠 넣기
fun loadCode(code: String, actions: TagActions) {
    if (code.length >= 4) {
        actions.setPart1(TextFieldValue(code[0].toString()))
        actions.setPart2(code[1].toString())
        actions.setPart3(TextFieldValue(code.substring(2, 4)))
    }
}
