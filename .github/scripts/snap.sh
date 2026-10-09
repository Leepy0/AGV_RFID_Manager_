#!/bin/bash
# 에뮬레이터에서 주요 화면 캡처 (uiautomator로 글자 위치를 찾아 탭)
set -x
mkdir -p snaps
PKG=com.example.agv_rfid_manager

dump() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml > /dev/null 2>&1
}

find_text() {
  python3 - "$1" <<'PY'
import sys, re
try:
    xml = open('ui.xml', encoding='utf-8').read()
except Exception:
    sys.exit(0)
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
}

# 글자가 나타날 때까지 기다림 (최대 $2초)
wait_text() {
  for i in $(seq 1 "${2:-30}"); do
    dump
    [ -n "$(find_text "$1")" ] && return 0
    sleep 1
  done
  echo "TIMEOUT waiting: $1"
  return 1
}

tap_text() {
  dump
  POS=$(find_text "$1")
  if [ -n "$POS" ]; then adb shell input tap $POS; sleep 1.5; else echo "NOT FOUND: $1"; fi
}

# 길게 누르기 (같은 좌표로 swipe 700ms)
long_text() {
  dump
  POS=$(find_text "$1")
  if [ -n "$POS" ]; then adb shell input swipe $POS $POS 700; sleep 1.5; else echo "NOT FOUND: $1"; fi
}

shot() { sleep "${2:-1.5}"; adb exec-out screencap -p > "snaps/$1.png"; }

# 화면 어딘가에 글자가 포함돼 있는지 (부분 일치, 최대 $2초)
wait_contains() {
  for i in $(seq 1 "${2:-30}"); do
    dump
    grep -q -- "$1" ui.xml 2>/dev/null && return 0
    sleep 1
  done
  echo "TIMEOUT waiting(contains): $1"
  return 1
}

# ---------- 시스템 언어를 한국어로 (설치 화면·설정 화면을 매뉴얼용으로 캡처) ----------
adb root >/dev/null 2>&1 || true
sleep 3; adb wait-for-device
adb shell "setprop persist.sys.locale ko-KR; setprop ctl.restart zygote" || true
sleep 8
for i in $(seq 1 45); do
  adb shell dumpsys window 2>/dev/null | grep mCurrentFocus | grep -qi launcher && break
  sleep 2
done
sleep 3
echo "locale: $(adb shell getprop persist.sys.locale)" > snaps/install_result.txt

# ---------- 처음 설치 흐름: 파일 앱에서 APK를 눌러 설치 (출처 허용 → 설치 확인 → 완료) ----------
APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
APK_NAME=$(basename "$APK")
adb push "$APK" "/sdcard/Download/$APK_NAME"
adb shell am start -a android.intent.action.VIEW -d "content://com.android.externalstorage.documents/document/primary%3ADownload" -t vnd.android.document/directory
sleep 4
shot 30_files_download 0.5
tap_text "$APK_NAME"
# 기종·버전에 따라 나오는 창이 다르므로, 보이는 창에 맞춰 차례로 진행 (최대 90초)
DONE_INSTALL=""
for i in $(seq 1 45); do
  dump
  if grep -q "설치되었습니다" ui.xml; then
    shot 35_install_done 0.5           # "앱이 설치되었습니다" (완료 / 열기)
    tap_text "완료"; DONE_INSTALL=1; break
  elif grep -q "설치하시겠습니까" ui.xml; then
    shot 34_install_confirm 0.5        # "이 애플리케이션을 설치하시겠습니까?"
    tap_text "설치"; sleep 3
  elif [ -n "$(find_text "계속")" ]; then
    shot 31_install_consent 0.5        # "알 수 없는 앱의 공격에 더욱 취약합니다 … 계속" (Android 10 계열)
    tap_text "계속"
  elif grep -q "알 수 없는 앱" ui.xml && [ -n "$(find_text "설정")" ]; then
    shot 31_install_blocked 0.5        # "보안상의 이유로 … 설치할 수 없도록 설정" → 설정
    tap_text "설정"; sleep 2
    shot 32_allow_source 0.5           # 설정: 이 출처 허용 (꺼짐)
    tap_text "이 출처 허용"
    shot 33_allow_source_on 0.5        # 켜짐
    adb shell input keyevent KEYCODE_BACK; sleep 2
  else
    sleep 2
  fi
