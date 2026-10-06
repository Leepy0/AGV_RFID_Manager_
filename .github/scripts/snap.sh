#!/bin/bash
# 에뮬레이터에서 주요 화면 캡처 (uiautomator로 글자 위치를 찾아 탭)
set -x
mkdir -p snaps
PKG=com.example.agv_rfid_manager

shot() { sleep "${2:-2}"; adb exec-out screencap -p > "snaps/$1.png"; }

tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml > /dev/null 2>&1
  POS=$(python3 - "$1" <<'PY'
import sys, re
xml = open('ui.xml', encoding='utf-8').read()
target = sys.argv[1]
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n)
    d = re.search(r' content-desc="([^"]*)"', n)
    if (t and t.group(1) == target) or (d and d.group(1) == target):
        b = list(map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups()))
        print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
        break
PY
)
  if [ -n "$POS" ]; then adb shell input tap $POS; else echo "NOT FOUND: $1"; fi
}

adb install -r "$(ls app/build/outputs/apk/debug/*.apk | head -1)"
adb shell am start -n $PKG/.MainActivity
shot 01_start_nfc_dialog 8
adb shell input keyevent KEYCODE_BACK
shot 02_tag_idle

tap_text "쓰기"
shot 03_tag_writing 1
shot 04_tag_timeout 6

tap_text "목록"
shot 05_code_sheet
tap_text "우회전 90도"
shot 06_after_pick

tap_text "연속 쓰기"
shot 07_cont_selected
tap_text "연속 쓰기 시작"
shot 08_cont_running 1
tap_text "연속 쓰기 종료"
tap_text "1회 쓰기"

tap_text "전체"
shot 09_history_sheet
adb shell input keyevent KEYCODE_BACK

tap_text "편집"
shot 10_preset_edit
tap_text "완료"

tap_text "가이드"
shot 11_guide
tap_text "에러 코드"
shot 12_guide_errors
tap_text "E257"
shot 13_error_dialog
adb shell input keyevent KEYCODE_BACK

tap_text "설정"
shot 14_settings
tap_text "테마"
shot 15_theme_dialog
tap_text "라이트"
shot 16_settings_light
tap_text "태그"
shot 17_tag_light

tap_text "설정"
tap_text "화면 효과"
shot 18_fx_dialog
tap_text "저사양"
tap_text "태그"
shot 19_tag_lowfx
tap_text "설정"
tap_text "화면 효과"
tap_text "자동"
tap_text "테마"
tap_text "다크"

# 폴드 펼침 크기 흉내 (2단 배치)
adb shell wm size 2176x1812
adb shell wm density 420
tap_text "태그"
shot 20_fold_twopane 5
adb shell wm size reset
adb shell wm density reset

adb logcat -d -s AndroidRuntime:E > snaps/crash_logcat.txt || true
