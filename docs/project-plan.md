# 옷장을 부탁해 프로젝트 수행 계획서 (초안)

> ⚠️ 이 문서는 초안입니다. `(확정 필요)`, `(팀 논의 후 확정)` 표시가 있는 항목은 킥오프 전후로 팀원들과 논의해 채워 넣어 주세요.
>

---

## 👀 그라운드 룰

> 계획서를 열 때마다 읽기
>

### 기본 태도

- 팀 미팅, 멘토링 시간 약속 지키기
- 서로의 속도와 실력 차이 받아들이기 (4인 소규모 팀이라 한 명의 공백이 전체 일정에 미치는 영향이 큼을 인지하기)
- 불편사항은 두 번 참지 않고 공유하기
    - "어떤 상황에서, 뭐가 불편했고, 어떻게 하면 좋겠다" 형식으로 정리

### 소통 및 공유

- 적극적으로 공유하고 소통하기
    - **No Communication. No Responsibility.**
    - 메시지 확인했으면 확인 여부 메시지로 꼭 남기기
    - 공유되지 않은 정보는 팀에게 없는 정보
- 막히는 것은 빠르게 공유하기
    - 30분 이상 막히면 팀원 또는 멘토에게 공유
    - 내가 생각한 최선의 방법을 먼저 제시하고 리뷰받기
    - **다른 팀원 작업을 지연시키는 블로커는 30분 기다리지 않고 즉시 팀 채널에 공유**

### 코드 리뷰 및 PR

- PR 리뷰는 가능한 빨리 확인 (최대 3시간, 지연 시 작성자가 재차 요청)
    - 코멘트 달기 어려우면 Approve / Request Changes라도 남기기
    - 4인 팀 특성상 리뷰가 지연되면 후속 작업도 함께 지연될 가능성이 큼
- `dev` · `main` 브랜치 직접 push 금지
- Fork 기반 워크플로우 — 각자 `origin`(개인 fork)에 push 후 `upstream`(팀 레포)의 `dev`로 PR (상세 10단계는 [🧩 규칙 수립 — 워크플로우] 참고)
- 코드 리뷰 시 부드럽고 완곡한 표현 사용
    - "이건 왜 이렇게 짰어요?" ❌
    - "이 부분은 이렇게 바꾸면 어떨까요?" ✅
    - 코드에 대한 피드백이지 사람에 대한 평가가 아님

### 보안

