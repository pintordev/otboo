#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SECRET_DIR="$REPO_ROOT/.claude/otboo-secret"
RESOURCES_DIR="$REPO_ROOT/src/main/resources"

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
  git -C "$SECRET_DIR" pull --quiet
else
  mkdir -p "$(dirname "$SECRET_DIR")"
  git clone --quiet "$SECRET_REPO_URL" "$SECRET_DIR"
fi

BACKUP_DIR="$SECRET_DIR/bak"
mkdir -p "$BACKUP_DIR"

shopt -s nullglob
files=("$SECRET_DIR"/application*.yaml)
shopt -u nullglob

if [ ${#files[@]} -eq 0 ]; then
  echo "otboo-secret에 application*.yaml 파일이 없습니다." >&2
  exit 1
fi

for src in "${files[@]}"; do
  name="$(basename "$src")"
  dest="$RESOURCES_DIR/$name"

  if [ ! -f "$dest" ]; then
    cp "$src" "$dest"
    echo "새로 추가됨: $name"
    continue
  fi

  if diff -q "$dest" "$src" > /dev/null; then
    echo "변경 없음: $name"
    continue
  fi

  echo "=== $name 변경 사항 ==="
  diff -u "$dest" "$src" || true
  read -r -p "$name 을(를) 교체할까요? [y/N] " answer
  case "$answer" in
    [yY]*)
      cp "$dest" "$BACKUP_DIR/$name.bak"
      cp "$src" "$dest"
      echo "교체 완료 (백업: $BACKUP_DIR/$name.bak)"
      ;;
    *)
      echo "건너뜀: $name"
      ;;
  esac
done