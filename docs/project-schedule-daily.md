# 옷장을 부탁해 — 상세 일정 (일 단위)

> 원본: `프로젝트 수행 계획서 (초안).md`의 스프린트별 완료 목표를 일 단위로 쪼갠 실행 계획.
> 기준: 확정된 R&R + `docs/api-docs.json`(41개 엔드포인트, `otboo-fe` 코드 대조 완료) + [🔌 FE 연동 계약](./프로젝트%20수행%20계획서%20(초안).md) 반영.
> 각 항목은 이슈 1개 = 브랜치 1개 단위. `⚠️ 블로커` 표시 항목은 다른 팀원 작업을 막으므로 최우선 처리.
> 체크박스는 진척도 트래킹용 — 완료 시 체크하고 실제 GitHub Issue 번호를 옆에 채워 넣을 것.

---

## 0. 요일 배치 원칙

- **월~목**: 기능 구현 (TDD Red→Green→Refactor 사이클을 각 이슈 안에서 완결)
- **금**: 통합 확인 + 리뷰 반영 + 테스트 보강 + 다음 스프린트로 이월할 이슈 정리(칸반 갱신)
- 블로커성 이슈(다른 사람을 막는 것)는 항상 해당 주 **최우선 순번**으로 배치하고, 완료 전까지 후속 작업자는 인터페이스 스텁(mock)으로 먼저 개발
- 이슈 제목 prefix는 `프로젝트 수행 계획서`의 Issue 제목 규칙(`[FEAT]`/`[FIX]`/`[REFACTOR]`/`[DOCS]`/`[TEST]`/`[CHORE]`/`[BATCH]`/`[DEPLOY]`)을 따름
- `[ADR]`은 이 Issue prefix 목록과 다른 체계입니다 — Issue가 아니라 **GitHub Discussions** 스레드 제목이며, 이 문서에선 "블로킹되는 기술 결정" 항목을 표시하는 용도로만 씁니다 (수행계획서 [기술 결정 논의 방식 — ADR] 참고)
- 각 이슈 제목 뒤 `(METHOD /path)`는 `docs/api-docs.json`의 실제 엔드포인트를 가리킴 — 스펙 없는 항목(인프라/배치/WebSocket 등)은 표기 생략

---

## 1. 선행 의존성 맵 (블로커 요약)

| 순위 | 작업 | 담당 | 완료 기한 | 막는 대상 |
| --- | --- | --- | --- | --- |
| 1 | 공통 예외 응답(`ApiResponse`/`ErrorCode`/`GlobalExceptionHandler`) 구조 확정 | 김호현 | 사전기간 07/24(금) | 전 팀원 — 모든 API 응답 포맷 |
| 2 | JWT 인증 구조 + Spring Security 필터 체인, CSRF/Refresh 쿠키(`REFRESH_TOKEN`, `XSRF-TOKEN`) 흐름 확정 | 신홍규 | 사전기간 07/24(금) | 전 팀원 — FE axios가 401 시 자동으로 `/api/auth/refresh`를 호출하므로, 이 흐름이 없으면 인증이 필요한 모든 API 테스트가 막힘 |
| 3 | SSE 알림 이벤트 발행 인터페이스 확정 (이벤트명 `notifications` 고정 — FE가 이미 이 이름으로 구독) | 김호현 | 사전기간 07/24(금) | 이경신(피드/팔로우/DM 알림), 신홍규(권한변경 알림) |
| 4 | 패키지 구조(`domain`/`global`/`external`) + ERD 합의 | 전체 | 사전기간 07/24(금) 오전 | 전 팀원 — 이후 모든 도메인 코드 위치 |
| 5 | 커서 페이지네이션 공통 응답 구조(`CursorPageResponse<T>`, FE `CursorResponse<T>`와 필드명 일치: `data`/`nextCursor`/`nextIdAfter`/`hasNext`/`totalCount`/`sortBy`/`sortDirection`) | 김호현 | 사전기간 07/24(금) | 이경신(피드/팔로우/DM 목록), 김하빈(의상 목록), 신홍규(계정 목록) |
| 6 | WebSocket STOMP destination 규칙 확인 (`/pub/direct-messages_send`, `/sub/direct-messages_{userId 사전순 쌍}`) — FE가 이미 이 이름으로 고정 구현됨 | 이경신 | 1차 스프린트 초 | 본인 — 규칙을 어기면 FE 수정 없이 연동 불가 |
| 7 | 소셜 로그인 OAuth2 리다이렉트 후 SPA 토큰 전달 방식 ADR | 신홍규 | 3차 스프린트 착수(08/11) 전 | 본인(3차 심화 착수 지연 방지) |

