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

if [ -d "$SECRET_DIR/.git" ]; then
  git -C "$SECRET_DIR" pull --quiet --rebase
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