- API 키, DB 비밀번호, JWT Secret, OAuth Client Secret 등 민감 정보는 절대 repo에 커밋하지 않기
- `.env`, `application-secret.yml` 등 실제 설정 파일은 `.gitignore`에 반드시 추가
- 환경변수 목록 및 설정 구조는 Notion(비공개 페이지) 또는 별도 Secret 저장소를 통해 공유. CI/CD·운영용 시크릿은 GitHub Actions Secrets / AWS Secrets Manager·Parameter Store로 분리 관리
- 소셜 로그인(Google/Kakao) 연동 시 개인정보(이메일 등)는 최소한으로 수집·저장
- DM(웹소켓) 메시지, 위치 정보(위·경도), 생년월일·성별 등 프로필 개인정보는 로그에 원문 노출 금지
- Native Query 사용 시 반드시 파라미터 바인딩 사용 (SQL Injection 방지)
- 외부 API(기상청, Kakao, LLM 등) 응답값은 DB 저장 전 필드 타입·범위 검증 필수
- 비밀번호는 `BCryptPasswordEncoder`로 해싱 저장 — 평문 저장·로그 노출 절대 금지 (멘토 피드백 #6)

---

## 💡 프로젝트 주제

**옷장을 부탁해 (Otboo)** — 개인화 의상 및 아이템 추천 SaaS

날씨·취향을 고려해 사용자가 보유한 의상 조합을 추천해주고, OOTD 피드·팔로우·DM 등의 소셜 기능을 갖춘 Spring Boot 백엔드 서비스.

핵심 기술 과제: Spring Security + JWT 인증/인가, 기상청 Open API 연동 및 Spring Batch 자동화, 자체 추천 알고리즘 + LLM 기반 고도화(+ 간단한 챗봇, 선택 심화), WebSocket/SSE 실시간 통신, Redis·Kafka 기반 분산 환경 설계.

---

## 🎓 멘토 피드백 반영 (필수는 아니지만 가점 요소)

| # | 피드백 | 반영 내용 | 담당 | 시점 |
| --- | --- | --- | --- | --- |
| 1 | 외부 호출은 RestTemplate 대신 Feign Client | 기상청/Kakao 좌표변환/구매링크 크롤링/LLM API 호출에 Feign Client 적용 | 김호현, 김하빈 | 1~3차 스프린트 |
| 2 | 테스트 코드에 EasyRandom, FixtureMonkey 반영 | 테스트 픽스처 생성에 EasyRandom 또는 FixtureMonkey 도입 (수동 빌더 지양) | 전체 | 사전기간 도구 선정, 1차부터 적용 |
| 3 | 날씨 실시간 대신 배치 주기 재고 | `otboo-fe` 확인 결과 날씨는 페이지 진입 시 1회 조회만 하고 폴링하지 않음 → 실시간 스트리밍 불필요, **매시 정각 배치**로 기상청 단기예보 수집(발표 주기에 맞춤) | 김호현 | 1차 스프린트 |
| 4 | SSE를 Kafka+Redis로 구성 시 유실 방지 | 알림을 Kafka에 발행 → Redis로 SSE 세션/미전송 알림 관리, `LastEventId` 기반 재연결 복구 + 주기적 재시도 배치로 유실 보완 | 김호현 | 5차 스프린트(Kafka 도입 시점) |
| 5 | ES 적용 (FE 키워드 검색 → ES) | 피드 검색을 FE의 `keywordLike`(자유 텍스트, 300ms 디바운스) 중심으로 ES 인덱싱, `skyStatusEqual`/`precipitationTypeEqual` 필터 + `createdAt`/`likeCount` 정렬 지원 | 이경신 | 3차 스프린트 |
| 6 | 로그인 암호화 처리 | 비밀번호 `BCryptPasswordEncoder` 해싱 저장, 전송 구간은 ALB/Nginx TLS 종료로 HTTPS 강제 | 신홍규 | 1차 스프린트 |
| 7 | APM 툴(Pinpoint, Datadog) | 3차 스프린트 성능 테스트 전(2차 스프린트 말) 계측 완료해 실측 데이터 확보 | 김호현 | 2차 스프린트 말 ~ 3차 초 |
| 8 | 옷 추천 알고리즘 + 간단한 챗봇 | 추천 알고리즘은 기존 계획(LLM 고도화) 유지. 챗봇은 `otboo-fe`에 대응 UI가 없어 신규 설계 필요 — LLM 기반 간단 Q&A 챗봇을 **선택적 스트레치 목표**로 배치 | 김하빈 | 4~5차 스프린트(선택, 시간 되면) |

---

## 👥 프로젝트 구성원과 R&R

| 팀원 | GitHub | 담당 도메인 | 주요 업무 |
| --- | --- | --- | --- |
| 김호현 (팀장) | `pintordev` | 인프라/공통 + 날씨·알림 인프라 | 프로젝트 초기화, CI/CD, 공통 인프라(ApiResponse·예외 처리), 기상청 단기예보 Open API 연동, 위치 기반 x/y 좌표·행정구역 변환(`GET /api/weathers/location`, Kakao/기상청 격자 변환), Spring Batch 배치(날씨 수집), 날씨 급변 알림 트리거, SSE 알림 시스템(공통 알림 이벤트 발행 구조) 구축, 분산 환경(Redis/Kafka/Nginx, ECS 다중 인스턴스) `심화` |
| 김하빈 | `kimhabin` | 의상 & 추천 | 의상 속성 정의(어드민), 의상 CRUD, 구매 링크 기반 의상 정보 자동 추출 `심화`, 날씨·프로필 기반 추천 알고리즘, LLM API 기반 추천 고도화 `심화`, 간단한 LLM 챗봇 `심화(선택)` |
| 신홍규 | `Zrp0x0` | 사용자 · 인증 | Spring Security + JWT 인증/인가, 회원가입/로그인/비밀번호 초기화, 어드민 초기화·권한 관리·계정 잠금, 프로필 관리(이미지·위치·온도민감도 — 위치 값은 FE가 날씨 도메인 API로 변환해 전달한 값을 그대로 저장), 소셜 로그인(Google/Kakao) `심화` |
| 이경신 | `Ksinny` | 소셜 (OOTD 피드 · 팔로우 · DM) | 피드 CRUD, 좋아요/댓글, 팔로우, DM 웹소켓 실시간 채팅, Elasticsearch 기반 피드 검색 `심화` |

> 도메인 배정은 "연관성 높은 4묶음" 기준의 초안이며, 킥오프 전 팀 논의 후 확정 예정입니다.
> 위치 좌표 변환은 "인증·사용자" 업무로 보이지만 실제 API 계약(`otboo-fe`)상 `weathers` 도메인 엔드포인트이므로 김호현 담당으로 배치했습니다 — 아래 [🔌 FE 연동 계약](#-fe-연동-계약-otboo-fe-기준) 참고.
>

**API 엔드포인트 총 개수**: **41개** (`docs/api-docs.json` 기준 — `otboo-fe`의 `api.json`과 거의 일치, 도메인별 세부 개수는 [🔌 FE 연동 계약](#-fe-연동-계약-otboo-fe-기준) 참고)

---

## 🗓️ 프로젝트 일정 요약

> 최종 발표일(08/31) 기준 역산한 일정입니다. 사전기간은 킥오프 논의(07/22~23)를 마치고 실제 셋업 작업은 **07/24 하루로 압축**했습니다.
>

| 항목 | 기간 | 내용 |
| --- | --- | --- |
| 사전 기간 | 07/24 (금) 하루 | 인프라 셋업, GitHub 레포/브랜치 전략 확정, ADR 스피드런, 계획서 작성 |
| 1차 스프린트 | 07/27 (월) ~ 07/31 (금) | 전 도메인 기본 기능 착수 (인증/사용자, 의상, 날씨 수집, 피드/팔로우/DM 기본 CRUD) |
| 2차 스프린트 | 08/03 (월) ~ 08/07 (금) | 기본 기능 요구사항 전체 완성 (프로필, 추천 기본 알고리즘, 알림, 웹소켓 DM 등) |
| 중간 발표 준비 | 08/08 (토) ~ 08/10 (월) 낮 | 데모 준비, 발표 자료 작성, 08/10 낮은 최종 리허설/버퍼 |
| 중간 발표 | 08/10 (월) **17:00–19:00** | 기본 기능 시연 + 심화 진행 계획 공유 |
| 3차 스프린트 | 08/11 (화) ~ 08/14 (금) | 1차 성능 테스트, 심화 기능 착수 (소셜 로그인, 구매링크 추출, LLM 추천, ES 검색) — 08/10은 발표일로 스프린트에서 제외 |
| 4차 스프린트 | 08/17 (월) ~ 08/21 (금) | 심화 기능 마무리, 성능 보강 및 테스트 커버리지 80% 달성 |
| 5차 스프린트 | 08/24 (월) ~ 08/28 (금) | Redis/Kafka 전환·Nginx 분산 환경 구축, 통합 테스트, 버그 수정 |
| 최종 발표 준비 | 08/29 (토) ~ 08/30 (일) | 최종 발표 자료 및 데모 시나리오 준비 |
| 최종 발표 | 08/31 (월) **09:00–14:00** | 최종 발표 및 제출 |

---

## 📚 프로젝트 세부 계획

### 사전 기간 (07/24 금, 하루) — 전체 참여

> 07/22~23은 킥오프 논의(R&R·규칙 확정)에 이미 사용했으므로, 손 움직이는 준비 작업은 07/24 **하루로 압축**. 오전엔 결정만 빠르게 끝내고(ADR 스피드런), 오후엔 각자 스켈레톤 착수 — 못 끝낸 건 1차 스프린트 월요일(07/27) 오전까지 이어서 완료.

**오전 — ADR 스피드런 (전체 참여, GitHub Discussions에 결론만 기록)**
- ERD 확정 (`docs/erd.md` 초안 검토) + 패키지 구조(`domain`/`global`/`external`) 합의
- 공통 응답(`ApiResponse`/`ErrorCode`)·예외 구조, 커서 페이지네이션 공통 구조(`CursorPageResponse<T>`) 확정
- JWT 인증 구조 + CSRF/Refresh 쿠키 정책 확정
- SSE 알림 이벤트 인터페이스 확정 (이벤트명 `notifications` 고정)
- 테스트 픽스처 전략(EasyRandom vs FixtureMonkey), 외부 API 호출 방식(Feign Client) 결정 — 멘토 피드백 #1, #2
- **Comment 수정/삭제 API 필요 여부 확인** — 현재 스펙(`api-docs.json`)엔 등록/조회만 있음, 의도된 축소인지 멘토 확인
- **User 휴면 계정 정책 확정** — 마지막 로그인 90일 기준 배치 전환 + 로그인 시 자동 재활성화 (docs/conventions.md 2-1 참고)
- 브랜치 전략·커밋 컨벤션·PR 머지조건(2인)·라벨 최종 확정

**오후 — 스켈레톤 착수 (미완료 시 1차 스프린트 월요일 오전까지)**
- 김호현 — GitHub 레포 생성, Spring Boot 초기화, IAM 계정 발급, `.gitignore`/`.github` 초기 설정, `ApiResponse`/예외/`CursorPageResponse<T>` 스켈레톤 ⚠️ 블로커
- 신홍규 — JWT/Spring Security 필터체인, CSRF/Refresh 쿠키 스켈레톤 ⚠️ 블로커
- 김하빈·이경신 — 담당 도메인 API 초안 리스트업(문서만, 개발은 1차 스프린트부터)
- GitHub Actions CI/CD, PR/Issue/ADR 템플릿, `.coderabbit.yaml` 이식은 **블로킹 작업이 아니므로** 김호현이 1차 스프린트 초반과 병행해 마무리(아래 1차 스프린트 참고)

### 1차 스프린트 (07/27 ~ 07/31) — 기본 기능 착수

> **⚠️ 블로커**: 신홍규의 Spring Security + JWT 인증 기반이 늦어지면 전체 팀의 API 개발이 지연됨. 김호현의 공통 예외 처리(`ApiResponse`)와 SSE 알림 이벤트 인터페이스 확정도 각각 전체 API 응답 구조와 다른 도메인(피드/팔로우/DM)의 알림 연동을 블로킹함.
> 김호현은 위 블로커 스켈레톤과 별개로 월~화 중 GitHub Actions CI/CD, PR/Issue/ADR 템플릿, `.coderabbit.yaml` 이식(사전기간에서 이월)도 병행.
>

| 담당 | 완료 목표 |
| --- | --- |
| 김호현 | 공통 인프라(`ApiResponse`, 예외 처리), 기상청 단기예보 API 연동(Feign Client, 위치 x/y 좌표 변환 `GET /api/weathers/location` 포함), 날씨 데이터 수집 배치 기본(매시 정각 스케줄), SSE 알림 인프라(`/api/sse`, 이벤트명 `notifications` 고정) 및 이벤트 발행 인터페이스 확정 |
| 김하빈 | 의상 속성 정의(어드민, 사용 중인 정의 삭제 시 409 처리 포함), 의상 등록/조회 CRUD 기본 |
| 신홍규 | JWT 인증/인가 기반, 회원가입·로그인(비밀번호 BCrypt 암호화), 어드민 초기화·권한 관리·계정 잠금 |
| 이경신 | OOTD 피드 CRUD 기본, 팔로우 기본 기능 |

### 2차 스프린트 (08/03 ~ 08/07) — 기본 기능 완성

| 담당 | 완료 목표 |
| --- | --- |
| 김호현 | 날씨 급변(강수·기온 급변) 알림 트리거, 배치 안정화, 알림 읽음 정리 배치(읽음 후 7일 경과분 삭제), 날씨 데이터 retention 배치 |
| 김하빈 | 날씨·프로필 데이터 기반 자체 추천 알고리즘 1차 구현 |
| 신홍규 | 비밀번호 초기화(임시 비밀번호), 프로필 관리(이미지·위치·온도민감도), 휴면 계정 배치(마지막 로그인 추적 + 90일 배치 전환 + 로그인 시 자동 재활성화) |
| 이경신 | 피드 좋아요/댓글, 팔로우 알림 연동, DM 웹소켓(`/ws`, STOMP `/pub/direct-messages_send` · `/sub/direct-messages_{userId쌍}`) 기본 송수신 구현 |

### 중간 발표 준비 및 발표 (08/08 ~ 08/10, 발표 17:00–19:00)

- 기본 기능 전체 데모 시나리오 준비 (08/08~09 주말)
- 심화 기능 진행 계획 발표 자료 정리
- 08/10 낮(09:00~17:00): 최종 리허설/버퍼 — 이 시간은 3차 스프린트에 포함하지 않음
- 08/10 17:00–19:00: 중간 발표

### 3차 스프린트 (08/11 ~ 08/14) — 성능 테스트 + 심화 착수

| 담당 | 완료 목표 |
| --- | --- |
| 김호현 | Spring Actuator 커스텀 메트릭으로 배치 모니터링, 성능 테스트 결과 기반 배치 튜닝, 1차 성능 테스트(부하 테스트 도구 선정 및 측정) |
| 김하빈 | 구매 링크 기반 의상 정보 자동 추출(웹 스크래핑, Feign Client + LLM) `심화` 착수 |
| 신홍규 | 소셜 로그인(Google/Kakao) 연동 `심화` |
| 이경신 | Elasticsearch 피드 검색 `심화` 착수 (키워드 검색 + 날씨/강수 필터 + 정렬 인덱싱), DM 실시간 안정성 테스트 |

### 4차 스프린트 (08/17 ~ 08/21) — 심화 마무리 + 성능 보강

- 심화 기능(소셜 로그인, 구매링크 추출, LLM 추천, ES 검색) 마무리
- LLM API(OpenAI/HuggingFace/OpenRouter) 연동으로 추천 성능 향상 `심화`
- (선택) 간단한 LLM 챗봇 착수 — 옷 추천 관련 자연어 질의응답, 시간 되면 진행 `심화(선택)`
- 3차 성능 테스트 결과 기반 병목 구간 개선
- 테스트 커버리지 80% 달성, Docker Compose 로컬 분산 환경 구성 및 검증
- README 정리(커버리지 배지 포함)

### 5차 스프린트 (08/24 ~ 08/28)

- Redis(3차 스프린트 이후 AWS ElastiCache 전환 검토)·Kafka(AWS MSK 대신 비용 효율적 대안 검토 중)·Nginx 리버스 프록시 기반 분산 환경 구축 `심화`
- SSE 알림을 Kafka+Redis 파이프라인으로 전환, 유실 방지 로직 반영(`LastEventId` 기반 재연결 복구 + 미전송 알림 재시도 배치) — 멘토 피드백 #4 `심화`
- (선택) LLM 챗봇 마무리 — 4차에서 이어온 경우 `심화(선택)`
- AWS ECS 다중 인스턴스 + 로드밸런싱 구성 `심화`
- 전체 통합 테스트, 버그 수정, API 스펙(Swagger) 최종 일치 검증
- 제공된 프론트엔드 최종 연동 확인
- 세부 범위(통합 테스트 커버리지 등)는 4차 스프린트 진행 상황을 보고 재점검

### 최종 발표 준비 (08/29 ~ 08/30) 및 최종 발표 (08/31, 09:00–14:00)

- 최종 발표 자료 및 데모 시나리오 준비
- 최종 발표 및 프로젝트 제출

---

## ⚙️ 기술 스택 및 협업 도구

| 분류 | 사용 도구 |
| --- | --- |
| Backend | Spring Boot, Spring Security |
| DB / ORM | PostgreSQL(AWS RDS), Spring Data JPA |
| 인증 | JWT 기반 인증/인가, 소셜 로그인(Google/Kakao) `심화` |
| 실시간 통신 | WebSocket(DM), SSE(알림) |
| 배치 | Spring Batch (날씨 수집 — 매시 정각, 알림 트리거) |
| 캐시 | Redis / Spring Cache `심화` — 초기 개발은 외부(비-AWS) 서버 사용, 3차 스프린트 이후 AWS ElastiCache 전환 검토, 이후 SSE 알림 전달 유실 방지에도 활용 |
| 메시징 | Kafka `심화` — AWS MSK 대신 비용 효율적 대안(예: Confluent Cloud) 검토 중, SSE 알림 발행 파이프라인(유실 방지) 백엔드로 사용 |
| 검색 | Elasticsearch `심화` — 피드 키워드 검색(FE `keywordLike`) + 날씨/강수 필터 인덱싱 |
| Infra | AWS ECS(EC2, 다중 태스크) + Nginx/ALB, RDS, Docker / Docker Compose |
| CI/CD | GitHub Actions — `ci-pr.yml`/`ci-push.yml`/`cd.yml`/`pr-review.yml`/`review-discord.yml` 분리, ECS Rolling Update + 헬스체크 실패 시 자동 롤백, AWS Secrets Manager/Parameter Store 연동 |
| 코드 품질 | 컨벤션 문서 준수 + CodeRabbit(AI 코드 리뷰) |
| 테스트 픽스처 | EasyRandom 또는 FixtureMonkey (수동 빌더 대신 랜덤 픽스처 생성) |
| 모니터링 | APM(Pinpoint 또는 Datadog) — 3차 스프린트 성능 테스트 전 계측 완료 |
| 외부 API 클라이언트 | Feign Client (RestTemplate 미사용) — 기상청/Kakao/구매링크 크롤링/LLM 호출 |
| 외부 API | 기상청 단기예보 API, Kakao API(로컬/로그인), OpenAI / Hugging Face / OpenRouter |
| API 문서화 | springdoc-openapi (Swagger UI) |
| 협업 | Notion(회의록·대시보드), Discord(실시간 소통·웹훅 알림), GitHub Issues + Projects(칸반 보드) + Discussions(기술 논의) |
| 일정 관리 | GitHub Projects |

---

## 🔌 FE 연동 계약 (otboo-fe 기준)

> `otboo-fe`(React 19 + Vite + TS + Zustand + STOMP/SockJS + SSE) 코드 분석 결과. FE가 이미 이 계약을 기준으로 구현돼 있으므로, 백엔드는 아래 규칙을 그대로 맞춰야 함(자유롭게 재설계 불가).

### API 엔드포인트 요약 (총 41개, `docs/api-docs.json` 기준)

| 태그(도메인) | 개수 | 담당 |
| --- | --- | --- |
| 피드 관리 | 8 | 이경신 |
| 프로필 관리 | 7 | 신홍규 |
| 팔로우 관리 | 5 | 이경신 |
| 의상 관리 | 5 | 김하빈 |
| 인증 관리 | 5 | 신홍규 |
| 의상 속성 정의 | 4 | 김하빈 |
| 날씨 관리 | 2 | 김호현 |
| 알림 | 2 | 김호현 |
| DirectMessage(REST, 목록 조회만) | 1 | 이경신 |
| 추천 관리 | 1 | 김하빈 |
| SSE 구독 | 1 | 김호현 |

`otboo-fe/api.json`(FE가 들고 있는 로컬 스펙)과 대조하면 40/41개가 일치. 유일한 차이는 `GET /api/weathers/location`으로, FE 로컬 스펙엔 없지만 실제 코드(`LocationInput.tsx` → `getWeatherLocation`)에서 호출하고 있어 반드시 구현 필요.

### 인증

- 로그인(`POST /api/auth/sign-in`)은 JSON이 아니라 **`multipart/form-data`**로 `username`, `password` 필드를 받음
- 액세스 토큰은 응답 바디(`JwtDto.accessToken`)로 내려주고 FE는 메모리(Zustand)에만 보관 — 새로고침 시 `POST /api/auth/refresh` 호출로 복구
- 리프레시 토큰은 body가 아니라 **`REFRESH_TOKEN` httpOnly 쿠키**로 관리 (`/api/auth/refresh`가 쿠키에서 읽음)
- CSRF 토큰은 `GET /api/auth/csrf-token` 호출 시 **`XSRF-TOKEN` 쿠키**로 내려줌 — FE는 앱 부팅 시 항상 1회 호출(`CsrfInitializer`)
- axios 요청마다 `Authorization: Bearer {accessToken}` 헤더 자동 첨부, 401 응답 시 자동으로 refresh 후 재시도

### 소셜 로그인 `심화`

- FE는 `window.location.href = "/oauth2/authorization/{google|kakao}"`로 리다이렉트만 함 — Spring Security OAuth2 Client 표준 흐름 그대로 사용
- 커스텀 REST 엔드포인트가 아니므로 `api-docs.json`에는 나타나지 않음
- **ADR 필요**: OAuth2 로그인 성공 후 SPA(해시 라우터)로 어떻게 토큰을 전달할지(리다이렉트 쿼리 파라미터 vs 쿠키 발급 후 프론트에서 refresh 호출 등) 신홍규가 3차 스프린트 착수 전 결정

### WebSocket (DM)

- SockJS 엔드포인트: `/ws`, STOMP 프로토콜, CONNECT 헤더에 `Authorization: Bearer {accessToken}`
- 발행(send) destination: `/pub/direct-messages_send`, body `{ senderId, receiverId, content }`
- 구독(subscribe) destination: `/sub/direct-messages_{두 사용자 UUID를 문자열 사전순 정렬 후 "_"로 연결}` (예: `a1..." < "b2..."` → `/sub/direct-messages_a1..._b2...`)
- 이 네이밍 규칙을 정확히 구현해야 FE 수정 없이 연동됨 — 이경신 1~2차 스프린트 핵심 작업

### SSE (알림)

- 엔드포인트: `GET /api/sse`, `Authorization: Bearer` 헤더, 재연결 시 유실 이벤트 복구용 `LastEventId` 쿼리 파라미터 지원
- 이벤트명은 **`notifications`로 고정** (FE가 `eventSource.addEventListener('notifications', ...)`로 구독), payload는 `NotificationDto` JSON

### 알려진 계약 차이 (보강 필요)

- FE 타입(`UserDto`)에는 `linkedOAuthProviders: OAuthProvider[]` 필드가 있으나 현재 `api-docs.json`의 `UserDto` 스키마에는 없음 — 소셜 로그인 연동 완료 시 응답에 추가 필요 (설정 페이지의 "연동된 계정" 표시용으로 추정, 현재 FE 컴포넌트에서 아직 미사용). `User`에 배열 컬럼을 두지 않고 별도 `social_accounts` 테이블(`user_id`/`provider`/`provider_id`)을 조회해 매핑 시점에 채우는 방식으로 확정 — `docs/conventions.md` §2-1, `docs/erd.md` 참고

---

## 🗂️ 도메인별 데이터 운영 전략

> 회원 탈퇴 API가 없다는 걸 확인해, "삭제"를 어떻게 처리할지 도메인별로 정리했습니다. 상세 근거·엔티티 필드는 `docs/conventions.md`([2-1. 도메인별 데이터 운영 전략]), 테이블 구조는 `docs/erd.md`(초안) 참고 — 여기서는 일정에 영향 있는 부분만 요약합니다.

| 도메인 | 정책 | 비고 |
| --- | --- | --- |
| User | 하드 삭제 없음 → **휴면 계정**으로 대체 | 로그인 90일 미접속 시 배치로 휴면 전환(내부 필드만, API 계약 불변), 로그인하면 자동 재활성화, 관리자 `locked`와는 별개 필드 |
| Clothes | 하드 삭제, `Feed`엔 영향 없음 | `Feed.ootds`가 게시 시점 JSONB 스냅샷이라 삭제해도 기존 피드 표시가 그대로 유지됨(별도 cascade 불필요) |
| ClothesAttributeDef | 사용 중인 정의는 삭제 시 409 | 이미 등록된 의상의 속성값 정합성 보호 |
| Feed | 하드 삭제 + 댓글/좋아요 cascade, 날씨는 **스냅샷 저장**(FK 아님) | 날씨 정리 배치가 과거 피드 표시를 깨지 않도록 |
| Comment | ⚠️ 수정/삭제 API가 스펙에 없음 — **사전기간에 팀/멘토 확인 필요** | 의도된 축소인지 스펙 누락인지 미확정 |
| Follow / FeedLike | 하드 삭제, 복합 유니크 제약으로 중복 방지 | — |
| Notification | 소프트(읽음 처리 `readAt`) + 읽음 후 7일 경과분만 배치로 물리 삭제 | `DELETE` 메서드지만 설명은 "읽음 처리" |
| DirectMessage | 삭제 API 없음, 무기한 보관 | 보관기간 정책은 이번 프로젝트 범위 밖 |
| Weather | 하드 삭제 + retention 배치(예: 7일 경과분 정리) | Feed가 스냅샷을 갖고 있어 안전하게 정리 가능 |

이 결정에 따라 아래 작업이 일정에 추가됩니다 — 자세한 배치는 `프로젝트 상세 일정 (일 단위).md` 참고:
- 사전기간: ERD 확정(`docs/erd.md` 초안 검토), Comment 삭제 API 필요 여부 확인
- 1차 스프린트(김하빈): ClothesAttributeDef 삭제 시 참조 확인(409)
- 2차 스프린트(신홍규): 휴면 계정 배치(마지막 로그인 추적 + 전환 + 자동 재활성화)
- 2차 스프린트(김호현): 알림 읽음 정리 배치, 날씨 데이터 retention 배치

---

## ☁️ 인프라·운영 정책

### AWS 비용 관리

- 월 예산 **$150** (재원: 학교 지원금 200,000원 [환율 1,500원/$ 기준 약 $133] + AWS 프리티어 크레딧 $200)
- Billing Alarm 설정으로 예산 초과 시 팀 전체 알림
- 비용이 큰 리소스(Redis/Kafka 등)는 단계적 도입으로 비용 통제 (아래 인프라 구성 참고)

### IAM 권한

- 팀원별 IAM 계정 발급, 기본 **읽기 전용** 정책 부여
- 쓰기 권한이 필요한 작업은 팀장에게 요청 후 필요한 범위만 임시로 부여

### 인프라 구성

- 계획서 원안 유지: AWS ECS(EC2) 다중 태스크 + Nginx/ALB
- DB: AWS RDS(PostgreSQL)
- Redis: 초기 개발 단계는 외부(비-AWS) 서버로 운용, 3차 스프린트 이후 AWS 전환(ElastiCache 등) 검토
- Kafka: AWS MSK 대신 비용 효율적인 대안(예: Confluent Cloud) 검토 — 비용 관리 필수

### 기술 결정 논의 방식 — ADR

> "RFC"라는 별도 단계는 두지 않고 **ADR(Architecture Decision Record) 한 가지 용어로 통일**합니다. 논의든 결론이든 전부 ADR 스레드 안에서 이뤄집니다.

- ADR은 **GitHub Discussions**에 스레드로 남깁니다 — Issue가 아닙니다. `adr` 카테고리를 만들어 모아둡니다.
- 제목 형식: `[ADR] {결정 주제}` (예: `[ADR] SSE 알림 이벤트 발행 인터페이스 확정`)
- 본문 구조: `배경` → `선택지` → `결정` → `영향(누구의 어떤 작업에 반영되는지)`
- 결정이 특정 Issue의 작업을 블로킹하면, 해당 Issue 본문/코멘트에 ADR Discussion 링크를 남겨 연결 (Issue 쪽엔 `adr` 라벨을 붙이지 않음 — 아래 [GitHub Labels](#github-labels)의 타입/도메인 라벨과는 별개 체계)
- 데일리 스크럼 정리, 주간 회의록 등은 Notion에 기재

### 협업 도구 역할 분담

| 도구 | 역할 |
| --- | --- |
| Notion | 회의록, 대시보드/진행 현황판 |
| Discord | 봇 계정 + 웹훅 연동(PR/CI/리뷰 결과 알림), 자료 공유, 개인 사정 공유(팀원 개별 채널), 그 외 회의는 codeit 공식 채널에서 진행 |
| GitHub | 코드 + 이슈/PR + 일정(Projects 칸반) 전부, 기술 결정(ADR)은 Discussions로 흡수 |

### 회고

- 스프린트별 회고 없이, 프로젝트 종료 시점(최종 발표 이후) 1회 진행

### 템플릿 / 코딩 컨벤션 이식

- `.github/` 템플릿(PR, Issue 타입별), `.coderabbit.yaml`, 코딩 컨벤션(`docs/conventions.md`)은 otboo 도메인·JWT 인증 방식에 맞게 작성 (라벨 작업 포함) — 별도 착수 예정
- ADR은 Issue가 아니라 위 [기술 결정 논의 방식](#기술-결정-논의-방식--adr)에 따라 GitHub **Discussions**로 관리하므로 별도 ADR용 Issue 템플릿은 두지 않음

---

## 🧩 규칙 수립

> 대략적인 내용. 구체적인 세부 규칙은 팀 Discord 또는 프로젝트 내 `docs/` 문서로 별도 관리 예정.
>

### 브랜치 전략

```
main (production)
└── dev (staging)
    └── {prefix}/{domain}/{description}
        예) feat/auth/login, fix/feed/duplicate-like
```

prefix: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `batch`, `deploy`

> `infra`가 아니라 `deploy`를 씁니다 — "인프라/공통" **도메인 라벨**과 이름이 겹치는 걸 피하기 위함입니다. CI/CD·Docker·ECS·Secrets 등 배포·인프라 관련 작업 전부 `deploy` prefix.

### 워크플로우 (10단계)

**Fork 기반**입니다 — `origin`은 각자의 개인 fork, `upstream`은 팀 레포(`sb11-code-rangers/sb11-otboo-team4`)입니다. 팀원은 각자 GitHub에서 팀 레포를 fork한 뒤 로컬에 `origin`(내 fork)/`upstream`(팀 레포) 두 리모트를 등록하고 시작합니다.

1. GitHub Issue 등록 — `[FEAT] 회원가입 API 구현` 형식
2. `git switch dev`
3. `git pull upstream dev`
4. `git push origin dev`
5. `git switch -c feat/auth/register`
6. 개발 작업 (TDD Red → Green → Refactor)
7. PR 전 `git pull upstream dev` → conflict 확인
8. `git push origin feat/auth/register`
9. PR 작성: `upstream:dev ← origin:feat/auth/register`, 제목은 squash 커밋과 동일 (`feat: 회원가입 API 구현`), `Closes #이슈번호` 명시
10. **2인 이상** 리뷰 승인 → Squash and Merge

### 커밋 컨벤션

TDD 단계별 커밋:

```
test(red): 이메일 중복 회원가입 예외 테스트 추가
test(green): 이메일 중복 검증 로직 구현
refactor: UserService 예외 처리 공통화
```

일반 커밋:

```
feat: 팔로우 커서 페이지네이션 구현
fix: DM 웹소켓 재연결 시 중복 메시지 수신 버그 수정
batch: 날씨 데이터 수집 배치 구현
deploy: GitHub Actions CI 워크플로우 추가
docs: API 명세 업데이트
```

### PR / Issue 규칙

| 항목 | 내용 |
| --- | --- |
| Issue 제목 | 브랜치/커밋 prefix를 대문자 대괄호로 표기 — `[FEAT]`/`[FIX]`/`[REFACTOR]`/`[DOCS]`/`[TEST]`/`[CHORE]`/`[BATCH]`/`[DEPLOY]` (예: `[FEAT] DM 웹소켓 송수신 구현`). ADR은 Issue가 아니라 Discussions에 남기므로 이 목록에 없음 — [기술 결정 논의 방식](#기술-결정-논의-방식--adr) 참고 |
| PR 제목 | squash 커밋 메시지와 동일 — 예) `feat: 회원가입 구현` |
| PR 대상 | `dev` 브랜치 |
| 머지 방식 | **Squash and Merge** |
| 머지 조건 | **2인 이상** 리뷰 승인 + CI 통과 + `Closes #이슈번호` 명시 |
| 직접 push 금지 | `dev` / `main` 브랜치 직접 push 금지 |
| Issue 단위 | API 1개 = Issue 1개 원칙, 배치·심화 항목은 별도 Issue |
| 템플릿 | `.github/pull_request_template.md`, `.github/ISSUE_TEMPLATE/` — 초안을 `docs/github-templates/`에 작성해둠, 실제 레포 생성 시 그대로 복사 |

### 네이밍 컨벤션

| 대상 | 규칙 |
| --- | --- |
| 클래스 | PascalCase |
| 변수·메서드 | camelCase |
| DB 테이블/컬럼 | snake_case |
| DTO | Java record 사용 |
| PK | UUID |
| 시간 타입 | `Instant` (LocalDateTime 사용 금지) |

### 코딩 규칙 주요 사항

- Service 클래스: `@Transactional(readOnly = true)` 기본, 변경 메서드만 `@Transactional` 개별 지정
- Setter 금지 — 상태 변경은 의도가 드러나는 메서드명 사용
- Entity 생성은 정적 팩토리 메서드만 사용, `@Builder`/`@SuperBuilder`는 `private`로만(외부 노출 금지)
- Entity → DTO 변환: MapStruct 없이 수동 매퍼 클래스 사용 (Service 내 직접 변환 금지)
- 로깅: `@Slf4j` 사용, `System.out.println` 금지
- 인증: Spring Security + JWT, 인가는 역할(`ADMIN`/`USER`) 기반
- 패키지 구조: 대분류 `domain` / `global` / `external`, 소분류 `config`/`exception`/`dto`/...
- 코드 스타일: 컨벤션 문서 준수 필수 + CodeRabbit으로 1차 자동 리뷰
- 외부 API 호출은 `RestTemplate` 대신 **Feign Client** 사용 (`external/` 패키지에 클라이언트 인터페이스 정의) — 멘토 피드백 #1
- 비밀번호는 `BCryptPasswordEncoder`로 해싱 저장 — 멘토 피드백 #6

### GitHub Labels

> 라벨은 서로 다른 두 축입니다 — Issue 하나에 **도메인 라벨 1개 + 타입 라벨 1개**(+선택적으로 `advanced`)를 함께 붙입니다. 타입 라벨은 브랜치/커밋/Issue 제목 prefix와 이름이 1:1로 대응합니다(위 [브랜치 전략](#브랜치-전략) 참고). ADR은 Issue가 아니라 Discussions에서 관리하므로 여기 라벨 체계에 포함되지 않습니다.

**도메인** (Issue가 어느 도메인 소속인지)

| Label | 설명 |
| --- | --- |
| `infra` | 인프라/공통 |
| `auth-user` | 사용자·인증·프로필 |
| `clothes-recommend` | 의상·추천 |
| `weather-notification` | 날씨·알림 |
| `social` | 피드·팔로우·DM |

**타입** (어떤 종류의 작업인지 — 브랜치/커밋 prefix와 동일한 8종)

| Label | 설명 |
| --- | --- |
| `feat` | 기능 개발 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 |
| `docs` | 문서화 |
| `test` | 테스트 작성 |
| `chore` | 계획서·협업규칙·프로젝트 설정·DB 연동 등 |
| `batch` | 배치 작업 |
| `deploy` | CI/CD·Docker·ECS·Secrets 등 배포/인프라 작업 |

**수식** (타입/도메인과 별도 축 — 필요할 때만 추가)

| Label | 설명 |
| --- | --- |
| `advanced` | 심화 항목 |

### 데일리 스크럼

- **시간**: 매일 오전 9:00–9:15 고정
- **형식**:
    1. 어제 한 것
    2. 오늘 할 것
    3. 막히는 것 (블로커)
- **원칙**: 15분 이내 종료. 추가 논의는 별도 회의로 전환
- **코어타임**: 13:00–17:00

### 테스트 규칙

- **TDD 필수** — Red → Green → Refactor 각 단계 별도 커밋
- **커버리지 80% 이상** — GitHub Actions CI 게이트, README 배지 표시
- 테스트 클래스명: `{대상클래스}Test`, 메서드명·`@DisplayName`은 한글
- `@Nested` + `given / when / then` 구조 준수
- Service: `@ExtendWith(MockitoExtension)` 단위 테스트
- Repository: `@DataJpaTest` 슬라이스 테스트 — Testcontainers로 실제 PostgreSQL 사용 (H2 미사용)
- Controller: `@WebMvcTest` 슬라이스 테스트
- 외부 API(기상청/Kakao/LLM) 연동 로직은 실제 API 호출 기반 검증 테스트 포함
- 테스트 픽스처는 EasyRandom 또는 FixtureMonkey로 생성 (수동 빌더/생성자 나열 지양) — 멘토 피드백 #2

---

## 🚨 예상 문제점 / 아쉬운 점

- **4인 소규모 팀 + 전체 심화 기능 목표**: 소셜 로그인, 구매링크 자동 추출, LLM 추천, Redis/Kafka/Nginx 분산 환경, Elasticsearch까지 모든 심화 항목을 목표로 하고 있어 인원 대비 범위가 매우 넓음. 3차 스프린트 시작 시점(중간 발표 이후)에 진행 상황을 보고 우선순위를 재조정할 필요가 있음
- **공통 인프라 블로킹**: 신홍규의 JWT 인증 기반과 팀장(김호현)의 공통 예외 처리가 늦어지면 전체 팀이 개발을 시작하지 못함
- **크로스 도메인 알림 의존성**: SSE 알림은 날씨(김호현)·피드/팔로우/DM(이경신)·의상 속성(김하빈)·권한 변경(신홍규) 등 거의 모든 도메인에서 발행되므로, 이벤트 인터페이스 사전 합의가 필수
- **WebSocket/DM 안정성**: 다중 클라이언트 환경에서 실시간 메시지 유실·중복 수신 가능성, 재연결 처리 필요
- **외부 API 불안정성**: 기상청 API, Kakao API, LLM API의 응답 형식 변경·장애·속도 이슈로 배치나 추천 기능이 실패할 수 있음
- **AWS 분산 환경 구성 난이도**: ElastiCache 전환, 관리형 Kafka 대안 도입, ECS 다중 인스턴스 + Nginx 구성이 복잡하고 클라우드 비용이 발생할 수 있어 5차 스프린트 내 완료가 촉박할 수 있음 — 월 $150 예산 내 관리 필수

---

## 🔧 개선 사항

- 사전 기간에 SSE 알림 이벤트 인터페이스(발행 메서드 시그니처)를 미리 정의하고 팀원들과 공유해 1차 스프린트부터 바로 사용 가능하게 준비
- 공통 인프라(`ApiResponse`, 예외 클래스, JWT 인증 베이스) 스켈레톤 코드를 사전 기간에 작성해 킥오프 당일 바로 사용 가능하게 준비
- 심화 기능 중 리스크가 큰 항목(Redis/Kafka/Nginx 분산 환경, Elasticsearch)은 3차 스프린트 초반에 진행 상황을 재점검하고, 필요 시 범위를 축소할 수 있는 플랜B 마련
- TDD 리듬 유지를 위해 각 API 구현 시 스프린트 마지막 날이 아닌 기능 완료 직후 테스트 작성
- 외부 API(기상청/Kakao/LLM) 실패 대비 재시도 로직 및 실패 알림 처리 설계
- 스프린트 종료일마다 미완료 이슈를 다음 마일스톤으로 이동시키는 정리 루틴 수립 (별도 회고 세션 없이 진행, [☁️ 인프라·운영 정책 — 회고](#회고) 참고)

---

## 📋 구성원별 체크포인트

| 팀원 | 사전 기간 | 1차 스프린트 | 2차 스프린트 | 중간 발표 | 3차 스프린트 | 4차 스프린트 | 5차 스프린트 | 최종 발표 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 김호현 |  |  |  |  |  |  |  |  |
| 김하빈 |  |  |  |  |  |  |  |  |
| 신홍규 |  |  |  |  |  |  |  |  |
| 이경신 |  |  |  |  |  |  |  |  |

> 각 셀에 완료 여부/이슈 번호를 채워 넣어 진행 상황을 트래킹하세요.
>
