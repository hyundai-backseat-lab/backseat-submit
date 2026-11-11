---
name: driving-ux-check
description: 화면(ui/)을 새로 만들거나 바꿨을 때 차량용 UX 기준으로 검토하는 체크리스트. 터치 영역·글자 크기·한눈에 읽히는지·주행/정차/주차 상태별 화면 제한·음성 우선. "화면 검토", "UX 체크", ui/ 변경이 있는 PR, reviewer 검토 시 사용.
argument-hint: F-번호 또는 화면 이름
---

# 차량 UX 검토

심사의 "차량용 앱다운가"를 가르는 기준이다. 수치가 붙은 항목은 화면 덤프(ui.xml)와 스크린샷으로 기계적으로 판정한다.

## 1. 자료 모으기
- 상태별로 화면을 찍는다:
  `SIM="speed-30" ./scripts/verify-emulator.sh ux-driving`
  `SIM="speed-0" WAIT=6 ./scripts/verify-emulator.sh ux-stopped`
  `SIM="park-on" ./scripts/verify-emulator.sh ux-parked`
- 판정은 `build/verify/ux-*/ui.xml`의 `bounds`와 `text`로 먼저 하고, 배치처럼 텍스트로 안 되는 것만 screen.png를 연다.
- px → dp 환산: `adb shell wm density`의 값으로 dp = px × 160 / density.

## 2. 체크리스트
| # | 항목 | 기준 | 판정 방법 |
|---|---|---|---|
| 1 | 주행 중 터치 요소 | 0개(버튼·입력창 없음) | ux-driving의 ui.xml에 `clickable="true"` 없음 |
| 2 | 정차·주차 중 버튼 터치 영역 | 76dp × 76dp 이상 (Android for Cars 권장값, 착수 시 doc-scout로 재확인) | ui.xml bounds 환산 |
| 3 | 상태 글자 크기 | 32sp 이상, 화면의 주 정보는 한 줄 | 코드의 fontSize, 스크린샷 |
| 4 | 한눈에 읽히는가 | 화면에 핵심 정보 1개, 문장 15자 이내. 2초 안에 읽혀야 함(운전자 시선 이탈 1회 2초 이내 원칙) | ui.xml text 길이·개수 |
| 5 | 대비 | 글자와 배경 명도 대비 4.5:1 이상, 낮·밤 테마 모두 | 색 토큰 값으로 계산 |
| 6 | 움직임 | 주행 중 애니메이션·깜빡임·영상 없음 | 코드 검색(animate*, infinite) |
| 7 | 키보드·긴 글 | 키보드 입력, 3줄 넘는 글, 스크롤 목록 없음 | ui.xml |
| 8 | 음성 우선 | 화면으로만 알 수 있는 정보가 없다(모든 상태 변화가 음성 또는 짧은 효과로도 전달됨) | 코드: 상태 변화마다 PleosClient.speak 호출 또는 의도적 무음 사유 |
| 9 | 주의 분산 선언 | MainActivity에 distraction-optimized 메타데이터 | manifest |
| 10 | 상태별 화면 | 주행: 상태 한 줄만 / 정차·주차: 상태 + 버튼 1개 / 동의·녹음·삭제 화면: 주차에서만 열리고 주행 전환 시 즉시 닫힘 | 세 상태 덤프 비교 |
| 11 | 운전자에게 요구하지 않음 | "~하세요"류 조작 요청 문구 없음 | strings.xml·assets 검색 |

## 3. 결과
표로 남긴다: # / 기준 / 실제(수치) / 근거 파일 / 통과 여부. 하나라도 실패면 PR 본문 "검증"에 적고 고친다. 2번 수치를 doc-scout로 다시 확인했으면 이 문서의 값을 고친다.
