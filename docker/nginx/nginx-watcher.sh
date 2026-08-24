#!/bin/bash
# Cloud Map(app.otboo.local)에서 살아있는 app 인스턴스 목록을 조회해
# nginx.conf의 upstream 블록(BEGIN_UPSTREAM~END_UPSTREAM)만 재생성하고 그레이스풀 리로드한다.
# 15초 주기 systemd 타이머로 실행(otboo-nginx-watcher.timer 참고).
set -euo pipefail

REGION="ap-northeast-2"
NGINX_CONF="/opt/otboo/nginx.conf"

# nginx는 ECS 태스크로 뜨므로 컨테이너 이름이 매번 자동 생성된다 — 고정 이름 대신
# 이 호스트에서 nginx 이미지로 뜬 컨테이너를 이미지 기준으로 찾는다(이 인스턴스엔 nginx 태스크가
# desiredCount:1로 하나만 배치되므로 이 필터로 충분히 유일하게 식별된다).
CONTAINER_ID=$(docker ps --filter "ancestor=464079169729.dkr.ecr.ap-northeast-2.amazonaws.com/otboo-nginx:latest" --format '{{.ID}}' | head -n1)
if [ -z "$CONTAINER_ID" ]; then
  echo "$(date -Iseconds) nginx container not found on this host yet, skip this cycle" >&2
  exit 0
fi

INSTANCES=$(aws servicediscovery discover-instances \
  --namespace-name otboo.local \
  --service-name app \
  --region "$REGION" \
  --query 'Instances[].Attributes' --output json)

# Cloud Map MULTIVALUE 라우팅은 매 조회마다 순서가 랜덤이라, 정렬 없이 그대로 비교하면
# 멤버가 그대로여도 순서만 바뀐 걸 "변경"으로 오판해 불필요한 reload가 계속 발생한다
# (긴 SSE/WebSocket 연결이 물려있는 워커가 reload마다 거의 매번 shutting-down 상태로 남는 원인).
UPSTREAM_MEMBERS=$(echo "$INSTANCES" | jq -r '.[] | "\(.AWS_INSTANCE_IPV4):\(.AWS_INSTANCE_PORT)"' | sort | \
  sed 's/^/        server /; s/$/ max_fails=3 fail_timeout=30s;/')

if [ -z "$UPSTREAM_MEMBERS" ]; then
  echo "$(date -Iseconds) no live app instances from Cloud Map, skip this cycle" >&2
  exit 0
fi

NEW_BLOCK=$(printf '    # BEGIN_UPSTREAM\n    upstream otboo_app {\n%s\n        keepalive 32;\n    }\n    # END_UPSTREAM' "$UPSTREAM_MEMBERS")

TMP_CONF=$(mktemp)
awk -v block="$NEW_BLOCK" '
  /# BEGIN_UPSTREAM/ { print block; skip=1; next }
  /# END_UPSTREAM/   { skip=0; next }
  !skip { print }
' "$NGINX_CONF" > "$TMP_CONF"

if diff -q "$TMP_CONF" "$NGINX_CONF" > /dev/null 2>&1; then
  rm -f "$TMP_CONF"
  exit 0   # 변경 없음
fi

if ! docker run --rm -v "$TMP_CONF:/etc/nginx/nginx.conf:ro" nginx:1.27-alpine nginx -t 2>&1; then
  echo "$(date -Iseconds) generated nginx.conf failed nginx -t, aborting reload" >&2
  rm -f "$TMP_CONF"
  exit 1
fi

# NGINX_CONF는 컨테이너가 단일 파일로 바인드 마운트한 대상이라, mv(rename)로 교체하면 호스트
# 경로는 새 inode를 가리키게 되지만 이미 떠 있는 컨테이너의 마운트는 원래 inode에 그대로 고정된
# 채로 남는다 — 그 결과 reload를 아무리 걸어도 컨테이너 안에서는 옛 내용을 계속 읽는다(실측
# 확인됨: 같은 EC2에서 mv로 교체 후 컨테이너 재조회 시 inode가 서로 달라지고 내용도 그대로였음).
# mv 대신 내용만 그대로 덮어써서 inode를 유지해야 살아있는 컨테이너에도 실제로 반영된다.
BACKUP_CONF="${NGINX_CONF}.bak"
cp "$NGINX_CONF" "$BACKUP_CONF"
cat "$TMP_CONF" > "$NGINX_CONF"
rm -f "$TMP_CONF"

if ! docker exec "$CONTAINER_ID" nginx -s reload; then
  # reload가 실패해도 위 in-place 쓰기는 이미 끝난 뒤라, 여기서 원상복구하지 않으면 다음 15초
  # 주기의 diff가 "변경 없음"으로 판단해 재시도조차 걸리지 않는다 — 이전 설정으로 되돌려 다음
  # 주기에 다시 "변경 있음"으로 잡히도록 한다. 복원도 반드시 in-place로 해야 한다(mv로 복원하면
  # 같은 이유로 컨테이너 마운트가 다시 끊어진다).
  echo "$(date -Iseconds) nginx -s reload failed, restoring previous config so the next cycle retries" >&2
  cat "$BACKUP_CONF" > "$NGINX_CONF"
  rm -f "$BACKUP_CONF"
  exit 1
fi

rm -f "$BACKUP_CONF"
echo "$(date -Iseconds) upstream reloaded: $(echo "$INSTANCES" | jq -c '[.[] | "\(.AWS_INSTANCE_IPV4):\(.AWS_INSTANCE_PORT)"]')"