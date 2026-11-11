#!/usr/bin/env bash
# Stop hook: 코드가 바뀌었는데 검사를 통과하지 못했으면 Claude가 턴을 끝내지 못하게 막는다(exit 2).
# - 코드 변경이 없는 턴은 검사를 건너뛴다(시간·토큰 절약).
# - 탈출 조건: 같은 실패가 3번 연속이고 features.md에 [blocked]를 기록했으면 멈추는 것을 허용한다.
#   (환경 문제로 영원히 실패하는 경우 max-turns를 다 쓰는 것을 막는다)
set -u
cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/..}" || exit 0
cat >/dev/null

[ -f ./gradlew ] || exit 0
gitdir=$(git rev-parse --git-dir 2>/dev/null) || exit 0
globs=('*.kt' '*.kts' '*.xml' '*.toml' '*.json' '*.properties' '*.pro')

[ -z "$(git status --porcelain -- "${globs[@]}" 2>/dev/null | grep -v 'local.properties')" ] && exit 0

fingerprint=$( { git diff HEAD -- "${globs[@]}" 2>/dev/null; \
                 git ls-files --others --exclude-standard -- "${globs[@]}" | grep -v 'local.properties' | while IFS= read -r f; do cat "$f" 2>/dev/null; done; } \
               | git hash-object --stdin )
ok="$gitdir/claude-gate-ok"; fails="$gitdir/claude-gate-fail"
[ -f "$ok" ] && [ "$(cat "$ok")" = "$fingerprint" ] && exit 0

# macOS에는 timeout이 없다(coreutils 설치 시 gtimeout). 둘 다 없으면 제한 없이 실행
if command -v timeout >/dev/null 2>&1; then lim="timeout 540"
elif command -v gtimeout >/dev/null 2>&1; then lim="gtimeout 540"
else lim=""; fi
out=$($lim ./scripts/check.sh --ci 2>&1); rc=$?
if [ $rc -eq 0 ]; then
  echo "$fingerprint" >"$ok"; rm -f "$fails"
  exit 0
fi
[ $rc -eq 124 ] && out="FAIL: check.sh가 9분 안에 끝나지 않았습니다(첫 Gradle 다운로드일 수 있음). 다시 실행하세요."

n=$(( $(cat "$fails" 2>/dev/null || echo 0) + 1 )); echo "$n" >"$fails"
if [ "$n" -ge 3 ] && git diff HEAD -- docs/features.md | grep -q '^+.*\[blocked\]'; then
  rm -f "$fails"
  echo "검사 실패 상태지만 [blocked]가 기록되어 멈춤을 허용합니다." >&2
  exit 0
fi

{
  echo "아직 끝낼 수 없습니다: 코드가 바뀌었는데 ./scripts/check.sh가 실패했습니다. (연속 실패 $n회)"
  echo "오류를 고친 뒤 check.sh를 다시 실행하세요. 테스트를 지우거나 건너뛰어 통과시키지 마세요."
  [ "$n" -ge 3 ] && echo "3회 이상 같은 문제라면 접근을 바꾸거나, 해결 불가 시 docs/features.md 해당 항목에 '막힘:' 메모와 [blocked]를 기록하세요."
  echo "$out"
} >&2
exit 2
