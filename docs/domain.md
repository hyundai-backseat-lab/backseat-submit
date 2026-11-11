# 도메인 지식 — 기능을 만들기 전에 알아야 할 것

표기: [확인] 공식 문서로 확인 / [추정] 이름·관례로 추정, 실제 SDK에서 확인 필요 / [미확인] 아직 근거 없음
[추정]·[미확인] 항목은 F-00·F-01 spike에서 실제 SDK 클래스로 확인하고 docs/sdk-notes/에 결과를 남긴다.

---

## 1. 차량 신호 — Vehicle SDK (ai.pleos.playground:Vehicle:2.3.2)
사용 패턴 [확인]
- `Vehicle(context).initialize()` → `vehicle.getXxx()`로 도메인 객체 → 다 쓰면 `vehicle.release()`
- Get: `getCurrentXxx(onComplete, onFailed)` 한 번 조회
- Callback: `registerXxx(listener)` → 값이 바뀔 때 `onXxxUpdated`, 해제는 같은 listener로 `unregisterXxx`
- Capability: 이 차량이 그 기능을 지원하는지, 값 범위는 어떤지 확인 → 기능 시작 전에 반드시 호출

도메인과 권한 [확인]
| 도메인 | 내용 | 권한 |
|---|---|---|
| Brake | 브레이크 페달, 주차 브레이크 상태 | CAR_DRIVE |
| TurnSignal | 방향지시등 상태 | CAR_LIGHTS |
| Safety | 차량 경고 정보 | CAR_SAFETY |
| Wiper | 와이퍼 상태 조회·제어 | CAR_WINDOWS |
| Door | 문 열림·잠금 상태 | CAR_DOORS |
| DrivingMode | 주행 모드, 크루즈 | CAR_DRIVE |
| Seat | 시트 위치·기능 | CAR_SEATS |
| 그 외 | Light, Window, HVAC, EvBattery, Fuel, Odometer, CarInfo, Display, SideMirror, Steeringwheel, Tire, Toll | |
권한은 `pleos.car.permission.<이름>` 형식으로 manifest에 선언. 공모전 개발 단계에서는 선언만으로 사용 가능(설명회).

Virtual Console로 바꿀 수 있는 신호 [확인: Virtual Console 가이드, 2026-11-05]
| 카드(도메인) | 값 |
|---|---|
| Gear Selection(Motion) | P, R, N, D |
| Turn Signal State(Convenience) | Off, 좌/우 Blink, 좌/우 Flash |
| Hazard Lights(Convenience) | Off, On |
| Brake Pedal(Motion) | 0~100% |
| Stop Lights(Convenience) | Off, On |
| Telltales(Safety) | 주차 브레이크·안전벨트·ABS·TPMS 등 경고등 Off/On/Blink |
| Windshield Wiper Switch(Convenience) | Off, Auto, Low, High (앞·뒤) |
| Door Position / Door Lock(Convenience) | 문별 열림·닫힘, 잠금 |
| Notification 1(Vehicle) | 알림 표시·숨김 |
| Cruise Target Speed(ADAS) | 5.6~27.8 m/s (크루즈 목표 속도일 뿐 실제 주행 속도 아님) |
없는 것: 실제 주행 속도, 내비 경로·도착, 위치(GPS), 음성.

속도 문제 [확인]
- Vehicle SDK에 속도 리스너가 없고(2.3.1에서 제거, 2.3.2도 동일), Virtual Console에도 실제 속도 조절이 없다.
- NaviHelper 3.0.5 `onDrivingInfo`는 목적지·경유지 정보뿐이라 속도가 없다 [확인: F-00, docs/sdk-notes/Vehicle.md]. 위치 정보(`CurrentLocationInfo`)에도 속도 필드가 없다.
- 그래서 주행 상태 판정은 속도 대신 기어·브레이크·주차 브레이크로 한다(features.md 결정표 "속도 없을 때" 참고). 앱의 `speedKmh`는 Pleos 구현에서 항상 "미지원"이고, debug sim으로만 값이 들어온다.

