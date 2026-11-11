# 개발 환경 세팅 가이드 (Mac) — setup-windows.md의 Mac판

목표와 순서는 Windows판과 같다. 다른 부분만 적는다.

## STEP 1. PC 점검
- 메뉴 > 이 Mac에 관하여에서 칩 확인: Apple M1~M4(Apple Silicon)인지 Intel인지 → STEP 4에서 쓰는 주소가 다르다
- 메모리 16GB 이상 권장, 디스크 여유 30GB 이상
- 가상화 설정은 필요 없다(macOS에 내장)

## STEP 2. 기본 도구
터미널(Terminal 앱)에서:

```bash
xcode-select --install          # git 포함 기본 개발 도구 (이미 있으면 건너뜀)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"   # Homebrew 없을 때만
brew install gh coreutils       # GitHub CLI, timeout 명령(gtimeout)
```
- Android Studio 최신 버전: https://developer.android.com/studio (Mac용 dmg, 칩 종류에 맞는 것)

## STEP 3. 환경 변수
`~/.zshrc` 맨 아래에 추가하고 터미널을 새로 연다.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator"
```
확인: `java -version`, `adb version`, `emulator -version`

## STEP 4. Pleos Connect 에뮬레이터
Windows판과 같고, SDK Update Site 주소만 칩에 맞게:
- Apple Silicon(M1~M4): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
- Intel Mac: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`

## STEP 5. Pleos Playground — Windows판과 동일

## STEP 6. Claude Code

```bash
curl -fsSL https://claude.ai/install.sh | bash
claude --version
claude doctor
```

## STEP 7. GitHub

```bash
gh auth login
git config --global user.name "<GitHub 사용자명>"
git config --global user.email "<GitHub 이메일>"
git clone https://github.com/hyundai-backseat-lab/backseat-playground.git
```
(Windows판의 `core.autocrlf` 설정은 Mac에서는 하지 않는다)

## 완료 확인 — 아래 결과를 Claude에게 전달

```bash
git --version; gh --version; gh auth status; java -version; adb version; emulator -list-avds; claude --version; gtimeout --version | head -1
```

## 참고
- 저장소 스크립트는 Mac 기본 bash(3.2)와 BSD 도구에서도 돌도록 작성했다. `timeout`이 없으면 `gtimeout`(coreutils)을 쓰고, 그것도 없으면 시간 제한 없이 검사한다.
- 알림(notify.sh)은 Mac에서는 시스템 알림 센터로 뜬다.
