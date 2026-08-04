#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SECRET_DIR="$REPO_ROOT/.claude/otboo-secret"

if [ -f "$REPO_ROOT/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  source "$REPO_ROOT/.env"
  set +a
fi

if [ -z "${SECRET_REPO_URL:-}" ]; then
  echo "SECRET_REPO_URL이 없습니다 (.env 확인)." >&2
  exit 1
fi

CONFIG_FILES=(application.yaml application-local.yaml application-test.yaml)

if [ -d "$SECRET_DIR/.git" ]; then
  # git의 pull.rebase/rebase.autostash 전역 설정에 기대지 않고, stash/pull/pop을 직접 제어한다.
  # (설정 조합에 따라 git pull이 조용히 exit 0으로 끝나면서 충돌 마커만 남기는 경우가 있어 안전하지 않음)
  stash_before=$(git -C "$SECRET_DIR" stash list | wc -l | tr -d ' ')

  if [ -n "$(git -C "$SECRET_DIR" status --porcelain -- "${CONFIG_FILES[@]}")" ]; then
    git -C "$SECRET_DIR" stash push --include-untracked --quiet -- "${CONFIG_FILES[@]}"
  fi

  stash_after=$(git -C "$SECRET_DIR" stash list | wc -l | tr -d ' ')
  dirty=""
  if [ "$stash_after" -gt "$stash_before" ]; then
    dirty=1
  fi

  git -C "$SECRET_DIR" pull --quiet --no-rebase

  if [ -n "$dirty" ] && ! git -C "$SECRET_DIR" stash pop --quiet; then
    echo "FAIL: 설정 파일 동기화 중 충돌이 발생했습니다. $SECRET_DIR 에서 직접 해결하세요:" >&2
    echo "  1) 충돌 마커(<<<<<<< Updated upstream / ======= / >>>>>>> Stashed changes)를 열어 직접 수정" >&2
    echo "  2) git -C \"$SECRET_DIR\" add <파일>" >&2
    echo "  3) git -C \"$SECRET_DIR\" stash drop" >&2
    echo "해결 후 link_prop.sh를 다시 실행하세요." >&2
    exit 1
  fi
else
  mkdir -p "$(dirname "$SECRET_DIR")"
  git clone --quiet "$SECRET_REPO_URL" "$SECRET_DIR"
fi

link_file() {
  local local_path="$1"
  local secret_path="$2"

  if [ -L "$local_path" ] && [ "$(readlink "$local_path")" = "$secret_path" ]; then
    echo "OK: 이미 연결됨 - $local_path"
    return
  fi

  if [ ! -f "$secret_path" ]; then
    echo "FAIL: $secret_path 가 없습니다. otboo-secret에 해당 파일이 있는지 확인하세요." >&2
    exit 1
  fi

  if [ -e "$local_path" ] && [ ! -L "$local_path" ]; then
    if diff -q "$local_path" "$secret_path" > /dev/null 2>&1; then
      rm -f "$local_path"
    else
      echo "FAIL: $local_path 에 otboo-secret과 다른 로컬 변경 사항이 있습니다." >&2
      echo "      기존 push_prop.sh로 먼저 업로드하거나 직접 병합한 뒤 다시 실행하세요." >&2
      exit 1
    fi
  fi

  mkdir -p "$(dirname "$local_path")"
  ln -sf "$secret_path" "$local_path"
  echo "OK: 연결함 - $local_path -> $secret_path"
}

link_file "$REPO_ROOT/src/main/resources/application.yaml"       "$SECRET_DIR/application.yaml"
link_file "$REPO_ROOT/src/main/resources/application-local.yaml" "$SECRET_DIR/application-local.yaml"
link_file "$REPO_ROOT/src/test/resources/application-test.yaml"  "$SECRET_DIR/application-test.yaml"

echo "심볼릭 링크 셋업 완료."