F-00에서 확인한 것 [확인: docs/sdk-notes/Vehicle.md]
- 기어: `Vehicle.getGear()`로 받는다(P/R/N/D). 주차 브레이크·비상등·경고는 전용 API가 없고 계기판 경고등(Telltale)으로만 온다(변경 알림 등록에 PRIVATE_CAR_SAFETY 필요).
- 와이퍼: `WiperState` OFF/ON(영역 FRONT_WINDSHIELD). 빗물 감지 값은 없다 → "와이퍼가 켜졌을 때 반응"만 설계

아직 모르는 것
- Safety 경고 종류(어떤 경고가 오는지) [미확인]
- 뒷좌석 탑승 감지 [미확인] → 쓰지 않는 것으로 가정

우리 앱의 판정 규칙(설계 결정)
- 말하지 않는 구간: 방향지시등 on, 브레이크 페달 on, 경고 발생(경고는 게임 종료)
- 정차: 속도 0이 2초 이상 지속. 정차 중에는 짧게 말해도 된다
- 주차: 주차 브레이크 on(+ 가능하면 P단)

## 2. 내비 — NaviHelper SDK
사용 패턴 [확인]: `initialize()` → `addListener(NaviHelperEventListener)` → `removeListener()` → `release()`

콜백 이름 [확인] (데이터 필드는 문서에서 확인 못 함)
- 경로: `onRouteStarted(RouteStartInfo)`, `onRoutingCompleted`, `onRouteCancelled`, `onReRouteEnded`
- 목적지: `onDestinationChanged`, `onDestinationArrived`, `onWayPointArrived`
- 정보: `onTripInfo`, `onTBTInfo`, `onDrivingInfo`, `onRouteStateInfo`, `onCurrentLocationInfo`, `onDestinationInfo`, `onNavigationStatus`, `onCurrentRoadSpeedLimit`, `onCameraAlert`, `onCautionPointInfo`

확인된 것 [확인: SDK 릴리스 노트 NaviHelper v3.0.5]
- 목적지·경유지 정보에 `remainDistance`, `remainTime` 필드(예전 totalDistance/totalTime에서 이름 변경)
- 새 리스너 `onTripInfo`, `onRoutingCompleted`, `onCautionPointInfo`, 요청 `requestTrip`
- 좌표 생성자 순서는 위도, 경도
- Playground 프로젝트 화면: "경로 정보 조회(경로·위치·TBT)", "경로 탐색 설정" 모두 사용 가능

추정
- 남은 시간·거리 = `onTripInfo` 또는 목적지 정보의 remainTime [추정, 필드 확인됨]
- 다음 회전 방향·거리 = `onTBTInfo` (TBT = Turn By Turn) [추정]
- 에뮬레이터에서 목적지를 정하고 주행을 재생할 수 있는지 [미확인] → F-01 다음 spike에서 확인

우리 기능 연결
- "벌써 도착!" 엔딩 = `onDestinationArrived` [확인]
- 게임 속도 조절 = 남은 시간 [추정]
- 다음 길은 어디로? = `onTBTInfo` [추정]
- 도착 예언가 = 출발 시 예상 소요 시간(`RouteStartInfo` 또는 TripInfo) vs 실제 도착 시각

## 3. 음성 — SpeechToText / TextToSpeech SDK (v3.3.1)
- 라이브러리 [확인]: `ai.pleos.playground:TextToSpeech:3.3.1`, `ai.pleos.playground:SpeechToText:3.3.1`(Pleos Maven, 접근 신청 안내 없음). 에러 콜백은 `ErrorType`, STT는 `InferenceSource.ON_DEVICE` 지정
- Playground 프로젝트 화면에는 TTS·STT·FusedLocation·Vehicle Control이 "Coming Soon"으로 표시된다. 프로젝트 단위 사용 신청(앱 등록·서버 모드·배포)이 아직 열리지 않았다는 뜻으로 보이며, 설명회에서는 "공모전에는 미공개 SDK까지 제공"한다고 했다. ON_DEVICE는 앱 등록이 필요 없으므로 동작할 가능성이 높지만, F-01에서 실제로 확인한다 [미확인]
- 모드 [확인]: HYBRID(기본, 서버 우선 + 온디바이스 폴백, 앱 등록 필요) / SERVER(앱 등록 필요) / ON_DEVICE(차량 내장 엔진, 네트워크·앱 등록 불필요) → 우리는 ON_DEVICE
- 언어 [확인]: TTS 로케일 목록에 `ko-KR` 있음. 모드별 지원 차이는 문서에 없음 [미확인]
- 에뮬레이터 [확인]: 지원. AVD에서 마이크 접근 설정 필요(STT 문서 언급)
- 말하는 중 듣기(끼어들기) [미확인] → 기본 설계는 순서대로: 앱이 다 말한 뒤 듣기 시작

