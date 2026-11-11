#!/usr/bin/env bash
# 에뮬레이터 실화면 검증: 설치 → 실행 → 스크린샷·화면 텍스트·크래시 로그 요약.
#   ./scripts/verify-emulator.sh [이름]     예) ./scripts/verify-emulator.sh F-03-brake
# 결과물: build/verify/<이름>/{screen.png, ui.xml, logcat.txt}
# 종료코드: 0 정상 / 1 설치·실행·크래시 문제 / 3 에뮬레이터 없음(SKIP)
set -u
export MSYS_NO_PATHCONV=1   # Windows Git Bash가 /sdcard 같은 기기 경로를 C:/... 로 바꾸지 않게
cd "$(dirname "$0")/.." || exit 1
APP_ID="${APP_ID:-io.backseatlab.playground}"
ACTIVITY="${ACTIVITY:-.MainActivity}"
NAME="${1:-$(date +%H%M%S)}"
OUT="build/verify/$NAME"
WAIT="${WAIT:-4}"
mkdir -p "$OUT"

# 기기가 여러 대면 에뮬레이터를 고른다
ANDROID_SERIAL="${ANDROID_SERIAL:-$(adb devices 2>/dev/null | awk '/^emulator-/{print $1; exit}')}"
export ANDROID_SERIAL
if [ -z "$ANDROID_SERIAL" ] || ! adb get-state >/dev/null 2>&1; then
  echo "SKIP: 실행 중인 에뮬레이터가 없습니다. Android Studio Device Manager에서 Pleos Connect AVD를 켜 주세요."
  exit 3
fi

if ! ./gradlew --console=plain --quiet installDebug >"$OUT/install.log" 2>&1; then
  echo "FAIL: installDebug 실패 — $OUT/install.log"
  grep -E "^e: |error:|What went wrong|FAILED" "$OUT/install.log" | head -n 20
  exit 1
fi

# 부팅 완료 대기(최대 120초)
for _ in $(seq 1 120); do [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break; sleep 1; done
# 무인 검증 중 권한 다이얼로그가 뜨지 않게 미리 허용(없는 권한이면 조용히 무시)
# Pleos 에뮬레이터는 앱을 사용자 10으로 실행하므로 현재 사용자에게 준다
user=$(adb shell am get-current-user 2>/dev/null | tr -d '\r')
for perm in android.permission.RECORD_AUDIO ${GRANT:-}; do adb shell pm grant --user "${user:-0}" "$APP_ID" "$perm" >/dev/null 2>&1; done
adb logcat -c
adb shell am force-stop "$APP_ID" >/dev/null 2>&1
adb shell am start -W -n "$APP_ID/$ACTIVITY" >/dev/null 2>&1
sleep "$WAIT"

# sim 이벤트를 연달아 주고 싶으면 SIM="brake-on" 처럼 넘긴다
if [ -n "${SIM:-}" ]; then
  for ev in $SIM; do ./scripts/sim.sh "$ev" >/dev/null; sleep 1; done
fi

adb exec-out screencap -p >"$OUT/screen.png" 2>/dev/null
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
adb logcat -d >"$OUT/logcat.txt" 2>/dev/null

echo "스크린샷: $OUT/screen.png"
echo "--- 화면에 보이는 텍스트 ---"
grep -o 'text="[^"]\+"' "$OUT/ui.xml" 2>/dev/null | sed 's/text="\(.*\)"/\1/' | awk '!seen[$0]++' | head -n 25

crash=$(grep -E "FATAL EXCEPTION|ANR in $APP_ID|Process: $APP_ID" "$OUT/logcat.txt" | head -n 5)
echo "--- 앱 로그(Backseat 태그, 최근 15줄) ---"
grep -E " Backseat" "$OUT/logcat.txt" | tail -n 15

pid=$(adb shell pidof "$APP_ID" 2>/dev/null | tr -d '\r')
if [ -n "$crash" ]; then
  echo "--- 크래시 ---"
  echo "$crash"
  grep -A 12 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -E "Exception|Error|at $APP_ID" | head -n 12
  echo "FAIL: 크래시 발생"
  exit 1
fi
if [ -z "$pid" ]; then
  echo "FAIL: 앱 프로세스가 살아 있지 않습니다"
  exit 1
fi
# 기대 결과 판정: EXPECT="TTS paused reason=BRAKE|화면글자" 처럼 '|'로 여러 개(모두 있어야 통과)
if [ -n "${EXPECT:-}" ]; then
  IFS='|' read -ra wants <<<"$EXPECT"
  for w in "${wants[@]}"; do
    if ! grep -qF "$w" "$OUT/logcat.txt" "$OUT/ui.xml" 2>/dev/null; then echo "FAIL: 기대한 '$w' 이(가) 로그·화면에 없음"; exit 1; fi
  done
  echo "PASS: 기대 결과 확인 ($EXPECT) / 실행 중 (pid $pid)"
else
  echo "PASS(실행만 확인): 크래시 없음, 실행 중 (pid $pid) — 기능 동작 판정은 EXPECT로 해야 함"
fi