> 사전기간이 07/24 **하루**로 압축됨에 따라 1~5번은 07/24 오전 ADR 스피드런(GitHub Discussions 스레드)으로 결론만 빠르게 내고, 결과는 오후에 각 담당자가 스켈레톤 코드로 반영. 오후 안에 못 끝내면 1차 스프린트 월요일(07/27) 오전까지 이어서 완료 — 그 전까지 후속 작업자는 목업 헤더/DTO로 개발 진행.
> 이전 초안에는 "위치 좌표 변환(신홍규)이 날씨 개인화(김호현)를 막는다"는 블로커가 있었으나, 실제 계약상 좌표 변환은 `weathers` 도메인 엔드포인트(`GET /api/weathers/location`)이고 FE가 프로필 저장 전에 별도로 호출해 완제품 위치값만 전달하므로 **이 블로커는 존재하지 않음** — 정정 반영.

### 멘토 피드백 반영 (수행 계획서 [🎓 멘토 피드백 반영] 참고)

| 항목 | 담당 | 배치 |
| --- | --- | --- |
| 외부 호출 Feign Client 전환 (RestTemplate 금지) | 김호현, 김하빈 | 사전기간 ADR → 1~3차 스프린트 적용 |
| 테스트 픽스처 EasyRandom/FixtureMonkey | 전체 | 사전기간 도구 선정 → 1차부터 적용 |
| 날씨 배치 주기(매시 정각, 실시간 폴링 불필요) | 김호현 | 1차 스프린트 |
| 로그인 암호화(BCrypt) | 신홍규 | 1차 스프린트 |
| APM(Pinpoint/Datadog) | 김호현 | 2차 스프린트 말 ~ 3차 초 |
| ES 키워드 검색 전환 | 이경신 | 3차 스프린트 |
| SSE Kafka+Redis 유실 방지 | 김호현 | 5차 스프린트 |
| LLM 챗봇(선택) | 김하빈 | 4~5차 스프린트(선택) |

---

## 2. Phase 0 — 사전기간 (07/24 금, 하루)

> 07/22~23은 킥오프 논의(R&R·규칙 확정)에 이미 사용했으므로, 손 움직이는 준비 작업은 07/24 **하루로 압축**. CI/CD 구성·템플릿 이식처럼 블로킹이 아닌 작업은 1차 스프린트로 이월.

