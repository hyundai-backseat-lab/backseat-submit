#!/usr/bin/env bash
# SessionStart hook(startup|compact|clear): 매 세션 맨 앞에 목표와 현재 성적을 띄워 에이전트가 목표를 잊지 않게 한다.
# 팀원이 clone만 해도 git hook이 켜지도록 hooksPath도 보장한다. 출력은 Claude 컨텍스트에 들어가므로 3줄.
set -u
cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/..}" || exit 0
cat >/dev/null
if git rev-parse --git-dir >/dev/null 2>&1; then
  [ "$(git config core.hooksPath)" = "scripts/git-hooks" ] || git config core.hooksPath scripts/git-hooks
fi
north=$(grep -A1 '^## 북극성' docs/strategy.md 2>/dev/null | tail -n 1)
score=$(bash scripts/score.sh 2>/dev/null)
branch=$(git symbolic-ref --short HEAD 2>/dev/null)
echo "목표: ${north:-docs/strategy.md 확인} (우선순위: 안전 > 시연 안정성 > 차량특화 > SDK 활용 > 기능 개수)"
echo "${score:-SCORE: 계산 불가}"
echo "branch=${branch:-none}"
exit 0
