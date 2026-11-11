# 하네스 안내 — 팀원용 (5분)

이 저장소는 "Claude가 만들고, 장치가 검사하고, 사람은 승인만 한다"는 방식으로 개발한다.
지시문(CLAUDE.md)은 확률을 높일 뿐이고, 꼭 지켜야 하는 규칙은 장치(hook·deny·CI)가 확정적으로 막는다.

## 사람이 하는 일은 두 가지
1. 기능 승인: `/spec <아이디어>` → Claude가 완료 기준·만들지 않을 것·시연 시나리오를 docs/features.md에 [draft]로 작성 → 읽고 OK하면 [approved]
2. PR 확인·머지: PR 본문의 완료 기준 근거·스크린샷·"배울 점 3줄"·"PT 예상 질문"을 보고 GitHub에서 머지

## Claude가 알아서 하는 일
`/feature F-03` (대화형) 또는 `./scripts/autopilot.sh` (무인, 승인된 기능 전부)
브랜치 → 테스트 먼저 → 구현 → check.sh → 에뮬레이터 실화면 검증 → reviewer 에이전트 독립 리뷰 → 문서 갱신 → push → PR

## 장치 지도 (어디서 무엇이 막히나)
| 시점 | 장치 | 막는 것 | 토큰 사용 |
|---|---|---|---|
| 명령 실행 전 | PreToolUse `guard-bash.sh` | main push, 강제 push, `--no-verify`, main 커밋, 비밀값 읽기 | 없음 |
| 파일 접근 | `settings.json` deny | local.properties, 서명 키, apk/ 수정, PR 머지 | 없음 |
| Claude가 턴을 끝낼 때 | Stop `stop-gate.sh` | 코드가 바뀌었는데 check.sh 실패 → 끝내지 못하고 고치러 돌아감 | 실패 요약만 |
| 커밋 | git `pre-commit` | ktlint·단위 테스트 실패, main 커밋 | 없음 |
| push | git `pre-push` | 빌드·Android Lint 실패, main push | 없음 |
| PR | GitHub Actions | 위 검사 재실행(다른 PC에서도 되는지) | 없음 |
| 세션 시작 | SessionStart | 팀원 PC에서도 git hook 자동 활성화 | 1줄 |

장치를 바꿨으면 일부러 깨뜨려서 실제로 막히는지 확인한다(예: 테스트 하나를 일부러 틀리게 고치고 Claude에게 "끝내"라고 해 본다).

## 토큰 아끼는 규칙
- 대화형은 `/model opusplan`: 계획은 Opus, 구현은 Sonnet으로 자동 전환
- 무인 실행은 기능마다 새 세션 + Sonnet. 어려운 기능만 `MODEL=opus ./scripts/autopilot.sh F-06`
- 문서 조회는 doc-scout(Haiku), 리뷰는 reviewer(Sonnet, 읽기 전용)
- Gradle·logcat 원본은 파일로, Claude에게는 요약만
- 한 세션 한 기능, 끝나면 `/clear`. 주 1회 `/usage`

## 처음 한 번
1. docs/setup-windows.md(Windows) 또는 docs/setup-mac.md(Mac) 대로 PC 세팅
2. `git clone` 후 저장소 폴더에서 `claude` 실행 → 폴더 신뢰(trust) 승인 → hook 활성화
3. 개인 취향(말투 등)은 `CLAUDE.local.md`에 (커밋되지 않음)