아이 목소리 인식
- 일반 음성 인식기는 성인 음성 위주로 학습되어 아이 발음 인식률이 떨어진다(일반적으로 알려진 한계)
- 대응 설계: 정답을 "후보 단어 목록에 포함되는가"로 넓게 판정, 짧은 단어로 답하는 게임 위주, 못 알아들으면 "다시 말해줄래?" 1번 → 그다음은 힌트로 넘어가 흐름을 끊지 않는다

## 4. 녹음 — "우리 차 목소리 만들기"
- 녹음은 Pleos SDK가 아니라 안드로이드 기본 기능(MediaRecorder/AudioRecord, `RECORD_AUDIO` 권한) [미확인: Pleos 에뮬레이터 마이크로 녹음되는지]
- STT와 녹음은 동시에 쓰지 않는다(같은 마이크) → "따라 말하기"는 녹음만, 판정은 녹음 길이·음량으로 단순하게
- 개인정보: 만 14세 미만 아동의 개인정보를 처리하려면 법정대리인 동의가 필요하다(개인정보 보호법 제22조의2). 우리 설계는
  1) 녹음은 기기 안에만 저장, 외부 전송 없음 2) 첫 녹음 전 보호자 동의 화면 3) 목소리 카드 전체 삭제 버튼
  법률 자문이 아니므로 PT 답변용 근거로만 쓰고, 필요하면 운영사무국에 문의한다

## 5. 위치 — FusedLocation SDK
- [확인] `LocationData`: 위도, 경도, 고도, 진행 방향(heading), 신뢰도, 시각. 권한 `pleos.car.permission.FUSED_LOCATION`. 먼저 `isSupported()` 호출, 결과가 `null`일 수 있음. 에뮬레이터 지원
- 라이브러리 v1.1.2 [확인]: `getLastKnownLocation`, 근접 알림(`addProximityAlert`), `onChanged(LocationData?)`, `onError` 필수
- 대안: NaviHelper "경로 정보 조회"가 위치 정보도 주므로(`onCurrentLocationInfo`) FusedLocation이 막히면 그쪽을 쓴다
- 주의: Playground 프로젝트 화면에는 "Coming Soon"으로 표시되고, 설명회에서는 "선언만으로 사용 가능"이라고 함 → 실제 동작 확인 필요 [미확인]. 안 되면 안드로이드 기본 위치 API로 대체
- 에뮬레이터 위치 재생: Android 에뮬레이터의 Extended controls > Location > Routes에서 GPX 경로 재생 [추정: Pleos 이미지에서도 되는지 확인 필요] → 시연 재현성의 핵심

## 6. 장소 데이터 — 간판 찾기
카카오 로컬 "카테고리로 장소 검색" [확인]
- `GET https://dapi.kakao.com/v2/local/search/category.json`
- 헤더 `Authorization: KakaoAK {REST API 키}` (키는 local.properties → BuildConfig, 저장소에 올리지 않음)
- 파라미터: `category_group_code`(필수), `x`(경도), `y`(위도), `radius`(최대 20000m), `sort=distance`, `size`(최대 15)
- 쓸 만한 코드: CS2 편의점, OL7 주유소·충전소, CE7 카페, FD6 음식점, MT1 대형마트, BK9 은행, PM9 약국, HP8 병원, SW8 지하철역
- 쿼터·출처 표시 의무 등 이용 약관 [미확인]

미션 선정 규칙(설계 결정)
- 진행 방향(heading) 기준 좌우 45° 안, 150~500m 앞의 장소만 미션으로 낸다
- 방향지시등·브레이크 중에는 미션을 내지 않는다
- 네트워크 실패·결과 없음 → 데이터 없는 미션(색·글자·숫자 간판)으로 자동 전환
- 판정은 아이의 "찾았다!" + 차가 그 장소를 지나쳤는지(거리가 줄다가 늘어나는 시점)로 결과 공개