### 07/24 (금) 오전 — ADR 스피드런 (전체 참여, 결론만 GitHub Discussions에 기록)
- [ ] 전체 — `[CHORE]` 브랜치 전략·커밋 컨벤션·PR 머지조건(2인) 확정 및 GitHub 브랜치 보호 규칙 설정
- [ ] 전체 — `[CHORE]` GitHub Labels(도메인 5종 + 타입 6종) 등록, Projects 칸반/마일스톤 세팅
- [ ] 전체 — `[ADR]` ERD 확정 (`docs/erd.md` 초안 검토·수정)
- [ ] 전체 — `[ADR]` 패키지 구조(`domain`/`global`/`external`) 합의
- [ ] 전체 — `[ADR]` Comment 수정/삭제 API 필요 여부 확인 (현재 스펙엔 등록/조회만 존재)
- [ ] 전체 — `[ADR]` User 휴면 계정 정책 확정 (마지막 로그인 90일 기준 배치 전환, 로그인 시 자동 재활성화, `locked`와 별개 필드)
- [ ] 전체 — `[DOCS]` `otboo-fe` 코드 리딩 세션: 로그인 multipart 계약, WebSocket/SSE destination·이벤트명, OAuth2 리다이렉트 흐름, CSRF/Refresh 쿠키 흐름 팀 전체 공유 (`docs/api-docs.json` + 수행계획서 [🔌 FE 연동 계약] 참고)
- [ ] 전체 — `[ADR]` 테스트 픽스처 전략 결정 (EasyRandom vs FixtureMonkey) — 멘토 피드백 #2
- [ ] 전체 — `[ADR]` 외부 API 호출은 Feign Client로 통일 (RestTemplate 미사용) — 멘토 피드백 #1
- [ ] 전체 — `[ADR]` 공통 응답/예외/`CursorPageResponse<T>` 구조, JWT 인증 구조, SSE 이벤트 인터페이스(이벤트명 `notifications`) 결론 확정 (구현은 오후~월요일)

### 07/24 (금) 오후 — 스켈레톤 착수 (미완료 시 1차 스프린트 월요일 오전까지)
- [ ] 김호현 — `[CHORE]` GitHub 레포 생성, Spring Boot 프로젝트 초기화, `.gitignore`/`.github` 초기 설정
- [ ] 김호현 — `[CHORE]` IAM 계정 발급(팀원별 읽기 전용), Notion 워크스페이스·Discord 채널 구조 세팅
- [ ] 김호현 — `[FEAT] ApiResponse/ErrorCode/GlobalExceptionHandler 스켈레톤 구현` ⚠️ 블로커
- [ ] 김호현 — `[FEAT] CursorPageResponse<T> 공통 유틸 구현 (FE CursorResponse<T> 필드명 일치)` ⚠️ 블로커
- [ ] 김호현 — `[ADR] SSE 알림 이벤트 발행 인터페이스 확정 (이벤트명 notifications 고정)` ⚠️ 블로커
- [ ] 신홍규 — `[ADR] JWT 인증 구조 + Spring Security 필터 체인, CSRF/Refresh 쿠키 정책 결정 + 스켈레톤` ⚠️ 블로커
- [ ] 김하빈 — `[DOCS]` 의상/추천 도메인 API 초안 리스트업 (`docs/api-docs.json` 기준 확인)
- [ ] 이경신 — `[DOCS]` 피드/팔로우/DM 도메인 API 초안 리스트업, STOMP destination 규칙 재확인
- [ ] 전체 — 계획서 최종 확정, 데일리 스크럼(9:00–9:15) 첫 실행 리허설

### 1차 스프린트로 이월 (블로킹 아님 — 김호현이 월~화 병행)
- [ ] 김호현 — `[DEPLOY] GitHub Actions CI 구성 (ci-pr.yml — 테스트 + 커버리지 80% 게이트)`
- [ ] 김호현 — `[DEPLOY] GitHub Actions CD 구성 (cd.yml — ECS 배포 스켈레톤, profile: base/local/test/dev/prod)`
- [ ] 김호현 — `[DEPLOY] .coderabbit.yaml, pr-review.yml, review-discord.yml 작성`
- [ ] 전체 — `[CHORE]` `docs/github-templates/`의 PR/Issue 템플릿 초안을 `.github/`로 복사·검토

---

## 3. Phase 1 — 1차 스프린트 (07/27 월 ~ 07/31 금) — 기본 기능 착수

> 사전기간에 인증/공통 스켈레톤이 끝나므로 실제 JWT·ApiResponse 사용 가능. 지연 시 팀원들은 목업 헤더로 우선 개발 후 화요일 오후 교체. 김호현은 아래 업무와 별개로 월~화 중 CI/CD 구성, PR/Issue 템플릿, `.coderabbit.yaml` 이식(사전기간에서 이월)도 병행.

