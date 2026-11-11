---
name: bootstrap
description: 저장소 최초 1회 설정. Android Studio로 만든 빈 프로젝트에 하네스를 합치고 GitHub 원격 저장소 연결, git hook 활성화, 첫 push까지 진행한다. "부트스트랩", "초기 세팅 해줘" 요청 시 사용.
disable-model-invocation: true
---

# 최초 설정 (사람이 /bootstrap 으로만 실행)

## 확인할 것 (하나라도 없으면 멈추고 무엇이 없는지 알려준다)
- 현재 폴더에 gradlew, app/build.gradle.kts, settings.gradle.kts 가 있다 (Android Studio "Empty Activity", Kotlin DSL, package io.backseatlab.playground, minSdk 28)
- `gh auth status` 성공, `adb version` 성공, `java -version` 성공

## 1. Git
- git hook 경로는 세션 시작 hook이 자동으로 `scripts/git-hooks`로 설정한다. 직접 설정하지 말고 `git config --get core.hooksPath`로 확인만 한다(설정 명령은 guard가 막음)
- 커밋 작성자는 각자 자기 GitHub 계정: `git config user.name "<GitHub 사용자명>"`, `git config user.email "<GitHub noreply 이메일>"`(GitHub Settings > Emails의 `숫자+사용자명@users.noreply.github.com`, 실제 이메일 노출 방지)
- .gitignore / .gitattributes 가 하네스 것으로 되어 있는지 확인. scripts/ 와 scripts/git-hooks/ 파일에 실행 권한(`git update-index --chmod=+x`)

## 2. 빈 안드로이드 프로젝트 합치기
- 저장소(hyundai-backseat-lab/backseat-playground)에는 하네스와 docs/가 이미 올라가 있고, 사람은 그것을 clone한 폴더에서 이 스킬을 실행한다.
- 사람에게 Android Studio로 임시 폴더(예: C:\temp\backseat-playground-template)에 "Empty Activity" 프로젝트를 만들게 한다(Kotlin DSL, package io.backseatlab.playground, Minimum SDK 28).
- 그 폴더의 gradlew, gradlew.bat, gradle/, settings.gradle.kts, build.gradle.kts, gradle.properties, app/ 를 clone 폴더로 복사한다(.gitignore·README는 하네스 것을 유지). local.properties는 복사하지 않고 사람에게 Android Studio로 한 번 열게 해서 생성되게 한다.
- `./gradlew --version`과 `./scripts/check.sh`가 돌아가는지 확인한다.
- `git switch -c feat/F-00-skeleton` 후 `git add -A` → 커밋(chore: 빈 안드로이드 프로젝트) → push. 이후 작업은 전부 기능 브랜치에서 한다.

## 3. 장치 동작 확인 (Windows에서 hook 경로가 실제로 동작하는지)
- Claude가 `git push origin HEAD:main`을 시도 → "차단됨"이 나와야 정상
- 임시 테스트 파일에 일부러 실패하는 단위 테스트를 만들고 턴을 끝내려 해 본다 → Stop hook이 막아야 정상. 확인 후 그 파일을 지운다
- 둘 중 하나라도 막히지 않으면 settings.json hook 경로(`${CLAUDE_PROJECT_DIR}`의 Windows 경로 문제)를 고친다

## 4. 다음 단계 안내
- docs/features.md 의 F-00 상태가 [approved]인지 확인하고, 사람에게 "F-00 진행할까요?" 한 줄로 묻는다.

참고: GitHub Free 조직의 private 저장소는 브랜치 보호 규칙을 쓸 수 없다. 그래서 main 보호는 로컬 장치(guard-bash.sh, pre-commit, pre-push, deny 규칙)로 한다.
