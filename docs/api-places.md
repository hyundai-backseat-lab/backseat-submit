# 장소 검색 중계 API 계약 (F-16)

앱은 카카오 API를 직접 부르지 않는다. 카카오 키가 APK 안에 들어가면 누구나 꺼낼 수 있기 때문이다. 앱은 이 중계 API만 부르고, 키는 중계 서버(이 저장소 밖)에만 둔다.

## 요청
```
GET {PLACES_API_URL}/api/places/ahead?lat={위도}&lng={경도}&heading={진행 방향(도, 0=북, 시계 방향)}&category={분류, 선택}
```
- `PLACES_API_URL`: 저장소에 올리지 않는 `local.properties`의 값이 빌드 때 BuildConfig로 들어간다. 비어 있으면 앱은 요청하지 않는다.
- 1차 개발·시연: 에뮬레이터는 PC의 로컬 서버를 `http://10.0.2.2:8080`으로 부른다. 평문 HTTP는 debug 빌드에서 `10.0.2.2`에만 허용한다(`app/src/debug/res/xml/network_security_config.xml`).

## 응답 (200)
```json
[
  {"name": "국밥집", "category": "FOOD", "lat": 37.5671, "lng": 126.9783, "distanceM": 210}
]
```
- 진행 방향 좌우 45° 안, 150~500m 장소를 가까운 순으로 최대 3개. 서버가 고른다.
- 앱은 `name`, `category`, `distanceM`만 게임에 넘긴다(`game/api` `Place`). 장소 좌표는 게임에 넘기지 않는다.

## 실패
- 서버 오류는 빈 배열 + 상태 코드(4xx·5xx). 앱은 200이 아니면 실패로 본다.
- 앱 쪽 규칙(`place/HttpPlaceSource`): 주소 없음·위치 모름·연결 실패·200 아님·JSON 오류·2초 초과면 빈 목록을 돌려준다. 게임은 빈 목록이면 데이터 없는 미션을 낸다. 서버가 꺼져 있어도 이용은 끊기지 않는다.

## 개인정보
- 앱은 좌표를 저장하지 않고 로그에도 남기지 않는다. 로그는 `PLACE fetch count=n ms=...`, `PLACE fallback reason=...`까지.