### 07/27 (월)
- [ ] 신홍규 — `[FEAT] 회원가입 API 구현 (POST /api/users, 비밀번호 BCryptPasswordEncoder 해싱)` — 멘토 피드백 #6
- [ ] 신홍규 — `[FEAT] 로그인 API 구현 (POST /api/auth/sign-in, multipart/form-data: username/password)` ⚠️ 블로커 — 화요일 오전까지 완료 목표
- [ ] 김호현 — `[FEAT] 기상청 단기예보 API 연동 (GET /api/weathers, Feign Client)` — 멘토 피드백 #1
- [ ] 김하빈 — `[FEAT] 의상 속성 정의 목록/등록 API 구현 (GET, POST /api/clothes/attribute-defs)`
- [ ] 이경신 — `[FEAT] 피드 등록 API 구현 (POST /api/feeds, weatherId로 조회한 날씨 요약을 Feed에 스냅샷 저장 — FK 아님, erd.md 설계 노트 4번 참고)`

### 07/28 (화)
- [ ] 신홍규 — `[FEAT] 토큰 재발급 API 구현 (POST /api/auth/refresh, REFRESH_TOKEN 쿠키)` ⚠️ 블로커 — FE 401 자동 재시도 흐름에 필수
- [ ] 신홍규 — `[FEAT] CSRF 토큰 조회 API 구현 (GET /api/auth/csrf-token, XSRF-TOKEN 쿠키)`
- [ ] 신홍규 — `[FEAT] 로그아웃 API 구현 (POST /api/auth/sign-out)`
- [ ] 김호현 — `[FEAT] 위치 정보(좌표 변환) API 구현 (GET /api/weathers/location, Kakao/기상청 격자 변환, Feign Client)`
- [ ] 김호현 — `[BATCH] 날씨 수집 배치 Job 구현 (매시 정각 스케줄 — 실시간 폴링 불필요, 멘토 피드백 #3)`
- [ ] 김하빈 — `[FEAT] 의상 속성 정의 수정/삭제 API 구현 (PATCH, DELETE /api/clothes/attribute-defs/{definitionId}, 사용 중인 정의는 삭제 시 409)`
- [ ] 이경신 — `[FEAT] 피드 목록 조회 API 구현 (GET /api/feeds, 커서 페이지네이션)`

### 07/29 (수)
- [ ] 신홍규 — `[FEAT] 어드민 계정 목록/권한/잠금 API 구현 (GET /api/users, PATCH .../role, PATCH .../lock)`
- [ ] 김호현 — `[FEAT] SSE 알림 인프라 구현 (GET /api/sse, LastEventId 재연결 지원)`
- [ ] 김호현 — `[FEAT] 알림 목록 조회 API 구현 (GET /api/notifications)`
- [ ] 김하빈 — `[FEAT] 의상 목록 조회/등록 API 구현 (GET, POST /api/clothes, multipart 이미지)`
- [ ] 이경신 — `[FEAT] 팔로우 생성/취소 API 구현 (POST /api/follows, DELETE /api/follows/{followId})`

### 07/30 (목)
- [ ] 전체 — 인증 스텁 → 실제 JWT 전환 통합 확인
- [ ] 신홍규 — `[TEST]` 회원가입/로그인/토큰재발급 테스트 커버리지 보강
- [ ] 김호현 — `[FEAT] 알림 읽음 처리 API 구현 (DELETE /api/notifications/{notificationId}, 물리삭제 아닌 readAt 갱신)`, `[REFACTOR]` 날씨 배치 안정화
- [ ] 김하빈 — `[FEAT] 의상 수정/삭제 API 구현 (PATCH, DELETE /api/clothes/{clothesId}, Feed.ootds가 JSONB 스냅샷이라 삭제해도 기존 피드 표시엔 영향 없음)`
- [ ] 이경신 — `[FEAT] 팔로우 요약/팔로잉/팔로워 목록 API 구현 (GET /api/follows/summary, followings, followers)`

