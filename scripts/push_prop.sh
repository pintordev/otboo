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

GITHUB_LOGIN="$(gh api user -q .login 2>/dev/null || true)"
case "$GITHUB_LOGIN" in
  pintordev) DISPLAY_NAME="김호현" ;;
  habin08090) DISPLAY_NAME="김하빈" ;;
  Zrp0x0) DISPLAY_NAME="신홍규" ;;
  Ksinny) DISPLAY_NAME="이경신" ;;
  "") DISPLAY_NAME="알 수 없음" ;;
  *) DISPLAY_NAME="$GITHUB_LOGIN" ;;
esac

if [ -d "$SECRET_DIR/.git" ]; then
  git -C "$SECRET_DIR" pull --quiet
else
  mkdir -p "$(dirname "$SECRET_DIR")"
  git clone --quiet "$SECRET_REPO_URL" "$SECRET_DIR"
fi

shopt -s nullglob
files=("$RESOURCES_DIR"/application*.yaml)
shopt -u nullglob

if [ ${#files[@]} -eq 0 ]; then
  echo "공유할 application*.yaml 파일이 없습니다: $RESOURCES_DIR" >&2
  exit 1
fi

cp "${files[@]}" "$SECRET_DIR/"

git -C "$SECRET_DIR" add application*.yaml

if git -C "$SECRET_DIR" diff --cached --quiet; then
  echo "변경된 설정 파일이 없습니다."
  exit 0
fi

echo "=== 업로드될 변경 사항 ==="
git -C "$SECRET_DIR" diff --cached
read -r -p "otboo-secret에 업로드할까요? [y/N] " answer
case "$answer" in
  [yY]*) ;;
  *)
    git -C "$SECRET_DIR" restore --staged application*.yaml
    echo "업로드를 취소했습니다."
    exit 0
    ;;
esac

COMMIT_MSG="chore: update application config ($(date '+%Y-%m-%d %H:%M:%S') by $DISPLAY_NAME)"
git -C "$SECRET_DIR" commit --quiet -m "$COMMIT_MSG"
git -C "$SECRET_DIR" push --quiet

echo "otboo-secret에 설정 파일을 업로드했습니다."

if [ -n "${DISCORD_WEBHOOK_URL:-}" ]; then
  curl -sS -X POST -H "Content-Type: application/json" \
    -d "{\"content\": \"📤 **$DISPLAY_NAME** 님이 설정 파일(application*.yaml)을 업로드했습니다.\"}" \
    "$DISCORD_WEBHOOK_URL" > /dev/null
else
  echo "DISCORD_WEBHOOK_URL이 없어 알림을 보내지 않았습니다 (.env 확인)." >&2
fi