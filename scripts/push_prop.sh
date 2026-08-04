#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SECRET_DIR="$REPO_ROOT/.claude/otboo-secret"
CONFIG_FILES=(application.yaml application-local.yaml application-test.yaml)
S3_CONFIG_FILES=(application.yaml application-test.yaml)

if [ -f "$REPO_ROOT/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  source "$REPO_ROOT/.env"
  set +a
fi

if [ ! -d "$SECRET_DIR/.git" ]; then
  echo "SECRET_DIR이 없습니다: $SECRET_DIR" >&2
  echo "먼저 scripts/link_prop.sh를 실행하세요." >&2
  exit 1
fi

GITHUB_LOGIN="$(gh api user -q .login 2>/dev/null || true)"
case "$GITHUB_LOGIN" in
  pintordev) DISPLAY_NAME="김호현" ;;
  habin08090) DISPLAY_NAME="김하빈" ;;
  zrp0x0) DISPLAY_NAME="신홍규" ;;
  Ksinny) DISPLAY_NAME="이경신" ;;
  "") DISPLAY_NAME="알 수 없음" ;;
  *) DISPLAY_NAME="$GITHUB_LOGIN" ;;
esac

# git의 pull.rebase/rebase.autostash 전역 설정에 기대지 않고, stash/pull/pop을 직접 제어한다.
# (설정 조합에 따라 git pull이 조용히 exit 0으로 끝나면서 충돌 마커만 남기는 경우가 있어 안전하지 않음 — docs/draft/17 참고)
sync_secret_dir() {
  local stash_before stash_after dirty
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
    echo "해결 후 push_prop.sh를 다시 실행하세요." >&2
    exit 1
  fi
}

sync_secret_dir

# git diff는 untracked 파일을 못 보므로(sync_secret_dir가 stash pop으로 복원한 untracked 설정 파일이
# 있을 수 있음) status --porcelain으로 판단한다.
HAS_CHANGES=""
if [ -n "$(git -C "$SECRET_DIR" status --porcelain -- "${CONFIG_FILES[@]}")" ]; then
  HAS_CHANGES=1
fi

PUSHED=""
if [ -z "$HAS_CHANGES" ]; then
  echo "변경된 설정 파일이 없습니다."
else
  echo "=== 업로드될 변경 사항 ==="
  git -C "$SECRET_DIR" status --short -- "${CONFIG_FILES[@]}"
  git -C "$SECRET_DIR" diff -- "${CONFIG_FILES[@]}"
  read -r -p "otboo-secret에 업로드할까요? [y/N] " answer
  case "$answer" in
    [yY]*)
      git -C "$SECRET_DIR" add "${CONFIG_FILES[@]}"
      COMMIT_MSG="chore: update application config ($(date '+%Y-%m-%d %H:%M:%S') by $DISPLAY_NAME)"
      git -C "$SECRET_DIR" commit --quiet --only -m "$COMMIT_MSG" -- "${CONFIG_FILES[@]}"
      git -C "$SECRET_DIR" push --quiet
      echo "otboo-secret에 설정 파일을 업로드했습니다."
      PUSHED=1
      ;;
    *)
      echo "업로드를 취소했습니다."
      ;;
  esac
fi

# 워킹트리가 깨끗해도 이전 실행에서 push만 실패했으면 HEAD가 upstream보다 앞서 있을 수 있다 —
# 그 상태에서 S3만 올리면 git엔 없는 커밋 내용이 S3에만 반영되므로, upstream과 완전히 일치할 때만 허용한다.
UP_TO_DATE=""
if [ -z "$HAS_CHANGES" ] &&
   git -C "$SECRET_DIR" rev-parse --verify -q '@{upstream}' > /dev/null 2>&1 &&
   [ "$(git -C "$SECRET_DIR" rev-parse HEAD)" = "$(git -C "$SECRET_DIR" rev-parse '@{upstream}')" ]; then
  UP_TO_DATE=1
fi

# 위 조건(upstream과 완전히 동기화된 상태)이거나 이번에 실제로 push했을 때만 S3에 올린다.
# 변경이 있는데 취소했거나 push가 아직 안 된 로컬 커밋이 남아있으면 절대 올리지 않는다.
if [ -n "$UP_TO_DATE" ] || [ -n "$PUSHED" ]; then
  if [ -n "${AWS_S3_BUCKET:-}" ]; then
    for f in "${S3_CONFIG_FILES[@]}"; do
      if [ -n "${AWS_PROFILE:-}" ]; then
        aws s3 cp --quiet --profile "$AWS_PROFILE" "$SECRET_DIR/$f" "s3://$AWS_S3_BUCKET/config/test/$f"
      else
        aws s3 cp --quiet "$SECRET_DIR/$f" "s3://$AWS_S3_BUCKET/config/test/$f"
      fi
    done
    echo "S3에도 설정 파일을 업로드했습니다."
  else
    echo "AWS_S3_BUCKET이 없어 S3 업로드를 건너뛰었습니다 (.env 확인)." >&2
  fi
fi

if [ -n "$PUSHED" ]; then
  if [ -n "${DISCORD_WEBHOOK_URL:-}" ]; then
    PAYLOAD_FILE="$(mktemp)"
    printf '{"content": "📤 **%s** 님이 설정 파일(application*.yaml)을 업로드했습니다."}' "$DISPLAY_NAME" > "$PAYLOAD_FILE"
    curl -fsS -X POST -H "Content-Type: application/json" \
      --data-binary "@$PAYLOAD_FILE" \
      "$DISCORD_WEBHOOK_URL" > /dev/null
    rm -f "$PAYLOAD_FILE"
  else
    echo "DISCORD_WEBHOOK_URL이 없어 알림을 보내지 않았습니다 (.env 확인)." >&2
  fi
fi