package com.example.agv_rfid_manager.data

// 공통 태그 코드와 뜻
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

// 코드 뜻 (목록에 없는 코드는 빈 문자열)
fun commandDesc(code: String, isKor: Boolean): String =
    getCommands(isKor).firstOrNull { it.first == code }?.second ?: ""

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

// ---------- 작업 이력 파싱 (저장 형식은 기존과 동일하게 유지) ----------
enum class HistKind { READ, WRITE, VERIFY_ERR, TIMEOUT, SYSTEM, OTHER }

data class HistItem(val time: String, val kind: HistKind, val body: String, val code: String?)

private val HIST_RE = Regex("""^\[([^\]]*)\]\s*\[([^\]]*)\]\s*(.*)$""")
val CODE_RE = Regex("[0-9A-F][A-Z][0-9]{2}")

// 예) "[14:31:02] [WRITE+VERIFY OK] : 0T05 → 0T21", "[14:30:11] [READ] Data : 0T08", "[14:28:40] [시간 초과] 0T22"
fun parseHistory(raw: String): HistItem {
    val m = HIST_RE.find(raw) ?: return HistItem("", HistKind.OTHER, raw, null)
    val time = m.groupValues[1]
    val tag = m.groupValues[2]
    val body = m.groupValues[3].trim().removePrefix("Data").trim().removePrefix(":").trim()
    val kind = when {
        tag == "SYSTEM" -> HistKind.SYSTEM
        tag == "READ" -> HistKind.READ
        tag.startsWith("WRITE") -> HistKind.WRITE
        tag.contains("VERIFY") -> HistKind.VERIFY_ERR
        tag.contains("TIMEOUT") || tag.contains("초과") -> HistKind.TIMEOUT
        else -> HistKind.OTHER
    }
    val tokens = body.split(" ").filter { it.isNotBlank() }
    val candidate = if (kind == HistKind.VERIFY_ERR) tokens.firstOrNull() else tokens.lastOrNull()
    val code = candidate?.takeIf { CODE_RE.matches(it) }
    return HistItem(time, kind, body, code)
}
