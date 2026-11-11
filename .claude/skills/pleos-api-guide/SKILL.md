---
name: pleos-api-guide
description: Pleos Connect SDK(Vehicle, NaviHelper, SpeechToText, TextToSpeech, FusedLocation)를 호출하는 코드를 쓰거나 고칠 때의 공통 패턴·권한·에러 처리 규칙. "차량 신호 받아와", "TTS 붙여줘", "SDK 연결", pleos/ 패키지 작업 시 사용.
---

# Pleos SDK 호출 가이드

모든 팀원의 Claude가 같은 패턴으로 SDK를 부르게 하는 기준이다. 시그니처가 불확실하면 추측하지 말고 doc-scout 에이전트로 확인한다. 확인된 내용은 docs/sdk-notes/<SDK>.md에 쓰고, 이 문서의 "확인된 패턴"에도 한 줄 추가한다.

## 1. 어디서 부르나
- SDK는 `pleos/sdk/` 안에서만 import한다. 나머지 코드는 `pleos/`의 `PleosClient` 인터페이스 하나만 안다(check-docs.sh가 검사).
- 새 SDK 기능은 PleosClient에 멤버를 추가하고 Pleos 구현(`pleos/sdk`)과 Fake 구현(`pleos/fake/FakePleosClient`)을 같이 고친다. debug sim은 `debug/SimPleosClient`가 덮어쓴다.

## 2. 수명 주기
- 생성·`initialize()`는 앱 시작 시 `di/AppContainer`에서 한 번. `release()`는 앱 종료 경로에서 한 번. 화면마다 만들지 않는다.
- 콜백을 등록했으면 같은 listener 객체로 해제한다(Vehicle 문서: 등록한 객체·area 그대로 unregister).

## 3. 값 받는 세 가지 방식 (Vehicle) — [확인]
| 방식 | 언제 | 형태 |
|---|---|---|
| Capability | 기능을 쓰기 전 반드시 먼저 | 지원 여부·값 범위. 미지원이면 그 항목은 "미지원"으로 표시하고 기능에서 제외 |
| Get | 시작 시 현재 값 한 번 | `getCurrentXxx(onComplete, onFailed)` |
| Callback | 바뀔 때마다 | `registerXxx(listener)` → `onXxxUpdated` / `unregisterXxx(listener)` |

## 4. 콜백을 Flow로 감싸는 표준 형태
앱 로직은 콜백이 아니라 Flow만 본다. 모든 신호를 이 모양으로 통일한다.
```kotlin
// pleos/sdk/PleosVehicle.kt (형태 예시 — 실제 listener·메서드 이름은 sdk-notes 기준)
val doorOpen: Flow<Boolean> = callbackFlow {
    val listener = object : DoorPositionListener {
        override fun onPositionUpdated(pos: Int?) { trySend(pos != null && pos > 0) }
        override fun onFailed(e: Exception) { Log.w(TAG, "door failed", e) }  // 끊지 말고 기록
    }
    door.registerDoorPosition(doorPositionListener = listener)
    awaitClose { door.unregisterDoorPosition(doorPositionListener = listener) }
}.distinctUntilChanged()
```
- 첫 값은 Get으로 받아 `onStart { emit(...) }`로 넣는다.
- 값이 `null`이면 "모름"으로 다룬다. 안전 판정에서 "모름"은 위험한 쪽(주행·신호 켜짐)으로 본다.

## 5. 권한 — [확인]
manifest에 `pleos.car.permission.<이름>`으로 선언. 공모전 개발 단계는 선언만으로 사용 가능.
| 권한 | 도메인 |
|---|---|
| CAR_DRIVE | Brake, DrivingMode, Odometer |
| CAR_LIGHTS | Light, TurnSignal |
| CAR_SAFETY | Safety(경고), Tire |
| CAR_WINDOWS | Window, Wiper |
| CAR_DOORS | Door |
| CAR_SEATS | Seat |
| CAR_SPEED | 속도(샘플 앱 권한 목록에 존재, 사용법은 확인 필요) |
| FUSED_LOCATION | FusedLocation |
STT/TTS·NaviHelper 권한은 각 SDK의 Declaring Permission 문서 기준으로 sdk-notes에 기록한다.

## 6. 음성 SDK — [확인]
- 모드는 `ON_DEVICE`만. HYBRID·SERVER는 앱 등록이 필요하므로 쓰지 않는다(바꾸려면 decisions.md에 기록).
- 로케일 `ko-KR`.
- 말하기(TTS)와 듣기(STT)는 동시에 하지 않는다. TTS 완료 이벤트 후 STT 시작.
- 녹음(MediaRecorder)과 STT는 마이크를 같이 쓰지 않는다.

## 7. 에러 처리
| 상황 | 증상 | 처리 |
|---|---|---|
| 권한 미선언 | `onFailed`에 `VehicleException`(ERROR_SECURITY_PERMISSION) | manifest 권한 확인. 코드로 우회하지 않는다 |
| 미지원 기능 | Capability 결과 미지원 | 기능 비활성 + 로그 `CAR unsupported <항목>` |
| 값 없음 | 콜백 값 `null` | "모름" 처리(위험한 쪽) |
| 위치 없음 | FusedLocation 콜백 `null` | 위치 기반 미션 건너뜀 |
| 초기화 실패 | initialize 예외 | 앱은 켜지되 상태 화면에 "차량 연결 안 됨" 표시, 게임 시작 막음 |
모든 실패는 `Backseat` 태그 로그로 남긴다(verify-car가 로그로 판정).

## 8. 확인된 사실 (계속 추가)
- 버전(2026-10-19 릴리스): Vehicle 2.3.1, NaviHelper 3.0.5, TextToSpeech 3.3.1, SpeechToText 3.3.1, FusedLocation 1.1.2. 좌표는 `ai.pleos.playground:<SDK이름>:<버전>`
- Vehicle 2.3.1에서 `VehicleSpeedListener` 제거 → 속도는 다른 출처를 찾거나 기어·브레이크로 판정
- NaviHelper 3.0.5: 남은 거리·시간 필드는 `remainDistance`/`remainTime`, 좌표 생성자는 (위도, 경도), `onTripInfo` 추가, `onRouteEnded` 대신 `onRoutingCompleted`
- TTS·STT 3.3.1: 에러는 `ErrorType`, STT는 `InferenceSource.ON_DEVICE`
- FusedLocation 1.1.2: `onChanged(LocationData?)` nullable, `onError` 필수, 먼저 `isSupported()`
- Vehicle 2.3.1은 Maven에 없고 2.3.2를 쓴다. 도메인은 `vehicle.getBrake()`처럼 메서드로 얻는다(프로퍼티는 private). 리스너 값은 enum도 null일 수 있다. 속도 API 없음, 비상등·주차 브레이크·경고는 Telltale. 자세히: docs/sdk-notes/Vehicle.md
- Virtual Console로 바꿀 수 있는 신호와 없는 신호: docs/domain.md 1번

## 출처
- Getting Started: https://document.pleos.ai/en/docs/pleos-only/getting-started
- Vehicle 권한: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission
- Vehicle 콜백: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/callback
- STT: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro
- TTS: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro
- FusedLocation: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro
