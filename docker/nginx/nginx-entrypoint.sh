#!/bin/sh
# ECS 태스크 정의의 command를 이 스크립트로 오버라이드해, nginx가 실제로 리스닝을 시작하기
# 전에 Cloud Map에서 살아있는 app 인스턴스로 upstream을 한 번 채운다.
# nginx.prod.conf의 placeholder(127.0.0.1:8080 down)는 EC2 부팅 후 15초 뒤에야 도는
# nginx-watcher.sh 타이머와 별개로, ECS가 nginx 컨테이너를 그보다 먼저 배치하면 그대로 노출돼
# 첫 요청들이 "no live upstreams" 502를 받는다 — 이 스크립트가 그 경합을 없앤다.
set -eu

REGION="ap-northeast-2"
NGINX_CONF="/etc/nginx/nginx.conf"
MAX_ATTEMPTS=10
SLEEP_SECONDS=3

attempt=1
UPSTREAM_MEMBERS=""
while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
  # set -e 아래서는 대입식의 명령 치환이 실패해도 스크립트가 그대로 죽는다 —
  # discover-instances 호출 자체가 실패하는 경우도 "인스턴스가 아직 없는" 경우와 동일하게
  # 재시도 대상으로 다뤄야 한다.
  if ! INSTANCES=$(aws servicediscovery discover-instances \
    --namespace-name otboo.local \
    --service-name app \
    --region "$REGION" \
    --query 'Instances[].Attributes' --output json); then
    echo "$(date -Iseconds) discover-instances call failed, retry ${attempt}/${MAX_ATTEMPTS}" >&2
    attempt=$((attempt + 1))
    sleep "$SLEEP_SECONDS"
    continue
  fi

  UPSTREAM_MEMBERS=$(echo "$INSTANCES" | jq -r '.[] | "\(.AWS_INSTANCE_IPV4):\(.AWS_INSTANCE_PORT)"' | sort | \
    sed 's/^/        server /; s/$/ max_fails=3 fail_timeout=30s;/')

  if [ -n "$UPSTREAM_MEMBERS" ]; then
    break
  fi

  echo "$(date -Iseconds) no live app instances yet from Cloud Map, retry ${attempt}/${MAX_ATTEMPTS}" >&2
  attempt=$((attempt + 1))
  sleep "$SLEEP_SECONDS"
done

if [ -z "$UPSTREAM_MEMBERS" ]; then
  # 여기서 nginx 기동을 막지는 않는다 — 어차피 placeholder로도 15초 뒤 watcher 주기가 채워준다.
  echo "$(date -Iseconds) giving up waiting for app instances, starting nginx with placeholder upstream" >&2
  exec nginx -g "daemon off;"
fi

NEW_BLOCK=$(printf '    # BEGIN_UPSTREAM\n    upstream otboo_app {\n%s\n        keepalive 32;\n    }\n    # END_UPSTREAM' "$UPSTREAM_MEMBERS")

TMP_CONF=$(mktemp)
awk -v block="$NEW_BLOCK" '
  /# BEGIN_UPSTREAM/ { print block; skip=1; next }
  /# END_UPSTREAM/   { skip=0; next }
  !skip { print }
' "$NGINX_CONF" > "$TMP_CONF"

# NGINX_CONF는 Docker 바인드 마운트로 연결된 단일 파일이라, 그 경로 자체에 mv(rename)를 걸면
# 마운트 지점 inode를 rename으로 교체하게 돼 "Resource busy"로 실패한다(호스트에서 직접
# 파일을 다루는 nginx-watcher.sh의 mv와 다른 점 — 거기는 바인드 마운트 경계가 없다).
# 내용만 그대로 덮어써서 마운트된 inode 자체는 유지한다.
cat "$TMP_CONF" > "$NGINX_CONF"
rm -f "$TMP_CONF"
echo "$(date -Iseconds) initial upstream populated before nginx start: $(echo "$INSTANCES" | jq -c '[.[] | "\(.AWS_INSTANCE_IPV4):\(.AWS_INSTANCE_PORT)"]')"

exec nginx -g "daemon off;"