## 7. 주행 중 화면 제한 — Android Automotive Driver Distraction
- 차량 OS는 주행 상태에 따라 UI 제한을 건다. 앱은 `CarUxRestrictionsManager`로 현재 제한을 받아 화면을 바꾼다
- 주행 중에도 실행되어야 하는 Activity는 manifest에 distraction-optimized 메타데이터를 선언해야 한다
- 우리 설계: 화면은 큰 글씨 한 줄 상태 + 큰 버튼 하나, 주행 중에는 버튼도 숨기고 음성으로만 조작
- 출처: Android for Cars 개발 문서(Driver Distraction Guidelines) — F-05 착수 전 doc-scout로 세부 확인

## 8. 오디오 포커스 — 내비 안내와 겹칠 때
- 내비 안내 음성은 잠깐 오디오 포커스를 가져간다 → 우리 앱은 일시 손실(LOSS_TRANSIENT)을 받으면 멈추고, 다시 얻으면(GAIN) 이어간다
- Pleos TTS SDK가 내부에서 오디오 포커스를 처리하는지 [미확인] → 처리하지 않으면 앱에서 직접 요청

## 9. 놀이 설계 — 아이 기준
- 타깃: 6~9세(초등 저학년 전후). 4~5세는 연상 잇기·음성 인식 모두 어려워 1차 타깃에서 제외. 한 라운드 30초~1분, 한 판 5분 안팎
- 호칭은 "엄마/아빠" 중립으로(기본 문구에 "아빠"를 고정하지 않는다)
- "원숭이 엉덩이는 빨개"는 연상 잇기(A는 B, B는 C)라 끝말잇기보다 어린아이도 할 수 있다
- 칭찬은 즉시, 틀려도 감점 대신 힌트. 앱이 먼저 말을 많이 하지 않는다(아이 차례가 주인공)
- 운전자에게 무엇을 하라고 시키지 않는다. 앱은 차 상태에 "반응"만 한다(예: 와이퍼를 켜면 아이 목소리로 "와이퍼 출동!")

## 10. spike에서 확인할 목록 (확인되면 위 표기를 [확인]으로 바꾼다)
| # | 확인할 것 | 언제 | 안 되면 |
|---|---|---|---|
| 1 | 속도 값 출처 [확인 완료: 없음], 기어·주차 브레이크 값 [확인: Gear API, Telltale] | F-00 | 기어·브레이크·주차 브레이크로 판정(결정표 "속도 없을 때") |
| 2 | 와이퍼 on/off 콜백 [확인: WiperState] | F-00 | 목소리 카드 트리거에서 제외 |
| 3 | ON_DEVICE 한국어 TTS/STT, 지연 | F-01 | 안드로이드 기본 TTS/STT 비교 |
| 4 | 에뮬레이터 마이크 녹음 | F-01 | 시연은 미리 녹음한 파일로 |
| 5 | NaviHelper 남은 시간·회전 안내 필드, 에뮬레이터 주행 재생 [F-05: 도착 API 확인, 주행 재생 미확인 → docs/sdk-notes/Navi.md] | F-01 다음 | 도착 이벤트만 사용, 시연은 sim nav-arrive |
| 6 | FusedLocation 동작, GPX 재생 | 간판 찾기 착수 전 | 안드로이드 기본 위치 API |
| 7 | 카카오 API 키·쿼터·약관, APK 키 노출 대책 | 간판 찾기 착수 전 | 데이터 없는 미션만 |
| 8 | Virtual Console 신호 목록 [확인 완료: 위 표] | - | 속도·내비 도착은 VC로 못 바꾸므로 debug sim으로 시연 |
| 9 | 차량 마이크·스피커 위치(뒷좌석 인식, 출력 존) | F-01 | 앱 발화를 짧게, 아이 답은 짧은 단어 위주 |
| 10 | 에뮬레이터 주행 제한 상태 재현(VHAL 속도 주입) [F-06: 재현 방법 없음 → 앱 내부 판정, docs/sdk-notes/Vehicle.md] | F-06 | 앱 내부 판정 + 사람 실측 |
