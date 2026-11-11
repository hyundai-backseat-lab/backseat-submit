#!/usr/bin/env bash
# 오토파일럿: docs/features.md에서 [approved] 기능을 문서 순서대로 하나씩, 매번 새 세션으로 구현해 PR까지 올린다.
# 사람은 (1) 기능 완료 기준 승인 (2) PR 확인·머지만 한다.
#   ./scripts/autopilot.sh            approved 전부 순서대로
#   ./scripts/autopilot.sh F-03       지정한 기능만
#   MODEL=opus ./scripts/autopilot.sh F-06   어려운 기능은 모델을 올려서
# 안전장치
#   - 의존 기능(features.md "의존:" 줄)이 머지 전이면: 열린 PR 브랜치를 기준으로 쌓아(stacked PR) 진행, PR도 없으면 건너뜀
#   - 원격에 feat/<ID>-* 브랜치가 이미 있으면 다른 사람이 작업 중으로 보고 건너뜀(동시 실행 충돌 방지)
#   - 실패·중단 시 작업 내용을 stash로 보관하고 다음 기능으로 넘어감
set -u
cd "$(dirname "$0")/.." || exit 1
MODEL="${MODEL:-sonnet}"
EFFORT="${EFFORT:-medium}"
MAX_TURNS="${MAX_TURNS:-100}"
mkdir -p build/autopilot

sync_main() {
  git switch main >/dev/null 2>&1 || { echo "main으로 전환 실패 — 작업트리를 확인하세요"; git status --short | head; exit 1; }
  git pull --ff-only >/dev/null 2>&1 || echo "경고: main pull 실패(오프라인?)"
}
merged() { gh pr list --state merged --search "$1 in:title" --json number --jq 'length' 2>/dev/null | grep -qv '^0$'; }
open_branch() { gh pr list --state open --search "$1 in:title" --json headRefName --jq '.[0].headRefName // empty' 2>/dev/null; }
deps_of() { awk -v id="$1" '$0 ~ "^## "id" " {f=1; next} /^## F-/ {f=0} f && /^의존:/ {sub(/^의존:[[:space:]]*/, ""); print; exit}' docs/features.md | grep -oE 'F-[0-9]+'; }

sync_main
if [ $# -gt 0 ]; then ids="$*"
else ids=$(grep -oE '^## F-[0-9]+[^[]*\[approved\]' docs/features.md | grep -oE 'F-[0-9]+'); fi
[ -z "$ids" ] && { echo "승인된([approved]) 기능이 없습니다. docs/features.md를 확인하세요."; exit 0; }

summary=""
for id in $ids; do
  if git ls-remote --exit-code --heads origin "feat/$id-*" >/dev/null 2>&1; then
    echo "· $id 건너뜀: 원격에 작업 브랜치가 이미 있음(다른 사람 작업 중 또는 PR 대기)"; summary="$summary $id:skip"; continue
  fi
  base="main"; skip=""
  for d in $(deps_of "$id"); do
    merged "$d" && continue
    b=$(open_branch "$d")
    if [ -n "$b" ]; then base="$b"; else skip="$d"; fi
  done
  if [ -n "$skip" ]; then echo "· $id 건너뜀: 의존 기능 $skip 이 아직 PR도 없음"; summary="$summary $id:wait"; continue; fi

  log="build/autopilot/$id-$(date +%m%d-%H%M).log"
  echo "▶ $id 시작 (base=$base, model=$MODEL, effort=$EFFORT) — 로그: $log"
  claude -p "/feature $id
무인 실행이다. 사람에게 질문할 수 없다. 기준 브랜치는 $base 이다(브랜치 생성과 PR base 모두 이것을 쓴다).
막히면 docs/features.md 해당 항목에 '막힘:' 메모를 남기고 상태를 [blocked]로 바꾼 뒤, 브랜치를 push하고 draft PR로 올리고 멈춰라." \
    --model "$MODEL" --effort "$EFFORT" --permission-mode auto --max-turns "$MAX_TURNS" >"$log" 2>&1
  rc=$?
  echo "■ $id 종료(rc=$rc) — 마지막 보고:"; tail -n 6 "$log"
  if [ -n "$(git status --porcelain)" ]; then
    git stash push -u -m "autopilot-$id-$(date +%m%d-%H%M)" >/dev/null && echo "  남은 변경을 stash에 보관함(git stash list)"
  fi
  summary="$summary $id:rc$rc"
  sync_main
done
echo "결과:$summary"
bash ./scripts/notify.sh "오토파일럿 완료:$summary — PR을 확인해 주세요" </dev/null
