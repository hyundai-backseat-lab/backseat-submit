#!/usr/bin/env bash
# 디버그 빌드 전용: 차량 신호를 흉내 내는 브로드캐스트를 보낸다.
# 앱의 debug 소스셋에 있는 SimReceiver가 받아 Fake 차량 소스에 반영한다(docs/decisions.md D-006).
#   ./scripts/sim.sh start            (화면의 시작 버튼과 같은 길로 이용 시작을 요청한다)
#   ./scripts/sim.sh stt "<문장>"     (STT 인식 결과를 주입한다. 예: ./scripts/sim.sh stt "빨가면 사과")
#   ./scripts/sim.sh turn-left | turn-right | turn-off | brake-on | brake-off | speed-0 | speed-30 | park-on | park-off | gear-d | gear-p | wiper-on | wiper-off | door-open | hazard-on | hazard-off | warning | focus-loss | focus-gain | nav-arrive
set -u
export MSYS_NO_PATHCONV=1
APP_ID="${APP_ID:-io.backseatlab.playground}"
ev="${1:?이벤트 이름이 필요합니다 (docs/features.md 시연 시나리오 참고)}"
if [ "$ev" = "stt" ]; then
  text="${2:?stt 뒤에 인식 결과로 줄 문장이 필요합니다. 예: ./scripts/sim.sh stt \"빨가면 사과\"}"
  # 공백이 든 문장이 기기 셸에서 한 값으로 전달되도록 홑따옴표로 한 번 더 감싼다(문장에 홑따옴표가 있으면 안 된다)
  adb shell am broadcast -a "$APP_ID.SIM" --es event stt --es text "'$text'" -p "$APP_ID" | tail -n 1
  exit 0
fi
adb shell am broadcast -a "$APP_ID.SIM" --es event "$ev" -p "$APP_ID" | tail -n 1
