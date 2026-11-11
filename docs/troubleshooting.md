# 문제 해결 기록

개발 중 실제로 겪은 문제만 적는다. 형식: 증상 / 단서 / 원인 / 해결 / 재발 방지. 확인하지 못한 것은 "미확인"으로 적는다.

## 1. 에뮬레이터에서 마이크가 무음이다

- **증상**: 에뮬레이터에서 말해도 STT가 결과를 주지 않고, 앱이 30초 뒤 `STT end code=NO_RESPONSE`로 끝낸다. 3초 녹음은 에러 없이 끝나지만 소리가 없다.
- **단서**
  - 로그: `STT ready`는 찍히지만 `STT speech-begin`(`onStartedRecognition`)과 `STT end code=OK`가 없다.
  - 에뮬레이터 시작 로그에 `Allowing host microphone input`이 찍혀도 같은 증상이었다.
  - 녹음 파일 크기(`REC saved ... bytes=`)는 무음일 때 7,722, 소리가 들어왔을 때 7,822로 거의 같았다.
- **원인**: Extended Controls > Microphone > **Enable Host Microphone Access**가 기본으로 꺼져 있다. 아래는 모두 시도했으나 해결되지 않았다: Windows 마이크 접근 허용, `emulator -allow-host-audio`로 재시작, 입력 장치(이어폰) 변경 후 재시작.
- **해결**: 위 스위치를 켠다. 에뮬레이터를 재시작하지 않고 켠 직후 점검에서 `STT end code=OK heard="..."`와 `REC amp max=4722`가 나왔다.
- **재발 방지**
  - docs/setup-windows.md STEP 4에 스위치 켜기를 넣었다.
  - **녹음 파일 크기(bytes)는 무음 판정 근거가 아니다.** 3초 고정 길이·고정 비트레이트라 소리와 무관하게 비슷하다. 판정은 `STT end code`·`heard` 문장·`STT start`~`STT end` 지연, `REC amp max=`(0이면 무음)와 녹음 파일 청취로 한다.
  - `NO_RESPONSE`는 SDK 값이 아니라 앱의 30초 상한 코드다. 무음·마이크 불통·엔진 대기 중 어느 쪽인지는 이 값만으로 알 수 없다.
  - 에뮬레이터를 껐다 켠 뒤 스위치가 유지되는지는 미확인이다.

## 2. Gradle `fileHashes` 락 타임아웃

- **증상**: Gradle 실행(`./gradlew`, `./scripts/check.sh` 등)이 캐시 락을 기다리다 타임아웃으로 실패한다.
- **단서**: 오류 메시지에 `fileHashes` 락 타임아웃 문구가 나온다(메시지 원문은 이 문서 작성 때 보관돼 있지 않다. 다음에 발생하면 원문을 여기에 추가한다).
- **원인**: Android Studio와 Gradle 데몬이 같은 Gradle 캐시를 붙잡고 있다.
- **해결**
  1. Android Studio를 종료한다.
  2. `./gradlew --stop`으로 Gradle 데몬을 멈춘다.
  3. `jps`로 남아 있는 Gradle 관련 Java 프로세스가 없는지 확인한다.
  4. 명령을 다시 실행한다.
- **재발 방지**(권장): 터미널에서 빌드할 때는 Android Studio의 동기화·빌드가 끝난 뒤에 실행하거나 Android Studio를 닫아 둔다.

## 3. Git Bash 경로 `/c/dev`가 Windows 도구에서 `C:\c\dev`로 해석된다

- **증상**: Git Bash에서 `/c/dev/...` 경로를 Windows 프로그램에 넘기면 파일을 찾지 못하고, 오류 메시지에 `C:\c\dev\...`가 나온다.
- **단서**: 오류의 경로 앞부분이 `C:\c\dev`로 시작한다.
- **원인**: `/c/dev`는 Git Bash(MSYS) 안에서만 쓰는 표기다. Windows 프로그램은 드라이브 문자 없는 `/c/dev`를 현재 드라이브 루트 기준 경로로 읽어 `C:\c\dev`가 된다.
- **해결**
  - Windows 프로그램에는 `C:/dev/...` 또는 `C:\dev\...`를 넘긴다.
  - Git Bash 안에서 변환하려면 `cygpath -w <경로>`를 쓴다.
- **재발 방지**
  - 스크립트는 `cd "$(dirname "$0")/.."` 뒤 상대 경로를 쓴다.
  - 반대 방향 문제(Git Bash가 `adb`에 넘기는 `/sdcard` 같은 기기 경로를 Windows 경로로 바꿔 버림)는 `MSYS_NO_PATHCONV=1`로 막는다. `scripts/sim.sh`와 `scripts/verify-emulator.sh`에 설정돼 있다.

## 4. 가드가 `브랜치 생성 && 커밋`을 한 명령에서 막는다

- **증상**: `git switch -c <브랜치> && git commit ...`처럼 한 명령으로 실행하면 "main 브랜치에서 커밋할 수 없습니다"로 차단된다. `git push`와 문자열 `main`이 한 명령에 함께 있으면(예: `git push ...; gh pr create --base main ...`) "main으로 push할 수 없습니다"로 차단된다.
- **단서**: 차단 메시지(`scripts/guard-bash.sh`)가 나오고, **그 명령 전체가 실행되지 않는다.** 같은 명령 앞부분에서 만들려던 파일(예: heredoc로 쓰는 PR 본문)도 만들어지지 않는다.
- **원인**
  - 가드는 명령을 **실행하기 전**에 현재 브랜치를 읽는다. 명령 안에서 새 브랜치로 옮겨도 검사 시점의 브랜치는 `main`이라 커밋이 차단된다.
  - `git ... push`가 들어 있는 명령에서는 `main`/`master` 토큰이 어디에 있든(`--base main` 포함) 차단한다.
  - 의심되는 문자열이 있으면 막는 쪽으로 설계돼 있다(스크립트 주석).
- **해결**: 명령을 나눈다.
  1. 브랜치 생성(`git switch -c ...`)
  2. 커밋
  3. `git push -u origin <브랜치>`
  4. `gh pr create ...`(필요하면 `--base`를 생략해 기본 브랜치를 쓴다)
  - 파일 만들기는 push 명령과 섞지 않는다.
- **재발 방지**: docs/features.md 기능 절차의 순서(브랜치 → 커밋 → push → PR)를 명령 단위로도 지킨다.
