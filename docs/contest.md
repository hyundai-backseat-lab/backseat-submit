# 현대자동차그룹 차량용 앱 개발 공모전 — 핵심 정리 (2026-11-05 기준)

출처: 공식 가이드(appdevchallenge.auto-hmg.io/guide, /faq, /guide/submission, /guide/judging-awards, /guide/rules), 10/28 통합 온라인 설명회 스크립트, Pleos Playground 개발 문서(document.pleos.ai)

## 1. 일정
- 1차 접수 마감: 2026-11-15(일) 23:59 — 기획서(접수양식)+전원 서명 동의서 필수, 이메일 appdevchallenge@hyundai-partners.com
- 1차 서류 결과: 11-23
- 2차 최종 PT(오프라인, 강남대로사옥 1층 UX 스튜디오 서울): 12-03~04 — 이때까지 "실제 동작하는 앱" 완성 필수, 현장 직접 시연
- 최종 선정 발표: 12-18 (순위는 2027-02 행사 현장 공개)
- 12-19~1월 중순: 전시 준비(신규 기능 개발 아님, 전시 영상·현장 APK 구동 테스트)
- PT 이후 추가 개발은 순위에 영향 없음 → PT 시점 결과물이 최종 평가 대상

## 2. 심사 기준 (세부 배점 비공개, 4개 모두 필수)
- 차량특화성: 모바일 대비 차량만의 가치, 주행·정차 맥락/차량 데이터·하드웨어와 UX 결합
- 창의성: 독창성, 운전자·동승자 Pain point 정의와 해결 방식의 명확성
- 기술 활용성: Pleos Playground SDK·API를 "핵심 기능"에 적절히 활용 (많이 쓰는 게 목적 아님)
- 사업성: 타깃 고객, 예상 MAU, Retention, 확대 계획
- 가산점: 1차 접수 시 시연 영상(에뮬레이터 화면 녹화 인정) / GitHub 저장소(소스+APK) 제출 시 "단계별" 가산점

## 3. 제출 규칙
- 메일 제목: [차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명 / 수정 시 앞에 [수정제출]
- 팀원 전원 CC, 수정 제출 시 전체 자료+동의서 재첨부, 마감 전 마지막 제출본이 최종
- APK는 이메일 첨부 금지 → GitHub 링크로 제출
  - 위치: 저장소 apk/ 폴더 (용량 초과 시 GitHub Releases)
  - 실행 가능한 release 빌드, 파일명: 팀명_앱이름_release.apk
- README 필수 항목: 앱 이름, 한 줄 소개, 실행 방법, 테스트 환경·필요 설정, APK 위치/Release 링크
- 비공개 저장소 가능: 공지된 심사용 계정을 Collaborator로 초대(쓰기 권한 부여됨 주의), 마감 전 접근 확인
- 첨부·링크에 개인정보·계정정보·개발자 실명 노출 금지

## 4. 참가·운영 규정
- 개인 또는 최대 5인 팀, 일반인 1명이라도 있으면 일반 부문
- 복수 출품 가능(개수 제한 없음)
- 생성형 AI: 기획·개발 전 과정 사용 가능. 단, 참가자는 전체 구조·로직을 완벽히 이해하고 2차 PT 기술 Q&A에서 직접 설명할 수 있어야 함
- 실차 시연 없음 → 검증은 Pleos Connect 에뮬레이터
- 외부 API·퍼블릭 네트워크 사용 가능
- 숏폼 챌린지(선택, 가산점 없음): 30초~1분 세로 영상, #HMG차량용앱개발공모전 포함 해시태그 2~3개, 50만 원(설명회 구두 500만 원은 오기, 공식 50만 원)

## 5. 개발 환경 (Pleos Connect)
- AAOS 기반, Kotlin/Java, minSdk 28+, compileSdk 34, Gradle 8.0+
- Pleos Connect Emulator 2.1.0 (Android Studio SDK Update Site 등록)
  - Win/Linux: https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml
  - Mac(Apple Silicon): https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml
  - AVD: Automotive / Pleos Connect v2.1.0 / API 34 / Cold boot / RAM 4GB
- Virtual Console: 기어·도어·배터리 등 차량 상태를 가상 변경 (Emulator 2.1.0+)
- Maven: https://nexus-playground.pleos.ai/repository/maven-releases/
- 의존성 예: ai.pleos.playground:Vehicle:2.3.1
- 초기화 패턴: Vehicle(context) → initialize() → getXxx().getCurrent...(onComplete, onFailed) → onDestroy에서 release()
- 권한(Vehicle): pleos.car.permission.CAR_LIGHTS(방향지시등), CAR_DRIVE(브레이크·주행모드), CAR_SAFETY(Safety·Tire) 등. 미선언 시 onFailed에 ERROR_SECURITY_PERMISSION
- 공모전 개발 단계: 권한 승인 없이 manifest 선언만으로 Vehicle/NaviHelper/STT/TTS/FusedLocation 사용 가능(설명회 기술 Q&A)
- STT/TTS(SDK v3.3.1) 모드: HYBRID(기본, 서버 우선+온디바이스 폴백, 앱 등록 필요) / SERVER(앱 등록 필요) / ON_DEVICE(차량 온디바이스 엔진, 네트워크·앱 등록 불필요)
  → 우리 앱은 ON_DEVICE 고정. 미확인: 에뮬레이터 ON_DEVICE 한국어 지원, AVD 마이크 설정, 아이 목소리 인식률 → 착수 첫날 spike로 검증
- 공모전 개발환경 페이지(/guide/development) 예시: 음성 메모 앱(STT), 안전운전 점수 앱(Vehicle), 배달 기사용 앱(NaviHelper)
- SDK 문서: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/ / API 참조: https://document.pleos.ai/api-reference/connect-sdk-pleos / Vehicle 튜토리얼: https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk
- 관련 문서: Getting Started https://document.pleos.ai/en/docs/pleos-only/getting-started
