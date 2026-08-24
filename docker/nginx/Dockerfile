FROM nginx:1.27-alpine

# nginx-entrypoint.sh가 기동 전에 Cloud Map을 조회해 upstream을 채우기 위해 필요
RUN apk add --no-cache aws-cli jq

COPY nginx-entrypoint.sh /usr/local/bin/nginx-entrypoint.sh
RUN chmod +x /usr/local/bin/nginx-entrypoint.sh

ENTRYPOINT ["/usr/local/bin/nginx-entrypoint.sh"]