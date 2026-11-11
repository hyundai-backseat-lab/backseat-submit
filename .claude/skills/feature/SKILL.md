---
name: feature
description: docs/features.md의 기능 하나(F-번호)를 브랜치 생성 → 테스트 먼저 → 구현 → 에뮬레이터 검증 → 리뷰 → PR까지 진행하는 표준 절차. "F-03 해줘", "다음 기능 진행", "기능 구현해줘", 오토파일럿 실행 시 사용.
argument-hint: F-번호
---

# 기능 구현 절차

대상: $ARGUMENTS (없으면 docs/features.md에서 가장 위의 [approved] 항목)

## 0. 시작 조건
- 대상 항목 상태가 [approved]가 아니면 멈추고 그 사실만 보고한다. 완료 기준을 스스로 만들거나 바꾸지 않는다.
- 항목의 완료 기준, 만들지 않을 것, 시연 시나리오를 읽는다. 관련 docs/sdk-notes/도 읽는다.

## 1. 브랜치
- 기준 브랜치: 프롬프트에 지정이 있으면 그것, 없으면 main. `git switch <기준> && git pull --ff-only` 후 `git switch -c feat/<F-번호>-<짧은-영문-이름>`
- 바로 `git push -u origin <브랜치>`로 원격에 브랜치를 만들어 다른 사람과 같은 기능을 집지 않게 선점한다.
- features.md 상태를 [in-progress]로 바꾼다.

## 2. 테스트 먼저
- 완료 기준 하나당 테스트 하나 이상을 `app/src/test/`에 쓴다. 차량 SDK는 pleos/fake의 FakePleosClient로, 게임은 session 테스트의 FakeGame으로 대체한다.
- 테스트 이름은 한국어 백틱 이름으로 기준을 그대로 드러낸다. 예: `` `브레이크 신호가 오면 TTS가 즉시 멈춘다`() ``
- `./scripts/check.sh` 실행 → 새 테스트가 실패하는 것을 확인한다(이 상태로는 커밋하지 않는다. pre-commit이 막는다).

## 3. 구현
- 테스트를 통과시키는 최소 구현. 완료 기준에 없는 것은 만들지 않는다. 코드 구조·이름은 team-android-convention, SDK 호출은 pleos-api-guide를 따른다.
- SDK 사용법이 불확실하면 doc-scout 에이전트에 묻고, 확인한 내용을 docs/sdk-notes/<SDK>.md에 남긴다.
- `./scripts/check.sh` 통과 → 테스트와 구현을 함께, 의도 단위로 커밋 `feat(<영역>): ...`
- 같은 오류로 세 번 실패하면 접근을 바꾼다. 계속 밀어붙이지 말고 계획으로 돌아가 원인을 다시 적는다.

## 4. 실화면 검증 (UI·실행 동작이 있는 기능만)
- verify-car 스킬로 시연 시나리오의 각 단계를 실행한다.
- 에뮬레이터가 없으면(SKIP) PR 본문에 "실화면 검증 미실시"로 명시한다. 통과한 것처럼 쓰지 않는다.
- 대표 스크린샷 1~2장을 docs/screens/<F-번호>-*.png로 복사해 커밋한다.

## 4-1. 화면 검토 (ui/를 바꾼 기능만)
- driving-ux-check 스킬로 주행·정차·주차 세 상태를 검토하고 결과 표를 PR 본문 "검증"에 붙인다.

## 5. 독립 리뷰
- `git diff main...HEAD > build/review.diff` 후 reviewer 에이전트에게 검토를 맡긴다.
- MUST 항목은 고치고 다시 check.sh. SHOULD는 판단해서 고치거나 PR에 이유를 적는다.

## 6. 문서
- features.md 상태를 [pr]로, docs/progress.md 맨 위에 한 줄 기록(날짜, F-번호, 결과).
- 성적표는 고치지 않는다(scripts/score.sh가 머지 후 자동 계산). PR 본문 "목표 기여"에 근거를 적는다.
- docs/progress.md는 맨 아래에 한 줄 추가한다(병렬 PR 충돌 방지).
- 설계 선택이 있었다면 docs/decisions.md에 추가.
- 새 진입점·패키지·의존이 생겼으면 docs/architecture.md를, 새 용어가 생겼으면 docs/glossary.md를 갱신한다. 의존 그래프는 `./scripts/check-docs.sh --write`로 다시 그리고 의도와 맞는지 본다.

## 7. PR
- `git push -u origin <브랜치>` (pre-push hook이 빌드·Lint를 검사한다. 실패하면 고친다.)
- `gh pr create --base <기준 브랜치> --title "<F-번호> <기능 이름>" --body-file build/pr-body.md`
- PR 본문은 .github/pull_request_template.md 형식을 따르며, 특히 다음을 채운다.
  - 완료 기준 체크리스트: 각 줄에 근거(테스트 이름 또는 스크린샷 경로)
  - 이번 PR에서 배울 점 3줄: 어떤 개념·SDK·패턴을 썼고 왜 그렇게 했는지, 비개발자도 이해할 수준으로
  - PT 예상 질문 2개와 답
- 머지하지 않는다. 사람이 확인하고 머지한다.

## 막혔을 때
- 해결할 수 없는 원인(SDK 미지원, 환경 문제, 기준 모호)이면 features.md 해당 항목에 `막힘: <원인과 시도한 것>`을 쓰고 상태를 [blocked]로 바꾼다.
- 커밋 가능한 부분까지 커밋 → push → `gh pr create --draft`로 올려 사람이 볼 수 있게 한다.

## 끝내기 전 보고 (5줄 이내)
PR 링크 / 통과한 완료 기준 수 / 실화면 검증 여부 / 리뷰 MUST 처리 결과 / 사람이 봐야 할 점
