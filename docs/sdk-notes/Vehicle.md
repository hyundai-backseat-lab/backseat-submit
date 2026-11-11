# Vehicle SDK 확인 기록 (F-00)

확인 방법: Pleos Maven에서 `Vehicle-2.3.2.aar`, `NaviHelper-3.0.5.aar` 및 의존 데이터 라이브러리(vehicle-data 1.1.11, navi-data 3.0.4)를 받아 `javap`로 실제 클래스·메서드·어노테이션을 읽었다(2026-11-05). 공식 문서 페이지는 시그니처가 렌더링되지 않아 근거로 쓰지 못했다.
표기: [확인] AAR에서 확인 / [실측필요] 코드는 있으나 에뮬레이터·차량에서 사람이 확인해야 함

## 의존성
- 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/` — 인증 없이 받아진다 [확인]. GitHub Actions에도 secrets가 필요 없다.
- 좌표: `ai.pleos.playground:Vehicle:2.3.2`. **2.3.1은 저장소에 없다**(Vehicle 버전은 2.0.3, 2.3.2뿐). features.md·domain.md의 "2.3.1"은 2.3.2로 읽는다.
- NaviHelper는 3.0.5(2.0.2, 2.0.3, 3.0.5). F-00에서는 쓰지 않는다.
- 이 AAR의 manifest가 `queries`(서비스 `ai.pleos.playground.vehicle.VEHICLE_START`)와 `VEHICLE_BINDING` 권한을 자동으로 합쳐 준다.

## 사용 형태 [확인]
```kotlin
val vehicle = Vehicle(context)
vehicle.initialize()          // 인자 없음
val brake = vehicle.getBrake() // 프로퍼티(vehicle.brake)가 아니라 getXxx() 메서드로 부른다 (프로퍼티는 private)
```
도메인 getter: getBrake, getTurnSignal, getGear, getWiper, getDoor, getSafety, getDrivingMode, getLight, getCarInfo 등.
리스너 값은 enum도 null일 수 있다 → 우리 코드는 null을 `Reading.Unknown`("모름")으로 바꾼다.

## 신호별 출처
| 우리 신호 | SDK 호출 | 값 | 권한 |
|---|---|---|---|
| 방향지시등 | `TurnSignal.registerTurnSignalState` / `getCurrentTurnSignalState` / `checkTurnSignalStateCapabilities` | `TurnSignalValue` OFF, LEFT, RIGHT | CAR_LIGHTS |
| 브레이크 페달 | `Brake.registerBrakePedalStatus` / `getCurrentBrakePedalStatus` / `checkBrakePedalStatusCapabilities` | Boolean | CAR_DRIVE |
| 기어 | `Gear.registerGearSelection` / `getCurrentGearSelectionState` (Capability 확인 메서드 없음. 조회 실패는 "모름"으로 둔다) | `VehicleGear` GEAR_PARK/REVERSE/NEUTRAL/DRIVE/GEAR_1~9/UNKNOWN | CAR_DRIVE |
| 와이퍼 | `Wiper.registerWiperState(WindowArea, …)` / `getCurrentWiperState(WindowArea, …)` / `checkWiperStateCapabilities` | `WiperState` OFF, ON, SERVICE, OTHER. 영역은 `WindowArea(WindowValue.FRONT_WINDSHIELD)` | CAR_WINDOWS |
| 문 | (0이 닫힘이라는 것은 가정, null은 "모름") `Door.registerDoorPosition(DoorArea, …)` / `getCurrentDoorPosition` / `checkDoorPositionCapabilities`(지원 문 목록을 Map 키로 줌) | 위치 Int, 0이면 닫힘(가정) | CAR_DOORS |
| 비상등·주차 브레이크·경고 | **전용 API 없음.** `Safety.registerTelltales` / `getTelltalesInfo` / `checkTelltalesCapabilities`의 계기판 경고등(Telltale) 목록 | `TelltaleType` TURN_SIGNAL_LEFT+TURN_SIGNAL_RIGHT 동시 점등 = 비상등, PARKING_BRAKE 또는 ELECTRONIC_PARKING_BRAKE = 주차 브레이크, MASTER_WARNING = 경고. 상태는 OFF/ON/BLINK | CAR_SAFETY, 변경 알림 등록은 **PRIVATE_CAR_SAFETY** |
| 속도 | **없음** (아래) | | |

- 위험: 비상등을 "좌·우 방향지시등 경고등 동시 점등"으로 읽는데, 방향지시등 점멸 한순간의 동시 점등과 구분되는지는 실측 전이다.
- `TurnSignalValue`에는 비상등 값이 없다. Light 도메인에도 비상등이 없다. `PleosPropertyIds`에 `HAZARD_LIGHTS_STATE`는 있으나 공개 API로 노출되지 않는다.
- Capability 결과는 `CapabilityConfig.accessType`(NONE, READ, WRITE, READ_WRITE). READ 계열이 아니면 "미지원"으로 표시한다.

## 속도 출처 — 없다 [확인]
- Vehicle 2.3.2에 속도 도메인·리스너가 없다. `PleosPropertyIds.PERF_VEHICLE_SPEED`는 내부 상수일 뿐 공개 API가 아니다.
- NaviHelper 3.0.5 `onDrivingInfo(DrivingInfo)`의 `DrivingInfo`는 목적지·경유지(`DrivingState`: name, duration, distance, 충전 정보)만 담고 **속도 필드가 없다**. 위치 정보(`CurrentLocationInfo`)에는 위도·경도·heading뿐이다.
- 결론: `VehicleState.speedKmh`는 Pleos 구현에서 항상 `Unsupported`. 주행 상태는 features.md 결정표의 "속도 없을 때" 규칙(기어 + 브레이크 + 주차 브레이크)으로 판정한다. 속도 값은 debug `sim.sh speed-0|speed-30`으로만 들어온다.
- `CAR_SPEED` 권한은 샘플 앱 목록에만 있고 이를 쓰는 API를 찾지 못해 선언하지 않았다.

## Virtual Console과 신호의 대응 (사람 실측 필요)
domain.md 1번 표(VC 카드)와 위 SDK 값의 대응은 코드로 확인할 수 없다. 에뮬레이터에서 아래를 확인해 이 표를 채운다.
| 확인할 것 | 결과 |
|---|---|
| VC "Gear Selection"(Motion) P → 앱 기어 | [부분 확인] P로 바꿔도 앱이 죽지 않음. 앱 화면이 기어를 표시하지 않아 **값이 도달하는지는 확인 불가**. F-03에서 기어를 쓰기 전에 로그(`Backseat CAR gear=...`)로 확인 필요 |
| VC "Turn Signal State"(Convenience) Blink left → `TurnSignalValue` | [확인 2026-11-05] 앱에 "왼쪽 방향지시등". 단 켜지면 차량 OS가 사각지대 카메라(`ai.umos.car.evs`)를 앱 위에 덮는다 → 시연 영상에서 앱 화면이 가려진다. Blink right·Flash는 미확인 |
| VC "Hazard Lights"(Convenience) On → 앱의 비상등 | [확인 2026-11-05] **안 온다.** 앱에 "주행 신호 없음" 그대로. 반면 VC "Telltales"(Safety)에서 Turn Signal Left·Right를 둘 다 Blink로 켜면 앱에 "비상등"이 뜬다 → 앱의 판정은 맞고, Hazard Lights 카드가 그 경고등을 켜지 않을 뿐이다. 시연·영상에서는 Telltales 두 개를 켜거나 sim.sh를 쓴다 |
| VC "Telltales"(Safety) Parking Brake On → PARKING_BRAKE/ELECTRONIC_PARKING_BRAKE | [확인 2026-11-05] 앱에 "주차 브레이크". Telltale 변경 알림(PRIVATE_CAR_SAFETY 경로)이 실제로 동작함 |
| VC "Telltales" Master Warning On → 앱의 경고 | [확인 2026-11-05] 앱에 "경고" |
| VC "Door Position"(Convenience) Front left Open → 앱의 문 열림 | [확인 2026-11-05] 앱에 "문 열림", Close로 사라짐. 나머지 문은 미확인 |
| VC "Windshield Wiper Switch"(Convenience, Front) Low·Auto → `WiperState` | [확인 2026-11-05] 둘 다 앱에 "와이퍼", Off로 돌리면 사라짐. High·Rear는 미확인 |
| PRIVATE_CAR_SAFETY가 에뮬레이터에서 부여되는가 | [확인] Pleos Connect 에뮬레이터 2.1.0에서 `granted=true`(`dumpsys package`). 앱이 선언한 pleos.car.permission 6개 모두 granted. 실제 차량·심사 환경은 [실측필요] |
| VC 화면 캡처 | [확인] docs/screens/F-00-virtual-console.png (Emulation Controls) |

에뮬레이터 실행(2026-11-05): 앱 시작 시 Pleos에서 값이 들어와 "주행 신호 없음"이 표시되고 `Backseat CAR … failed` 로그는 없었다. 어느 신호가 값을 받았는지는 VC 실측에서 같이 본다.

## 앱 쪽 연결
- `pleos/PleosClient.vehicle: StateFlow<VehicleState>` — 신호마다 `Reading`(Unsupported / Unknown / Value).
- `pleos/sdk/PleosVehicle` — 위 표대로 Capability → Get → Callback. 한 신호가 실패해도 다른 신호는 계속 등록된다(문서 미확인 구간은 로그 `Backseat CAR <신호> failed`).
- debug 빌드: `di/BuildTypeWiring.kt`(debug 소스셋)가 PleosClient를 `debug/SimPleosClient`로 감싼다. release 소스셋의 같은 파일은 그대로 돌려준다.

## 주행 중 화면 제한 (F-06 spike, 2026-11-11)
- 에뮬레이터에서 CarUxRestrictions의 "주행 제한" 상태를 만드는 방법을 찾지 못했다. 속도 API가 없고(위 결론) Virtual Console에도 속도 입력이 없다. 앱에서 CarUxRestrictionsManager를 쓰려면 android.car 라이브러리 연결이 따로 필요해 1차에서는 쓰지 않았다.
- 대신 앱 내부 주행 상태 판정(`pleos/Driving.kt`의 주차 확인)으로 화면을 나눈다: 주차가 아니면 요약 패널(SC-04)만 보이고 버튼이 없다. 버튼 동작도 세션이 주차를 다시 확인한다.
- manifest의 MainActivity에 `distractionOptimized=true` 메타데이터를 선언했다. 주차 확인과 화면 조작 가능 여부는 따로 판단한다(설계서 5절)는 기준은 실차의 CarUxRestrictions로만 확인할 수 있다 [실측필요].
