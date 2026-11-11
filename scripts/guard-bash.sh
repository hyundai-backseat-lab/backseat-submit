#!/usr/bin/env bash
# PreToolUse(Bash) hook: 되돌리기 어려운 명령을 확정적으로 막는다(exit 2).
# 명령 파싱이 완벽하지 않으므로, 의심되는 문자열이 "어디에든" 들어 있으면 막는 쪽으로 설계한다.
set -u
input=$(cat)
cmd="$input"   # JSON 전체를 대상으로 검사(필드 순서·이스케이프에 흔들리지 않게)

block() { echo "차단됨: $1" >&2; exit 2; }
has() { printf '%s' "$cmd" | grep -Eq "$1"; }

# 1) 검사 우회
has '\-\-no-verify'                            && block "git hook 우회(--no-verify)는 금지입니다. 검사 실패 원인을 고치세요."
# hook 경로를 "바꾸는" 명령만 막는다(조회 `git config --get core.hooksPath`는 허용). 경로는 SessionStart hook이 자동 설정한다
has '(-c[[:space:]]+core\.hooksPath|core\.hooksPath[[:space:]]*=|config[^|;&"]*core\.hooksPath[[:space:]]+[^-[:space:]"])' && block "git hook 경로 변경은 금지입니다(세션 시작 시 자동 설정됨)."

has 'ALLOW_MAIN'                               && block "ALLOW_MAIN은 사람이 최초 1회만 쓰는 값입니다."
has 'git[^|;&"]*[[:space:]]commit[^|;&"]*[[:space:]]-[a-zA-Z]*n([[:space:]]|$|")' && block "git commit -n(검사 우회)은 금지입니다."

# 2) main 보호 / 강제 push (git -C 경로 push, HEAD:main, +main 포함)
if has 'git[^|;&"]*[[:space:]]push'; then
  has '[[:space:]:+](main|master)([[:space:]]|$|"|:)' && block "main으로 push할 수 없습니다. 기능 브랜치를 push하고 PR을 여세요."
  has '(--force|[[:space:]]-f([[:space:]]|$|")|--force-with-lease)' && block "강제 push는 금지입니다."
fi
has 'gh[[:space:]]+pr[[:space:]]+merge'        && block "PR 머지는 사람이 합니다."

# 3) main에서 커밋
if has 'git[^|;&"]*[[:space:]]commit'; then
  branch=$(git -C "${CLAUDE_PROJECT_DIR:-.}" symbolic-ref --short HEAD 2>/dev/null || echo "")
  if [ "$branch" = "main" ] || [ "$branch" = "master" ]; then
    block "main 브랜치에서 커밋할 수 없습니다. git switch -c feat/<기능ID>-<이름> 으로 브랜치를 만드세요."
  fi
fi

# 4) 비밀값: 명령 종류와 관계없이 이름이 등장하면 차단
has '(local\.properties|keystore\.properties|\.jks|\.keystore|BuildConfig\.java)' \
  && block "local.properties, 서명 키, BuildConfig는 셸로 다룰 수 없습니다. 값이 필요하면 사람에게 요청하세요."

exit 0
