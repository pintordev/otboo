# 환경변수 체크리스트

> 그라운드룰 보안 섹션(`환경변수는 Notion 비공개 페이지 또는 Secret 저장소로 공유`)의 실제 목록. 값 자체는 이 저장소에 커밋하지 않고 Notion/Secret 저장소에만 둡니다. `application-{profile}.yml`은 이 이름들을 참조만 하고, 실제 값은 `.env`(로컬)/GitHub Actions Secrets·AWS Secrets Manager(배포)에서 주입합니다.

| 변수명 | 용도 | 담당 | 비고 |
| --- | --- | --- | --- |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL(RDS) 접속 | 김호현 | local은 Docker Compose 기본값 사용 가능 |
| `JWT_SECRET` | JWT 서명 키 | 신홍규 | 최소 256bit, 환경별로 다른 값 |
| `JWT_ACCESS_EXPIRATION` / `JWT_REFRESH_EXPIRATION` | 토큰 만료 시간 | 신홍규 | |
| `OAUTH_GOOGLE_CLIENT_ID` / `OAUTH_GOOGLE_CLIENT_SECRET` | 소셜 로그인(Google) | 신홍규 | 3차 스프린트 전 발급 |
| `OAUTH_KAKAO_CLIENT_ID` / `OAUTH_KAKAO_CLIENT_SECRET` | 소셜 로그인(Kakao) | 신홍규 | 3차 스프린트 전 발급 |
| `KMA_API_KEY` | 기상청 단기예보 Open API 인증키 | 김호현 | 공공데이터포털 발급 |
| `KAKAO_LOCAL_API_KEY` | Kakao 로컬 API(좌표→행정구역) | 김호현 | `GET /api/weathers/location`용, 소셜 로그인 키와 별개 |
| `LLM_API_KEY` | OpenAI / Hugging Face / OpenRouter 중 확정본 | 김하빈 | 4차 스프린트 착수 전 결정·발급 |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | S3(이미지), ECS 배포 등 | 김호현 | 팀원 개별 IAM 계정 발급 후 각자 사용, 배포용은 GitHub Actions Secrets |
| `REDIS_HOST` / `REDIS_PORT` | 캐시·SSE 세션 | 김호현 | 초기엔 외부(비-AWS) 서버, 3차 이후 ElastiCache |
| `KAFKA_BOOTSTRAP_SERVERS` | SSE 알림 발행 파이프라인 `심화` | 김호현 | 5차 스프린트, 대안 검토 중(Confluent Cloud 등) |
| `ES_HOST` / `ES_PORT` | Elasticsearch 검색 `심화` | 이경신 | 3차 스프린트 |
| `APM_AGENT_KEY` (Pinpoint/Datadog) | 모니터링 | 김호현 | 2차 스프린트 말~3차 초 |
| `CORS_ALLOWED_ORIGINS` | `otboo-fe` 배포 URL 허용 | 김호현 | 로컬(`http://localhost:5173` 등)·배포 도메인 둘 다 |

## 아직 값이 확정되지 않은 것 (사전기간~해당 스프린트 전 확정)

- `LLM_API_KEY`가 참조할 실제 제공자(OpenAI/HuggingFace/OpenRouter) — 4차 스프린트 착수 전 결정
- Kafka 대안 서비스 선택 후 접속 정보 — 5차 스프린트 착수 전 결정
- AWS RDS/ElastiCache 엔드포인트 — 인프라 구성 시점(1차/3차 스프린트)에 확정
