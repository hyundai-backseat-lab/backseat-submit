# 벌써도착 (backseat-playground)

> "아빠, 언제 도착해?"에서 "벌써 도착했어?"로 — 뒷좌석 아이와 음성으로 노는 차량용 앱

## 요구사항과 설계 문서

- [공통 이용 흐름 요구사항](docs/backseat-core-requirements.md): 이용 준비부터 종료까지 다섯 단계의 필수 기능과 동작 정책
- [공모전용 아키텍처 설계서](docs/backseat-system-architecture.md): 출품 범위, 공통 이용 흐름, Pleos 연동과 게임 연결 계약

설계서 1절에는 요구사항과 다르게 적용하는 출품 정책이 정리되어 있습니다. 현재 코드 구조는 [구조 안내](docs/architecture.md), 기존 기능 완료 기준은 [기능 목록](docs/features.md)에서 확인할 수 있습니다.

## 한 줄 소개
주차 중 아이가 고른 놀이를, 주행 중에는 화면 없이 목소리로만 이어 가는 뒷좌석 놀이 앱입니다. 방향지시등·브레이크·경고 같은 차량 신호에 맞춰 스스로 멈추고, 도착하면 "벌써 도착했어요!"로 마무리합니다.

## 실행 방법
1. Android Studio에서 이 저장소를 열고 Pleos Connect Emulator(아래 환경)를 켭니다. 설정은 [Windows](docs/setup-windows.md) / [Mac](docs/setup-mac.md).
2. APK를 설치합니다: `adb install -r apk/BackseatLab_BackseatPlayground_release.apk` (소스에서 빌드하려면 `./gradlew assembleRelease`).
3. 앱 "벌써도착"을 열고, Virtual Console에서 주차(P단)로 둔 뒤 마이크 권한을 허용하고 시작 버튼을 누릅니다.
4. 출발(D단)·방향지시등·브레이크를 바꿔 보면 앱이 멈추고 다시 이어 갑니다. 내비 도착이나 주차로 마무리하면 결과 화면이 나옵니다.

디버그 빌드에서는 `./scripts/sim.sh <이벤트>`로 차량 신호를 흉내 낼 수 있습니다(예: `brake-on`, `turn-left`, `park-on`, `nav-arrive`).

## 테스트 환경 · 필요한 설정
- Pleos Connect Emulator 2.1.0, AVD: Automotive / Pleos Connect v2.1.0 / API 34 / Cold boot / RAM 4GB
- 앱: minSdk 28, Kotlin, 한국어 전용. 음성 인식·합성은 ON_DEVICE 모드(앱 등록·네트워크 불필요)
- 마이크 권한 필요. 위치 기반 장소 미션은 앱 안의 동의 버튼을 눌렀을 때만 켜지며, 동의 전이나 서버가 없으면 기본 미션으로 진행합니다([중계 API](docs/api-places.md)).
- 개발 도구: Android Studio 최신, Gradle 래퍼 포함

## APK
- 위치: `apk/BackseatLab_BackseatPlayground_release.apk` (release 서명 빌드)

## 개발 방식
이 저장소는 Claude Code 하네스로 개발합니다. 팀원은 docs/harness.md를 먼저 읽어 주세요.
