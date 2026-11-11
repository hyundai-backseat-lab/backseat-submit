---
name: team-android-convention
description: 이 앱의 Kotlin/Android 코드를 새로 쓰거나 고칠 때 따르는 팀 공통 규칙(패키지 구조, MVVM, 상태 관리, 이름 짓기, 라이브러리, 로그). 코드 작성·리팩터링·새 화면·새 클래스를 만들 때 항상 사용.
---

# 팀 안드로이드 컨벤션

여러 사람의 Claude가 만든 코드를 하나로 합칠 수 있게 하는 기준이다. 여기 없는 선택을 해야 하면 기존 코드 패턴을 따르고, 새 패턴이면 docs/decisions.md에 남긴다.

## 1. 구조
- 화면 하나 = Activity 하나(MainActivity). 화면은 Jetpack Compose.
- MVVM: Screen(Compose) → ViewModel → session/(SessionController) → pleos/ 인터페이스(PleosClient)와 game/api 계약.
- 도메인 로직(session/, game/)은 순수 Kotlin. `android.*` import 금지 → 단위 테스트로 커버리지 80%.
- 패키지와 의존 규칙은 docs/architecture.md가 기준(check-docs.sh가 검사).

## 2. 파일 배치와 이름
| 무엇 | 위치 | 이름 |
|---|---|---|
| 화면 | ui/<화면>/ | `<화면>Screen.kt`, `<화면>ViewModel.kt`, `<화면>UiState.kt` |
| SDK 인터페이스 | pleos/ | `PleosClient` 하나. `I` 접두사 쓰지 않음 |
| SDK 실제 구현 | pleos/sdk/ | `Pleos` 접두사(`PleosVehicle`, `PleosSdkClient`) |
| 가짜 구현 | pleos/fake/ | `Fake` 접두사(`FakePleosClient`) |
| 이용 흐름 | session/ | `SessionController` |
| 놀이 | game/ | `<놀이>Game.kt`(예: `ObserveGame`), 계약은 game/api |
| 객체 연결 | di/ | `AppContainer.kt` 하나 |
| debug 전용 | app/src/debug/ | `SimReceiver.kt` |
- 클래스 PascalCase, 함수·변수 camelCase, 상수 UPPER_SNAKE_CASE.
- 테스트 이름은 한국어 백틱으로 합격 기준을 그대로: `` `주행 중 방향지시등이 켜지면 TTS가 멈춘다`() ``

## 3. 상태 관리
- ViewModel은 `StateFlow<XxxUiState>` 하나를 노출하고, 화면은 그것만 그린다. 화면에서 상태를 직접 만들지 않는다.
- 사용자 행동은 ViewModel 함수로(`onStartClick()`). 화면 → ViewModel 한 방향.
- 차량 신호·음성 이벤트는 Flow로 흐르고, 판정은 session/에서 한 번만 한다(같은 판정을 여러 곳에 복사하지 않는다).
- 코루틴: ViewModel은 `viewModelScope`. 시간·디스패처는 생성자로 주입해 테스트에서 가짜 시계·`StandardTestDispatcher`를 쓴다. `GlobalScope`, `runBlocking`(테스트 제외) 금지.

## 4. 객체 연결(의존성 주입)
- Hilt 같은 DI 라이브러리를 쓰지 않는다. `di/AppContainer`에서 손으로 연결한다(PT에서 설명하기 쉽게).
- debug 빌드는 AppContainer에서 Pleos 구현 위에 Sim 덮어쓰기를 연결한다.

## 5. 라이브러리
허용: Compose BOM, lifecycle-viewmodel-compose, kotlinx-coroutines, kotlinx-serialization(assets JSON), 테스트용 JUnit4·kotlinx-coroutines-test, 품질 도구(ktlint, detekt, kover).
그 외 라이브러리를 추가하려면 docs/decisions.md에 이유를 먼저 쓴다.

## 6. 문자열·로그·리소스
- 사용자에게 보이거나 들리는 한국어 문장은 `strings.xml` 또는 assets JSON에. 코드에 하드코딩하지 않는다.
- 호칭은 "엄마/아빠" 중립. 6~9세가 이해하는 말.
- 로그는 `BackseatLog` 한 곳을 통해 태그 `Backseat`로. 형식은 `<영역> <사건> key=value`(예: `SAFETY state=DRIVING signal=TURN action=PAUSE`). verify-car가 이 로그로 판정하므로 형식을 바꾸지 않는다.
- 좌표·녹음 내용·인식된 아이 말 전문은 로그에 남기지 않는다(인식 결과는 판정 결과와 단어 하나까지만).

## 7. 하지 않는 것
- 주석 처리한 옛 코드, 안 쓰는 코드·리소스 남기기(detekt·Lint가 막음)
- 한 파일에 여러 화면, 300줄 넘는 클래스(나누기)
- `!!` 사용(널 처리를 명시적으로)
