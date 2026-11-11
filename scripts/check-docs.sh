#!/usr/bin/env bash
# 구조 검사(토큰 0, 1초 미만): 코드와 문서가 어긋나거나 금지된 의존이 생기면 실패한다.
# 1) 패키지 간 의존 그래프를 import문으로 계산 → docs/architecture.md의 그래프와 다르면 실패(문서 갱신 강제)
# 2) 금지된 의존(예: game → pleos, session → 구체 게임) 발견 시 실패
# 3) AndroidManifest의 진입점(Activity/Service/Receiver/Application)이 architecture.md "진입점" 표에 없으면 실패
# 4) 패키지가 glossary.md에 없으면 실패
#   ./scripts/check-docs.sh          검사
#   ./scripts/check-docs.sh --write  architecture.md의 의존 그래프를 현재 코드로 다시 쓴다
set -u
cd "$(dirname "$0")/.." || exit 1
BASE="app/src/main/java/io/backseatlab/playground"
[ -d "$BASE" ] || { echo "SKIP: 소스 없음"; exit 0; }
ARCH=docs/architecture.md; GLOS=docs/glossary.md
fail=0; err() { echo "FAIL(docs): $1"; fail=1; }

# --- 1) 의존 그래프 ---
graph=$(
  find "$BASE" -name '*.kt' | while read -r f; do
    rel=${f#"$BASE"/}; from=${rel%%/*}; [ "$from" = "$rel" ] && from="(root)"
    grep -oE '^import io\.backseatlab\.playground\.[a-z0-9_]+' "$f" | sed 's/.*playground\.//' | while read -r to; do
      [ "$to" != "$from" ] && echo "  $from --> $to"
    done
  done | sort -u
)
block=$(printf '```mermaid\ngraph LR\n%s\n```' "$graph")
if [ "${1:-}" = "--write" ]; then
  # 여러 줄 값은 awk -v로 넘기면 macOS(BSD awk)에서 실패하므로 환경변수로 넘긴다
  BLK="$block" awk '/<!-- depgraph:start -->/{print; print ENVIRON["BLK"]; skip=1; next} /<!-- depgraph:end -->/{skip=0} !skip' "$ARCH" >"$ARCH.tmp" && mv "$ARCH.tmp" "$ARCH"
  echo "architecture.md 의존 그래프 갱신"; exit 0
fi
current=$(awk '/<!-- depgraph:start -->/{f=1;next} /<!-- depgraph:end -->/{f=0} f' "$ARCH" 2>/dev/null)
[ "$current" = "$block" ] || err "docs/architecture.md 의존 그래프가 코드와 다릅니다 → ./scripts/check-docs.sh --write 후 그래프가 의도대로인지 확인하세요"

# --- 2) 금지된 의존 (architecture.md '의존 규칙'과 같은 내용) ---
while read -r rule; do
  from=${rule%% *}; to=${rule##* }
  printf '%s\n' "$graph" | grep -qE "^  $from --> $to\$" && err "금지된 의존 $from → $to (docs/architecture.md 의존 규칙)"
done <<'EOF'
game pleos
game session
game ui
game di
game place
session ui
session di
session place
ui pleos
ui game
ui di
ui place
pleos game
pleos session
pleos ui
pleos di
pleos place
place pleos
place session
place ui
EOF
# 위 그래프는 game과 game.api를 구분하지 못한다. session·ui·place는 게임 계약(game.api)만 import한다
if grep -rhE '^import io\.backseatlab\.playground\.game\.' "$BASE"/session "$BASE"/ui "$BASE"/place 2>/dev/null | grep -qvE '\.game\.api\.'; then
  err "session·ui·place는 game.api만 import할 수 있습니다(구체 게임은 di에서 연결)"
fi
if grep -rlE '^import (ai\.pleos\.|io\.backseatlab\.playground\.pleos\.sdk)' "$BASE" 2>/dev/null | grep -vE "/(pleos|di)/|/App\.kt$" | grep -q .; then
  err "Pleos SDK와 그 구현(pleos.sdk)은 pleos/·di/·App.kt에서만 import할 수 있습니다"
fi

# --- 3) 진입점 ---
entries=$(for mf in app/src/main/AndroidManifest.xml app/src/debug/AndroidManifest.xml; do
  [ -f "$mf" ] && tr '\n' ' ' <"$mf" | grep -oE '<(activity|service|receiver|provider|application)[^>]*android:name="[^"]+"' \
    | sed -E 's/.*android:name="([^"]+)".*/\1/' | sed 's/.*\.//'
done | sort -u)
for c in $entries; do
  grep -qE "^\| *$c *\|" "$ARCH" || err "진입점 $c 이(가) docs/architecture.md 진입점 표에 없습니다(종류·실행 시점·하는 일을 적으세요)"
done

# --- 4) 패키지 용어 ---
for d in "$BASE"/*/; do
  p=$(basename "$d"); grep -qE "^\| *\`?$p/?\`? *\|" "$GLOS" || err "패키지 '$p'가 docs/glossary.md에 없습니다"
done

[ $fail -eq 0 ] && echo "PASS: docs" || exit 1
