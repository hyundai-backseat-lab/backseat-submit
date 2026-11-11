#!/usr/bin/env bash
# 빠른 검사: 포맷 + 단위 테스트. Gradle 원본 로그는 build/check.log에 두고 요약만 출력한다.
#   ./scripts/check.sh        Claude 작업 중 (ktlintFormat으로 자동 정리 후 검사)
#   ./scripts/check.sh --ci   git hook / CI (자동 정리 없이 검사만)
set -u
cd "$(dirname "$0")/.." || exit 1

if [ ! -f ./gradlew ]; then
  echo "SKIP: gradlew 없음 (Android 프로젝트 생성 전)"
  exit 0
fi

mkdir -p build
LOG=build/check.log
# 구조·문서 검사(1초 미만, 토큰 0)를 먼저: 실패하면 Gradle까지 가지 않는다
if ! docs_out=$(bash ./scripts/check-docs.sh 2>&1); then echo "$docs_out"; exit 1; fi
# ktlint 플러그인이 아직 없으면(F-00 이전) 단위 테스트만 돌린다
if grep -qs "ktlint" build.gradle.kts app/build.gradle.kts gradle/libs.versions.toml; then
  if [ "${1:-}" = "--ci" ]; then TASKS="ktlintCheck testDebugUnitTest"
  else TASKS="ktlintFormat ktlintCheck testDebugUnitTest"; fi
  # detekt(안 쓰는 코드·복잡도)도 설정되어 있으면 함께
  grep -qs "detekt" build.gradle.kts app/build.gradle.kts gradle/libs.versions.toml && TASKS="$TASKS detekt"
else
  TASKS="testDebugUnitTest"
fi

start=$(date +%s)
./gradlew --console=plain --quiet $TASKS >"$LOG" 2>&1
code=$?
elapsed=$(( $(date +%s) - start ))

if [ $code -eq 0 ]; then
  echo "PASS: check (${elapsed}s)"
  exit 0
fi

echo "FAIL: check (${elapsed}s) — 핵심 오류만 표시, 전체 로그: $LOG"
# 컴파일 오류, 실패한 테스트, ktlint 위반, Gradle 실패 사유만 추린다
grep -E "^e: |error:|FAILED|Exception|expected|Expected|but was|Lint error|\.kt:[0-9]+:[0-9]+|What went wrong|Task .* not found|UnusedPrivate|UnusedImport|LongMethod|ComplexMethod|CyclomaticComplex" "$LOG" \
  | grep -v "^\s*at " | awk '!seen[$0]++' | head -n 40
exit 1