done
echo "ui install: $(adb shell dumpsys package $PKG | grep -m1 versionCode | tr -s ' ')" >> snaps/install_result.txt

# 파일 앱 설치가 실패했을 때 대비 (이미 설치돼 있으면 같은 버전 재설치)
adb install -r "$APK"
# 앱 서랍에서 아이콘 찾기 화면
adb shell input keyevent KEYCODE_HOME; sleep 1.5
adb shell input swipe 540 2100 540 500 400; sleep 2
shot 36_app_drawer 0.5
adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell am start -n $PKG/.MainActivity
wait_text "태그" 60
# 시작 시 업데이트 안내 (Release가 갱신 중이면 안 뜰 수 있음)
if wait_text "업데이트가 있어요" 12; then
  shot 01_update_dialog 0.5
  tap_text "나중에"
else
  shot 01_start 0.5
fi
shot 02_tag_idle 0.5

tap_text "쓰기"
shot 03_tag_writing 0.5
shot 04_tag_timeout 6

tap_text "목록"
shot 05_code_sheet
tap_text "우회전 90도"
shot 06_after_pick

long_text "쓰기"
shot 08_cont_running 0.5
tap_text "연속 쓰기 종료"

tap_text "전체"
shot 09_history_sheet
adb shell input keyevent KEYCODE_BACK
sleep 1

tap_text "편집"
shot 10_preset_edit
tap_text "완료"

tap_text "가이드"
shot 11_guide
tap_text "에러 코드"
shot 12_guide_errors
tap_text "E257"
shot 13_error_dialog
tap_text "닫기"

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
tap_text "전체 효과"
tap_text "태그"
shot 19_tag_light_fullfx
tap_text "설정"
tap_text "테마"
tap_text "다크"
tap_text "태그"
shot 20_tag_dark_fullfx
tap_text "설정"
tap_text "화면 효과"
tap_text "자동"

# 폴드 펼침 크기 흉내 (2단 배치)
adb shell wm size 2176x1812
adb shell wm density 420
sleep 4
tap_text "취소"
tap_text "태그"
shot 21_fold_twopane 2
adb shell wm size reset
adb shell wm density reset

# ---------- 앱 내 업데이트 흐름 ----------
PKG_V() { adb shell dumpsys package $PKG | grep -m1 versionCode | tr -s ' '; }
echo "before: $(PKG_V)" > snaps/update_result.txt
tap_text "설정"
adb shell input swipe 540 1800 540 500 300; sleep 0.5; adb shell input swipe 540 1800 540 500 300
sleep 1
tap_text "업데이트 확인"
if wait_text "업데이트가 있어요" 20; then
  shot 22_update_dialog 0.5
  tap_text "업데이트"
  shot 23_update_downloading 0.3
  # 처음에는 설치 허용이 꺼져 있음 → 안내 대화상자
  if wait_text "설치 허용이 필요해요" 60; then
    shot 24_update_need_permission 0.5
    tap_text "설정 열기"
    sleep 2
    shot 25_unknown_sources 0.5
    adb shell appops set $PKG REQUEST_INSTALL_PACKAGES allow
    adb shell input keyevent KEYCODE_BACK
  fi
  # 돌아오면 시스템 설치 화면이 열림
  for i in $(seq 1 30); do
    dump
    for b in "INSTALL" "Install" "설치" "UPDATE" "Update" "업데이트"; do
      POS=$(find_text "$b"); [ -n "$POS" ] && break
    done
    [ -n "$POS" ] && grep -q "packageinstaller" ui.xml && break
    POS=""; sleep 1
  done
  shot 26_installer 0.5
  if [ -n "$POS" ]; then
    adb shell input tap $POS
    sleep 12
    shot 27_after_install 0.5
  else
    echo "installer button not found" >> snaps/update_result.txt
  fi
else
  shot 22_update_check_result 0.5
  echo "update dialog not shown" >> snaps/update_result.txt
fi
echo "after: $(PKG_V)" >> snaps/update_result.txt
adb logcat -d | grep -iE "AppUpdater|PackageInstaller|FileProvider" | tail -40 >> snaps/update_result.txt || true

adb logcat -d -s AndroidRuntime:E > snaps/crash_logcat.txt || true
