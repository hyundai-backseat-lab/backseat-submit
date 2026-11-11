# NaviHelper 확인 기록 (F-05)

확인 방법: 공식 문서(doc-scout) + Pleos Maven `NaviHelper-3.0.5.aar`의 classes.jar를 `javap`로 읽고, Pleos Connect 에뮬레이터에서 실행(2026-11-11).
표기: [확인] AAR·에뮬레이터에서 확인 / [실측필요] 사람이 확인해야 함

## 의존성·권한
- `ai.pleos.playground:NaviHelper:3.0.5` [확인]. 함께 받는 것: `navi-aidl:3.0.1`, `navi-data:3.0.4`.
- 문서의 권한: `pleos.car.permission.NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`, `NAVI_CUSTOM_MAP`. 도착 이벤트에 필요한 권한은 문서에 없다. 앱은 `NAVI_ROUTE`만 선언했다 [실측필요: 실차에서 이것만으로 도착 이벤트가 오는지].

## 사용 형태 [확인: javap]
```kotlin
val navi = NaviHelper(context)                       // ai.pleos.playground.navi.helper
navi.initialize(onConnected = { navi.addListener(listener) }, onFailed = { e -> })
// listener: NaviHelperEventListener (모든 콜백이 default 메서드라 필요한 것만 구현)
override fun onDestinationArrived(info: DestinationArrivedInfo)   // ai.pleos.playground.navi.data
navi.removeListener(listener); navi.release()
```
- 문서: 다른 API는 initialize가 끝난 뒤에만 정상 동작한다. 그래서 onConnected 안에서 addListener를 부른다.
- 에뮬레이터: 앱 시작 때 `NAV init done`(onConnected) 확인.

## 에뮬레이터에서 도착 재현
- 에뮬레이터 내비로 목적지를 정해 주행을 재생해 실제 `onDestinationArrived`를 받는 것은 확인하지 못했다 [실측필요]. Virtual Console에도 내비 도착은 없다(domain.md 10번 8).
- 시연은 debug `sim.sh nav-arrive`(로그 `SIM nav=ARRIVED (시뮬레이션)`)로 도착을 주입한다. 실제 연동과 구분해 표시한다.
- 남은 시간·회전 안내 필드(domain.md 10번 5)는 이번 기능에서 쓰지 않아 확인하지 않았다.

## 현재 위치 (F-11) [확인: javap, 에뮬레이터]
```kotlin
navi.getCurrentLocationInfo()                                         // 결과는 콜백으로
override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo)
// CurrentLocationInfo(navi-data 3.0.4): latitude, longitude, heading(Double), address, routeId, currentRouteIndex
```
- 에뮬레이터: 답은 바로 오지만 `adb emu geo fix`·route.sh를 따라가지 않는 고정 위치, heading 0.0이었다. 그래서 GPS를 먼저 쓴다(D-031).
- 권한: 공식 문서에 없음. NAVI_ROUTE만 선언한 상태에서 응답을 받았다.
- 실차에서 heading이 실제 진행 방향인지는 [실측필요].