### 07/31 (금)
- [ ] 전체 — 1차 스프린트 데모 체크, 미완료 이슈 2차 스프린트로 이월(칸반 갱신)
- [ ] 전체 — 데일리 스크럼 블로커 회고(별도 세션 없이 스크럼에서 간단 정리)

---

## 4. Phase 2 — 2차 스프린트 (08/03 월 ~ 08/07 금) — 기본 기능 완성

### 08/03 (월)
- [ ] 신홍규 — `[FEAT] 프로필 조회/수정 API 구현 (GET, PATCH /api/users/{userId}/profiles, multipart 이미지, location은 FE가 변환해 보낸 값을 그대로 저장)`
- [ ] 김하빈 — `[FEAT] 날씨·프로필 기반 추천 알고리즘 설계 및 1차 구현 착수 (GET /api/recommendations)`
- [ ] 이경신 — `[FEAT] 피드 좋아요/취소 API 구현 (POST, DELETE /api/feeds/{feedId}/like)`

### 08/04 (화)
- [ ] 신홍규 — `[FEAT] 비밀번호 변경/초기화 API 구현 (PATCH /api/users/{userId}/password, POST /api/auth/reset-password)`
- [ ] 신홍규 — `[FEAT] 휴면 계정 배치 구현 (lastLoginAt 추적, 90일 미접속 시 dormant 전환, 전환 7일 전 알림 발행)`
- [ ] 김호현 — `[FEAT] 날씨 급변(강수·기온 급변) 알림 트리거 설계 (사용자 위치 기반)`
- [ ] 김호현 — `[BATCH] 알림 읽음 정리 배치 (읽은 지 7일 지난 알림만 물리 삭제)`
- [ ] 김하빈 — `[FEAT] 추천 알고리즘 1차 구현 (규칙 기반)`
- [ ] 이경신 — `[FEAT] 피드 댓글 조회/등록 API 구현 (GET, POST /api/feeds/{feedId}/comments)`

### 08/05 (수)
- [ ] 김호현 — `[FEAT] 날씨 급변 알림 트리거 구현`
- [ ] 김호현 — `[BATCH] 날씨 데이터 retention 배치 (7일 경과분 정리 — Feed는 스냅샷을 갖고 있어 안전, erd.md 설계 노트 4번 참고)`
- [ ] 김하빈 — `[TEST]` 추천 알고리즘 테스트 보강
- [ ] 이경신 — `[FEAT] 피드 수정/삭제 API 구현 (PATCH, DELETE /api/feeds/{feedId})`

### 08/06 (목)
- [ ] 김호현 — `[REFACTOR]` 날씨 배치·알림 트리거 안정화 2차
- [ ] 이경신 — `[FEAT] DM 목록 조회 API + WebSocket 인프라 구현 (GET /api/direct-messages, SockJS `/ws`, STOMP CONNECT 헤더 Authorization: Bearer)`
- [ ] 이경신 — `[FEAT] DM 송수신 구현 (발행 /pub/direct-messages_send, 구독 /sub/direct-messages_{userId 사전순 쌍})`

### 08/07 (금)
- [ ] 전체 — 기본 기능 요구사항 전체 완성 확인, 테스트 커버리지 중간 점검
- [ ] 전체 — 중간 발표 데모 시나리오 초안 작성 시작

---

## 5. 중간 발표 준비 및 발표 (08/08 토 ~ 08/10 월, 발표 17:00–19:00)

- [ ] 전체 — `[DOCS]` 기본 기능 전체 데모 시나리오 준비 (08/08~09 주말, 도메인별 담당자가 자기 파트 시연)
- [ ] 전체 — `[DOCS]` 심화 기능 진행 계획 발표 자료 정리
- [ ] 08/10 (월) 09:00~17:00 — 최종 리허설/버퍼 (3차 스프린트에 포함하지 않음)
- [ ] 08/10 (월) **17:00–19:00** — 중간 발표

---

