#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SECRET_DIR="$REPO_ROOT/.claude/otboo-secret"
CONFIG_FILES=(application.yaml application-local.yaml application-test.yaml)

if [ ! -d "$SECRET_DIR/.git" ]; then
  echo "SECRET_DIR이 없습니다: $SECRET_DIR" >&2
  echo "먼저 scripts/link_prop.sh를 실행하세요." >&2
  exit 1
fi

# git의 pull.rebase/rebase.autostash 전역 설정에 기대지 않고, stash/pull/pop을 직접 제어한다.
# (설정 조합에 따라 git pull이 조용히 exit 0으로 끝나면서 충돌 마커만 남기는 경우가 있어 안전하지 않음 — docs/draft/17 참고)
STASH_BEFORE=$(git -C "$SECRET_DIR" stash list | wc -l | tr -d ' ')

if [ -n "$(git -C "$SECRET_DIR" status --porcelain -- "${CONFIG_FILES[@]}")" ]; then
  git -C "$SECRET_DIR" stash push --include-untracked --quiet -- "${CONFIG_FILES[@]}"
fi

STASH_AFTER=$(git -C "$SECRET_DIR" stash list | wc -l | tr -d ' ')
DIRTY=""
if [ "$STASH_AFTER" -gt "$STASH_BEFORE" ]; then
  DIRTY=1
fi

git -C "$SECRET_DIR" pull --quiet --no-rebase

if [ -n "$DIRTY" ] && ! git -C "$SECRET_DIR" stash pop --quiet; then
  echo "FAIL: 설정 파일 동기화 중 충돌이 발생했습니다. $SECRET_DIR 에서 직접 해결하세요:" >&2
  echo "  1) 충돌 마커(<<<<<<< Updated upstream / ======= / >>>>>>> Stashed changes)를 열어 직접 수정" >&2
  echo "  2) git -C \"$SECRET_DIR\" add <파일>" >&2
  echo "  3) git -C \"$SECRET_DIR\" stash drop" >&2
  echo "해결 후 다시 실행하세요." >&2
  exit 1
fi

echo "SECRET_DIR을 최신 상태로 동기화했습니다."