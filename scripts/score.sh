#!/usr/bin/env bash
# 성적표 자동 계산(토큰 0): origin/main(없으면 로컬)의 docs/features.md에서 머지된 기능의 KPI 줄을 집계한다.
# 머지된 기능 = main에서 상태가 [pr] 또는 [done]인 항목. 기능 PR은 성적표를 직접 고치지 않는다(충돌 방지).
#   ./scripts/score.sh        한 줄 요약
#   ./scripts/score.sh -v     기능별 상세
set -u
cd "$(dirname "$0")/.." || exit 1
src=$(git show origin/main:docs/features.md 2>/dev/null || cat docs/features.md 2>/dev/null) || exit 0

printf '%s\n' "$src" | awk -v verbose="${1:-}" '
  /^## F-[0-9]+/ {
    id=$2; done_=($0 ~ /\[(pr|done)\]/)
  }
  /^KPI:/ && id!="" {
    line=$0
    if (done_) {
      ndone++
      if (line ~ /폰 테스트: 아니오/) k1++
      if (line ~ /폰 테스트:/ && line !~ /해당 없음/) k1t++
      while (match(line, /장면 [0-9]/)) { sc[substr(line, RSTART+RLENGTH-1, 1)]=1; line=substr(line, RSTART+RLENGTH) }
      line=$0
      n=split("Vehicle NaviHelper STT TTS FusedLocation", sdk, " ")
      for (i=1;i<=n;i++) if (index(line, sdk[i])) used[sdk[i]]=1
      if (verbose=="-v") print "  " id " " $0
    }
    id=""
  }
  END {
    s=0; for (k in sc) s++
    u=0; list=""; for (k in used) { u++; list=list k "," }
    sub(/,$/, "", list)
    printf "SCORE: 머지된 기능 %d | K1 차량특화 %d/%d | K2 결정적장면 %d/3 | K3 SDK %d/4(%s)\n", ndone, k1, k1t, s, u, (list==""?"-":list)
  }'