## 6. Phase 3 — 3차 스프린트 (08/11 화 ~ 08/14 금) — 성능 테스트 + 심화 착수

> 08/10은 발표일로 스프린트에서 제외. 발표 준비/시각 관련 논의가 필요하면 08/10 낮 리허설 시간에 병행.

### 08/11 (화)
- [ ] 전체 — (오전 최우선) 중간 발표 피드백 반영 우선순위 재조정
- [ ] 신홍규 — `[ADR] OAuth2 로그인 성공 후 SPA(해시 라우터) 토큰 전달 방식 결정` ⚠️ 블로커 — 오전 중 완료 목표(늦어도 소셜 로그인 착수 전)
- [ ] 김호현 — `[TEST]` 1차 성능 테스트 도구 선정 (k6/JMeter 등)
- [ ] 신홍규 — `[FEAT] 소셜 로그인(Google) OAuth2 Client 연동 (/oauth2/authorization/google)` `심화`
- [ ] 김하빈 — `[FEAT] 구매 링크 기반 의상 정보 자동 추출 착수 (GET /api/clothes/extractions, 웹 스크래핑용 Feign Client)` `심화` — 멘토 피드백 #1
- [ ] 이경신 — `[CHORE] Elasticsearch 인프라 연동 및 피드 인덱싱 설계 (content 키워드 + skyStatus/precipitationType 필터 + createdAt/likeCount 정렬)` `심화` — 멘토 피드백 #5
- [ ] 김호현 — `[FEAT] Spring Actuator 커스텀 메트릭 정의`
- [ ] 김호현 — `[CHORE] APM(Pinpoint 또는 Datadog) 도입 — 성능 테스트 전 계측 완료` — 멘토 피드백 #7

### 08/12 (수)
- [ ] 신홍규 — `[FEAT] 소셜 로그인(Kakao) OAuth2 Client 연동 (/oauth2/authorization/kakao)` `심화`
- [ ] 신홍규 — `[FEAT] SocialAccount 엔티티 연동 + UserDto.linkedOAuthProviders 응답 매핑` (User에 배열 컬럼 추가 아님 — social_accounts 조회로 파생, conventions.md §2-1 참고)
- [ ] 김하빈 — `[FEAT] 구매 링크 파싱 로직 구현 (LLM 보조 추출, Feign Client)` `심화`
- [ ] 이경신 — `[FEAT] 피드 검색 API 구현 (keywordLike → ES 쿼리 전환, GET /api/feeds)` `심화` — 멘토 피드백 #5
- [ ] 김호현 — `[TEST]` 1차 성능 테스트 실행 (APM 계측 데이터 활용) 및 결과 정리

### 08/13 (목)
- [ ] 신홍규 — `[TEST]` 소셜 로그인 테스트 보강
- [ ] 김하빈 — `[TEST]` 구매 링크 추출 테스트 보강, 실패 케이스(비정형 페이지) 처리
- [ ] 이경신 — `[TEST]` DM 실시간 안정성 테스트 (재연결/중복 수신)
- [ ] 김호현 — `[REFACTOR]` 성능 테스트 결과 기반 배치 튜닝

### 08/14 (금)
- [ ] 전체 — 3차 스프린트 통합 점검, 4차로 이월할 심화 항목 정리

---

## 7. Phase 4 — 4차 스프린트 (08/17 월 ~ 08/21 금) — 심화 마무리 + 성능 보강

### 08/17 (월)
- [ ] 김하빈 — `[FEAT] LLM API(OpenAI/HuggingFace/OpenRouter) 연동 추천 고도화 착수` `심화`
- [ ] 신홍규 — `[REFACTOR]` 소셜 로그인 마무리, 예외 케이스 처리
- [ ] 이경신 — `[REFACTOR]` ES 검색 정확도/성능 튜닝
- [ ] 김호현 — `[CHORE] Docker Compose 로컬 분산 환경 구성 착수`

