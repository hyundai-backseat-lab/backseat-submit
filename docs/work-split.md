# 개발 분담 (2026-11-10, 개발 마감 11/12)

개발자 2명이 각자 Claude Code로 동시에 개발한다. 원칙은 하나: **파일 소유권을 겹치지 않게 나누고, 둘 사이는 `game/api/GameContract.kt` 계약으로만 연결한다.** 상대 영역 파일은 읽기만 하고 고치지 않는다.

## 0단계 (먼저 머지, 둘 다 이 위에서 시작)
- `game/api/GameContract.kt`: features.md "게임 연결 계약" + 확장 2가지
  - 확장 ① `GameSession.status(): GameStatus` — 난이도·미션 순번·미션 제목·찾은 수·진행 수·힌트 수. 주행 화면(F-06 SC-04)과 결과 화면(F-05 SC-07)이 게임 안을 모르고 값을 받기 위해.
  - 확장 ② `CardIds` — 미션 카드 `mission-<미션ID>`(게임이 붙임), 차량 반응 카드 `vehicle-wiper|arrived|bye`(세션이 씀).
  - `PlaceSource.ahead(category)`: 위치·진행 방향은 구현이 넣는다(게임은 좌표를 모름).
- `game/GameFactory.kt`: `createGame()`과 임시 게임(PlaceholderGame). B가 실제 게임으로 바꾸기 전까지 A가 앱 흐름을 돌려 볼 수 있다.
- 계약을 바꿔야 하면: 둘이 합의 → 계약만 바꾸는 작은 PR을 먼저 머지 → 각자 rebase. 자기 브랜치에서 계약을 고치지 않는다.

## 개발자 A — 앱 기반(차량 신호·음성·이용 흐름·화면)
소유: `game/` 와 `game/api/` 를 뺀 전부. 즉 `pleos/ session/ ui/ di/ debug/ place/`, `App.kt`, `MainActivity.kt`, manifest, `build.gradle.kts`, `scripts/`, `.claude/`. 공용 문서는 아래 "공용 문서" 절을 따른다.
특별 규칙: 기존 `game/GameEngine.kt`와 그 테스트 3개(`GameEngineTest`, `GameEngineCueTest`, `EngineTestSupport`)는 F-22에서 A가 지운다(차례 진행이 session으로 옮겨가기 때문. car/ 이름 변경으로 어차피 컴파일이 깨지는 파일들).

| 순서 | 기능 | A가 하는 범위 |
|---|---|---|
| 1 | F-22 | 구조 전환 전체. 단 `game/`의 Round·WordChainRound·AssociationJson은 건드리지 않음 |
| 2 | F-23 | PleosClient 음성·주차·포커스 |
| 3 | F-21 | SessionController. 테스트는 `session` 테스트 폴더의 자체 FakeGame으로 |
| 4 | F-03, F-04 | 전체 |
| 5 | F-05 | Navi 도착 → `GameInput.Arrived` 전달, 멈춤 중 무음 종료, 결과 화면(값은 `status()`) |
| 6 | F-09 | 전체(VoiceCardStore, 카드 ID는 `CardIds`) |
| 7 | F-06 | 전체(값은 `status()`) |
| 8 | F-16 | `place/` HTTP·Fake 구현, docs/api-places.md |
| 9 | F-11 앱 쪽 | 위치·진행 방향 제공, 동의 버튼, di에서 PlaceSource 주입, route.sh |
| 10 | F-08 | 전체 |

## 개발자 B — 놀이 콘텐츠(관찰 미션·대화 규칙·말잇기·미션/대사 데이터)
소유: `app/src/main/java/.../game/**`(GameEngine.kt 제외, `game/api/` 제외), `app/src/test/java/.../game/**`(엔진 테스트 3개 제외), `app/src/main/assets/`의 게임 데이터(word_chain.json, 미션·대사 JSON).
B는 build·scripts·다른 패키지를 고치지 않는다.

## 공용 문서 (`docs/`, `CLAUDE.md`)
- 둘 다 고칠 수 있다. 자기 기능의 상태 표시(features.md), 결정(decisions.md), 진행 기록(progress.md)은 각자 자기 PR에서 고친다.
- 상대 기능의 내용이나 공통 정책·계약 설명처럼 상대에게 영향이 가는 부분은 고치기 전에 상대에게 먼저 알린다.
- 같은 문서를 동시에 고쳐 충돌이 나면 나중에 머지하는 사람이 main을 받아 충돌을 풀고 머지한다.

| 순서 | 기능 | B가 하는 범위 |
|---|---|---|
| 1 | F-07 | 관찰 미션 GameSession("observe") 전체, createGame에 등록 |
| 2 | F-18 | 대화 반응 규칙 전체(공통 제어 4종은 세션 몫이라 제외) |
| 3 | F-05 게임 쪽 | `Arrived` → 마무리 문장 + Finished, `status()` 값 채우기 |
| 4 | F-24 | 말잇기를 GameSession으로 옮기고 복합 게임 "giltam"을 기본값으로. 다 옮기면 Round.kt 등 옛 파일 삭제 |
| 5 | F-11 게임 쪽 | `PlaceSource` 결과가 있으면 장소 미션, 빈 목록이면 기본 미션(완료 기준 2·6). 지나침 공개(기준 3)는 시간 남으면 |

## 서로 맞춰 둘 약속 (코드 의존은 없음, 값의 약속만)
- 화면 버튼(난이도 등)은 A가 `Answer(turnId, listOf("중급"))`처럼 버튼 글자로 보낸다. B는 음성 "중급"과 똑같이 처리한다.
- "재안내·휴식·재개·종료" 4종은 A의 세션이 먼저 걸러낸다. B의 게임에는 오지 않는다.
- 무응답 14초 계산은 A, 무응답·못 알아들음 단계(1회 기다림 → 안내 → 다음 미션, 3미션 연속 → SuggestRest)는 B.
- 게임 안 오류는 게임이 기본 미션으로 대체한다. `step`이 예외를 던지면 A는 재시도 없이 종료한다.

## 합치는 순서
1. 0단계 PR 머지
2. A·B 각자 기능 브랜치 → PR → 머지 (순서 상관없음, 소유 파일이 안 겹침)
3. 마지막 연결: A가 di에서 `createGame("giltam", placeSource)`를 부르고 있으면 B의 게임이 머지되는 순간 자동으로 실제 게임으로 바뀐다. B의 F-24 "옛 파일 삭제" PR은 A의 F-22 머지 뒤에 머지한다(F-22가 옛 GameEngine 참조를 없애므로).
4. 둘 다 머지 후 A가 `./scripts/check.sh`, `./scripts/check-docs.sh`, 에뮬레이터 시연 시나리오로 최종 확인.
