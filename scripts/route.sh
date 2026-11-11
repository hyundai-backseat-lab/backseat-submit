#!/usr/bin/env bash
# 시연 경로 재현(F-11): GPX의 좌표를 에뮬레이터 GPS에 순서대로 넣는다. 진행 방향은 앱이 앞뒤 좌표로 계산한다.
#   ./scripts/route.sh scripts/routes/sejong-daero.gpx [간격 초, 기본 3]
# adb emu geo fix는 경도가 먼저다(geo fix <경도> <위도>).
set -eu
gpx="${1:?GPX 파일이 필요합니다. 예: ./scripts/route.sh scripts/routes/sejong-daero.gpx}"
interval="${2:-3}"
points=$(grep -o '<trkpt[^>]*>' "$gpx" | sed -E 's/.*lat="([^"]+)".*lon="([^"]+)".*/\1 \2/')
total=$(printf '%s\n' "$points" | wc -l | tr -d ' ')
i=0
printf '%s\n' "$points" | while read -r lat lon; do
  i=$((i + 1))
  adb emu geo fix "$lon" "$lat" > /dev/null
  echo "ROUTE $i/$total"
  if [ "$i" -lt "$total" ]; then sleep "$interval"; fi
done