### 08/18 (화)
- [ ] 김하빈 — `[FEAT] LLM 기반 추천 결과 후처리 및 응답 통합`
- [ ] 김호현 — `[REFACTOR]` 3차 성능 테스트 결과 기반 병목 구간 개선

### 08/19~20 병행 (선택, 시간 되면)
- [ ] 김하빈 — `[FEAT] (선택) 간단한 LLM 챗봇 착수 — 옷 추천 관련 자연어 질의응답 API + FE 위젯 설계` `심화(선택)` — 멘토 피드백 #8, `otboo-fe`엔 대응 UI가 없어 신규 화면/엔드포인트 설계 필요

### 08/19 (수)
- [ ] 전체 — `[TEST]` 커버리지 80% 달성을 위한 도메인별 보강 (부족한 슬라이스 테스트 우선)

### 08/20 (목)
- [ ] 전체 — `[TEST]` 커버리지 최종 점검, CI 게이트 통과 확인
- [ ] 김호현 — `[CHORE] Docker Compose 로컬 분산 환경 검증`

### 08/21 (금)
- [ ] 전체 — `[DOCS] README 정리 (커버리지 배지 포함)`
- [ ] 전체 — 4차 스프린트 마무리, 5차 스프린트 범위 재확인(리스크 큰 항목 축소 여부 판단)

---

## 8. Phase 5 — 5차 스프린트 (08/24 월 ~ 08/28 금) — 분산 환경 + 통합 테스트

### 08/24 (월)
- [ ] 김호현 — `[DEPLOY] Redis AWS ElastiCache 전환` `심화`
- [ ] 전체 — `[TEST]` 전체 통합 테스트 착수

### 08/25 (화)
- [ ] 김호현 — `[DEPLOY] Kafka 대안(예: Confluent Cloud) 연동` `심화`
- [ ] 전체 — `[FIX]` 통합 테스트 중 발견된 버그 수정

### 08/26 (수)
- [ ] 김호현 — `[DEPLOY] Nginx 리버스 프록시 + ECS 다중 인스턴스 로드밸런싱 구성` `심화`
- [ ] 김호현 — `[FEAT] SSE 알림 Kafka 발행 + Redis 세션/미전송 알림 관리, LastEventId 기반 재연결 복구 및 재시도 배치 구현` `심화` — 멘토 피드백 #4
- [ ] 전체 — `[FIX]` 버그 수정 지속

### 08/27 (목)
- [ ] 전체 — `[TEST] API 스펙(docs/api-docs.json) 최종 일치 검증`
- [ ] 전체 — `[TEST] otboo-fe 최종 연동 확인 (특히 STOMP destination, SSE 이벤트명, OAuth2 리다이렉트)`

### 08/28 (금)
- [ ] 전체 — 5차 스프린트 마무리, 최종 발표 데모 시나리오용 시스템 상태 고정(freeze)

---

## 9. 최종 발표 준비 및 발표 (08/29 토 ~ 08/31 월, 발표 09:00–14:00)

- [ ] 전체 — `[DOCS]` 최종 발표 자료 및 데모 시나리오 준비 (08/29~30 주말)
- [ ] 08/31 (월) **09:00–14:00** — 최종 발표 및 프로젝트 제출 — `[DONE]` 최종 머지

---

## 10. 진행 상황 관리 방법

- 위 체크박스는 초안이며, 실제 착수 시 각 항목을 GitHub Issue로 등록하고 이슈 번호를 항목 옆에 `(#12)` 형태로 채워 넣는다.
- 매일 스크럼(9:00–9:15)에서 전일 완료/금일 예정/블로커를 이 문서 기준으로 짚고, 지연 항목은 다음 날 또는 금요일 정리 시간에 재배치한다.
- 스프린트 종료마다(별도 회고 세션 없이) 미완료 이슈를 다음 스프린트 섹션으로 이동시키고 이 문서를 갱신한다.
- API 스펙이 바뀌면(`docs/api-docs.json` 갱신) 이 문서의 엔드포인트 표기도 함께 업데이트한다.
