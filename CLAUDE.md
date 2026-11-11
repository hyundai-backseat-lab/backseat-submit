# 벌써도착 (backseat-playground)

뒷좌석 아이와 음성으로 노는 Pleos Connect(AAOS) 차량용 앱. 현대차그룹 차량용 앱 개발 공모전 출품작.
공모전 규칙·일정: docs/contest.md / 기획서: docs/plan-proposal.md

## 목표
- 일반 부문 대상. 판단이 갈리면 docs/strategy.md의 우선순위를 따른다: 안전 > 시연 안정성 > 차량특화성 > SDK 활용 > 기능 개수.
- 모든 기능은 docs/strategy.md의 KPI(K1~K7) 중 하나 이상을 올려야 한다. 현재 성적은 `./scripts/score.sh`(머지된 기능 기준 자동 계산).

## 명령어
- 빠른 검사(포맷+단위테스트): `./scripts/check.sh`
- 에뮬레이터 실화면 검증: `./scripts/verify-emulator.sh` (스크린샷 경로와 크래시 요약 출력)
- 차량 상태 흉내(디버그 빌드): `./scripts/sim.sh <이벤트>` (turn-left, brake-on, speed-0, park-on, wiper-on, door-open, warning, nav-arrive 등)
- 제출용 빌드: `./gradlew assembleRelease`
- Gradle 출력은 길다. 직접 돌리지 말고 위 스크립트를 쓴다(요약만 출력함).

## 작업 원칙
- 무엇을 만들지는 docs/features.md가 정한다. 완료 기준에 없는 기능·화면·옵션은 만들지 않는다. "만들지 않을 것"에 있는 건 제안도 하지 않는다.
- 기능은 완료 기준을 테스트로 먼저 옮기고 구현한다. 버그는 재현 테스트부터 쓴다.
- Pleos SDK API를 추측하지 않는다. 모르면 doc-scout 에이전트로 공식 문서를 확인하고, 확인된 사용법은 docs/sdk-notes/<SDK>.md에 남긴다.
- 설계상 선택을 했으면 docs/decisions.md에 한 단락으로 남긴다(무엇을, 왜, 대안).
- 코드를 쓸 때는 team-android-convention, Pleos SDK를 부를 때는 pleos-api-guide, 화면을 바꿨을 때는 driving-ux-check 스킬을 따른다.
- 커밋은 한 의도씩, Conventional Commits 형식(`feat(game): 말잇기 차례 전환`). 요약은 한국어.

## 비자명한 제약
- minSdk 28, compileSdk 37(34는 Compose가 요구하는 35 이상에 못 미쳐 빌드 불가, D-014), Kotlin. Pleos Maven: https://nexus-playground.pleos.ai/repository/maven-releases/
- Pleos SDK(Vehicle, NaviHelper, STT, TTS, FusedLocation)는 `pleos/`의 `PleosClient` 인터페이스 뒤에서만 호출한다(SDK import는 `pleos/sdk`). 앱 로직은 인터페이스만 안다. 테스트는 Fake 구현으로, 디버그 빌드는 `sim.sh` 브로드캐스트로 상태를 바꿀 수 있어야 한다.
- STT/TTS는 ON_DEVICE 모드만 쓴다(앱 등록·네트워크 불필요).
- 안전 규칙은 docs/features.md의 "주행 상태 × 신호 결정표"가 기준이다(예: 브레이크는 주행 중에만 멈춤, 경고는 무음 종료). 주행 중에는 터치 입력이 필요한 UI를 띄우지 않는다.
- 구조는 docs/backseat-system-architecture.md(SessionController·PleosClient·GameSession). 이용 흐름은 `session/`, 놀이 내용은 `game/`(계약은 `game/api`), 화면은 `ui/`(session만 앎).
- 2차 PT에서 팀원이 구조를 직접 설명해야 한다. 영리한 코드보다 읽기 쉬운 코드를 택한다.

## 문서와 품질
- 구조·진입점·의존 규칙은 docs/architecture.md, 용어는 docs/glossary.md. 새 진입점·패키지·의존·용어를 만들면 같은 커밋에서 갱신한다(check-docs.sh가 어긋나면 실패시킴, 그래프는 `./scripts/check-docs.sh --write`).
- 핵심 로직(session/, game/)은 테스트 커버리지 80% 이상(pre-push·CI의 koverVerify). 안 쓰는 코드는 남기지 말고 지운다(detekt·Lint가 검사). 주석 처리한 옛 코드도 남기지 않는다.

## 하지 말 것
- local.properties, *.jks/*.keystore, apk/ 폴더를 읽거나 고치지 않는다(설정으로 차단됨).
- main 브랜치에 직접 커밋하지 않는다. 기능 브랜치 → PR.
