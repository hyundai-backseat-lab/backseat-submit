---
name: verify-car
description: Pleos Connect 에뮬레이터에서 앱을 설치·실행하고 차량 신호(방향지시등, 브레이크, 경고)를 흉내 내며 실제 화면과 로그로 동작을 확인한다. "에뮬레이터에서 확인", "실화면 검증", "시연 시나리오 돌려봐" 요청이나 feature 절차 4단계에서 사용.
argument-hint: F-번호 또는 시나리오 설명
model: sonnet
effort: medium
---

# 실화면 검증

1. 대상 기능의 시연 시나리오를 docs/features.md에서 읽는다.
2. 기본 실행: `./scripts/verify-emulator.sh <F-번호>-start`
   - SKIP(종료코드 3)이면 여기서 멈추고 "에뮬레이터 꺼져 있음"으로 보고한다.
   - FAIL이면 출력된 크래시/오류를 근거로 원인을 고친 뒤 다시 실행한다.
3. 시나리오 단계마다 신호를 주고 확인한다.
   - 반드시 EXPECT로 기대 결과를 기계 판정한다. 예: `SIM="brake-on" EXPECT="TTS paused reason=BRAKE" ./scripts/verify-emulator.sh <F-번호>-brake`
   - "PASS(실행만 확인)"은 크래시가 없다는 뜻일 뿐 기능 통과가 아니다. 시나리오 통과로 보고하지 않는다.
   - 화면 배치·상태 표시처럼 텍스트로 판정이 안 될 때만 스크린샷(screen.png)을 Read로 열어 본다. 이미지는 토큰을 많이 쓰므로 단계마다 열지 않는다.
4. 결과를 표로 남긴다: 단계 / 기대 / 실제 / 근거(로그 줄 또는 스크린샷 경로) / 통과 여부.
5. 실제와 기대가 다르면 코드를 고치고 check.sh → 해당 단계만 다시 검증한다.

음성은 에뮬레이터에서 귀로 확인할 수 없으므로, TTS 시작·중단·재개와 STT 결과는 앱이 Backseat 태그로 남기는 로그로 판정한다(예: `TTS start`, `TTS paused reason=BRAKE`, `STT result="빨개"`).
