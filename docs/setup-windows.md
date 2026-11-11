# 개발 환경 세팅 가이드 (Windows) — 팀원 공용

목표: Windows PC 한 대에서 Android Studio + Pleos Connect 에뮬레이터 + Claude Code + GitHub가 모두 동작하는 상태
소요: 약 1.5~2시간 (다운로드 시간 대부분)
원칙: Linux VM이나 WSL은 쓰지 않습니다. 에뮬레이터와 adb가 Windows에서 돌기 때문에 Claude Code도 Windows에 직접 설치해야 빌드·설치·로그 확인까지 한 흐름으로 이어집니다.

---

## STEP 1. PC 점검 (5분)
- [ ] RAM 16GB 이상 권장 (에뮬레이터 4GB + Android Studio + Gradle). 8GB면 Android Studio를 닫고 에뮬레이터만 띄우는 식으로 운영
- [ ] C 드라이브 여유 공간 30GB 이상
- [ ] 작업 관리자 > 성능 > CPU > "가상화: 사용" 확인. "사용 안 함"이면 BIOS에서 Intel VT-x / AMD SVM 켜기
- [ ] "Windows 기능 켜기/끄기"에서 "Windows 하이퍼바이저 플랫폼" 체크 → 재부팅 (에뮬레이터 가속용)

## STEP 2. 기본 도구 설치 (20분)
PowerShell에서 실행합니다(관리자 권한 불필요).

```powershell
winget install Git.Git
winget install GitHub.cli
```

- [ ] Android Studio 최신 버전 설치: https://developer.android.com/studio
  - 첫 실행 마법사에서 Standard 선택 → Android SDK, Platform-Tools, Emulator 자동 설치

## STEP 3. 환경 변수 (5분)
Claude Code가 터미널에서 gradlew·adb를 직접 실행하려면 필요합니다.
"시스템 환경 변수 편집" > 사용자 변수에 추가:

| 변수 | 값 |
|---|---|
| JAVA_HOME | C:\Program Files\Android\Android Studio\jbr (이미 JDK 17 이상, 예: jdk-21이 설정되어 있으면 그대로 둔다) |
| ANDROID_HOME | C:\Users\<사용자명>\AppData\Local\Android\Sdk |
| Path 추가 | %ANDROID_HOME%\platform-tools |
| Path 추가 | %ANDROID_HOME%\emulator |

새 PowerShell 창을 열고 확인:

```powershell
java -version
adb version
emulator -version
```

## STEP 4. Pleos Connect 에뮬레이터 (30~40분)
출처: https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator

1. Android Studio > Tools > SDK Manager > SDK Update Sites 탭 > [+]
   - Name: Pleos Connect System Image
   - URL: https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml
2. SDK Platforms 탭 > 우측 하단 "Show Package Details" 체크 > Android API 34 아래 Pleos Connect 시스템 이미지 선택 > OK
3. Android Studio 재시작
4. Device Manager > [+] Create Virtual Device
   - Form factor: Automotive
   - Device: Pleos Connect v2.1.0
   - System image: API 34 / Pleos Connect v2.1.0 APIs
   - Advanced Settings: Default boot = Cold, RAM = 4GB
5. 실행해서 Pleos 홈 화면이 뜨는지 확인 → 스크린샷 저장
6. **마이크 스위치 켜기(음성 기능 테스트에 필수):** 에뮬레이터 창 옆 `⋯`(Extended controls) > **Microphone** > **Enable Host Microphone Access**를 켠다. 기본은 꺼져 있고, 꺼져 있으면 STT가 아무것도 듣지 못하고 녹음도 무음이다. Windows 설정 > 개인 정보 및 보안 > 마이크에서 "데스크톱 앱이 마이크에 액세스하도록 허용"도 켜져 있어야 한다. 에뮬레이터를 껐다 켠 뒤에도 켜져 있는지 확인한다(재시작 후 유지 여부는 미확인).
7. Virtual Console 사용법 문서 확인: https://document.pleos.ai/en/docs/pleos-only/app-test-guide/virtual-console-guide

확인:

```powershell
emulator -list-avds
adb devices
```

## STEP 5. Pleos Playground 계정·프로젝트 (10분)
1. https://pleos.ai/playground 가입 → Workspace > My Project > 프로젝트 생성
2. Project Information에서 Project ID / Client ID / Client Secret 확인
3. SDK 탭에서 TextToSpeech, SpeechToText 상태를 캡처 (우리 앱의 최대 리스크 — 사용 가능 여부 판단 자료)
4. Client Secret은 절대 Git에 올리지 않습니다. 나중에 local.properties(커밋 제외 파일)에만 넣습니다.

## STEP 6. Claude Code (10분)
요건: Claude Pro / Max / Team / Enterprise 플랜 (무료 플랜 불가)
출처: https://code.claude.com/docs/en/setup

```powershell
irm https://claude.ai/install.ps1 | iex
```

새 PowerShell 창에서:

```powershell
claude --version
claude doctor
```

- Git for Windows가 설치되어 있으면 Claude Code가 Git Bash를 셸로 사용합니다(STEP 2에서 설치됨).
- 처음 `claude` 실행 시 브라우저 로그인.

## STEP 7. GitHub (10분)

```powershell
gh auth login
git config --global user.name "<GitHub 사용자명>"
git config --global user.email "<GitHub 이메일>"
git config --global core.autocrlf true
```

- [ ] (대표 1명) GitHub Organization 생성 (Free 플랜): github.com > Your organizations > New organization
- [ ] 팀원 초대 (Owner 또는 Member)
- [ ] 저장소는 아직 만들지 않습니다 — 앱명 확정 후 Claude Code로 함께 생성
- [ ] 심사용 계정명은 공식 공지사항에서 확인 후 제출 직전에 초대 (쓰기 권한이 같이 부여되므로 브랜치 보호 규칙을 먼저 설정)

---

## 완료 확인 — 아래 결과를 Claude에게 전달
PowerShell에서 한 번에 실행 후 출력 전체를 복사해 주세요.

```powershell
git --version; gh --version; gh auth status; java -version; adb version; emulator -list-avds; claude --version
```

추가로 전달할 것:
- 에뮬레이터 홈 화면 스크린샷
- Playground SDK 탭 캡처 (특히 STT/TTS 상태)
- PC 사양: CPU / RAM
