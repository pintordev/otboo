# Otboo (옷장을 부탁해) [![codecov](https://codecov.io/gh/sb11-code-rangers/sb11-otboo-team4/graph/badge.svg)](https://codecov.io/gh/sb11-code-rangers/sb11-otboo-team4)

> 날씨·취향을 고려해 사용자가 보유한 의상 조합을 추천해주고,<br>
> OOTD 피드·팔로우·DM 등의 소셜 기능을 갖춘 개인화 의상 추천 SaaS

> [!NOTE]
> **Live**: https://otboo.cc · **Swagger**: https://otboo.cc/swagger-ui/index.html<br>
> **자료**: [발표자료](https://link.otboo.cc/deck) · [시연 영상](https://link.otboo.cc/demo) · [팀문서](https://link.otboo.cc/docs)<br>
> 배포 데모는 2026-09-30까지 운영됩니다.

## Table of Contents

- [Contributors](#contributors)
- [Built With](#built-with)
- [Getting Started](#getting-started)
- [Features](#features)
- [Technical Challenges](#technical-challenges)
- [Wiki](#wiki)

## Contributors

| [<img src="https://github.com/pintordev.png" width="100">](https://github.com/pintordev)<br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[김호현](https://github.com/pintordev) 🚩 | [<img src="https://github.com/habin08090.png" width="100">](https://github.com/habin08090)<br>[김하빈](https://github.com/habin08090) | [<img src="https://github.com/zrp0x0.png" width="100">](https://github.com/zrp0x0)<br>[신홍규](https://github.com/zrp0x0) | [<img src="https://github.com/Ksinny.png" width="100">](https://github.com/Ksinny)<br>[이경신](https://github.com/Ksinny) |
| :---: | :---: | :---: | :---: |
| 인프라·공통<br>날씨·알림 | 의상·추천·챗봇 | 사용자·인증 | 소셜·검색 |

## Built With

**Backend**

![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring%20Security-JWT-6DB33F?logo=springsecurity&logoColor=white)
![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-QueryDSL-6DB33F?logo=hibernate&logoColor=white)<br>
![Spring Batch](https://img.shields.io/badge/Spring%20Batch-6DB33F?logo=spring&logoColor=white)
![WebSocket](https://img.shields.io/badge/WebSocket-STOMP-6DB33F?logo=spring&logoColor=white)
![OpenFeign](https://img.shields.io/badge/OpenFeign-6DB33F?logo=spring&logoColor=white)

**Database & Infra**

![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?logo=redis&logoColor=white)
![Kafka](https://img.shields.io/badge/Kafka-231F20?logo=apachekafka&logoColor=white)
![Elasticsearch](https://img.shields.io/badge/Elasticsearch-005571?logo=elasticsearch&logoColor=white)
![Flyway](https://img.shields.io/badge/Flyway-CC0200?logo=flyway&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white)
![AWS](https://img.shields.io/badge/AWS-FF9900)
![Nginx](https://img.shields.io/badge/Nginx-009639?logo=nginx&logoColor=white)

**Collaboration**

![Git](https://img.shields.io/badge/Git-F05032?logo=git&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-2088FF?logo=githubactions&logoColor=white)
![Discord](https://img.shields.io/badge/Discord-5865F2?logo=discord&logoColor=white)
![Notion](https://img.shields.io/badge/Notion-000000?logo=notion&logoColor=white)

## Getting Started

> 직접 로컬에서 실행해보고 싶은 경우의 참고용입니다. 실제 배포는 GitHub Actions → AWS.

```bash
git clone https://github.com/sb11-code-rangers/sb11-otboo-team4.git
cd sb11-otboo-team4
docker compose up -d --build # postgres·redis·kafka·es·nginx + 2 app instances
```

- 정적 리소스: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- 앱 시크릿 설정(OAuth 키, 외부 API 등)은 저장소에 포함되지 않습니다.
- 상세 설정/아키텍처는 [Wiki](#wiki) 참고

## Features

<table>
<thead><tr><th nowrap width="110" align="center">도메인</th><th>핵심 기능</th></tr></thead>
<tbody>
<tr><td nowrap align="center">사용자·인증</td><td>회원가입/로그인(JWT), 소셜 로그인(Google/Kakao), 계정 잠금, 어드민 권한 관리, 비밀번호 재설정(Redis 토큰)</td></tr>
<tr><td nowrap align="center">의상·추천</td><td>의상 CRUD, 속성 정의(어드민), 구매 링크 기반 자동 정보 추출(LLM), 날씨·프로필 기반 의상 추천, LLM 챗봇</td></tr>
<tr><td nowrap align="center">소셜</td><td>OOTD 피드, 좋아요/댓글, 팔로우, DM(WebSocket 실시간 채팅), Elasticsearch 피드 검색</td></tr>
<tr><td nowrap align="center">날씨·알림</td><td>기상청 단기예보 연동, 날씨 급변 알림, SSE 실시간 알림(Redis Pub/Sub), Kafka 이벤트 릴레이</td></tr>
<tr><td nowrap align="center">인프라</td><td>Docker Compose 분산 환경, 다중 인스턴스 + Nginx(LB), GitHub Actions CI/CD, Spring Batch, ShedLock 분산 락, Actuator 커스텀 메트릭</td></tr>
</tbody>
</table>

## Technical Challenges

> 기능 구현에서 멈추지 않고 유실·장애·확장까지 파고든 지점. 상세는 [Wiki](#wiki)에서 확인 가능합니다.

<details>
<summary><h3>인프라·공통 + 날씨·알림 — 김호현</h3></summary>

#### SSE 알림 다중 인스턴스 전파 — Redis Pub/Sub

- **문제** — 단일 인스턴스 전제의 in-memory SSE emitter·재생 버퍼는 인스턴스가 2대 이상이면 다른 인스턴스가 처리한 알림을 받지 못함
- **선택지** — ① 스티키 세션으로 사용자를 한 인스턴스에 고정 ② DB 폴링으로 이벤트 공유 ③ Redis Pub/Sub 브로드캐스트
- **채택** — ③. 재생 버퍼는 Redis ZSet+String, 발행/구독은 Redis Pub/Sub(`sse:notifications`). 저장~로컬 전송 사이 재연결 대비 연결 시점 스냅샷 비교로 중복 전송 차단
- **효과** — 전 인스턴스 전파 확보(app-1/app-2 로컬 검증). 이후 유실·중복·역압(`seq` 없는 메시지 NPE, 리스너 큐 포화 시 조용한 유실)도 순차 보완
- **링크** — [#153](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/153) · [#214](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/214) · [#277](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/277)

#### 알림 이벤트 유실 방지 — Kafka Transactional Outbox

- **문제** — 트랜잭션 커밋 후 바로 `kafkaTemplate.send()`를 호출하면 발행 실패 시 재시도할 대상이 남지 않음
- **선택지** — ① 커밋 후 직접 send + 실패 로깅 ② `@TransactionalEventListener(AFTER_COMMIT)` + 애플리케이션 재시도 ③ Transactional Outbox + 별도 릴레이
- **채택** — ③. 리스너는 원본 트랜잭션 안(`BEFORE_COMMIT`)에서 outbox 행만 저장, ShedLock 스케줄러가 폴링해 Kafka로 발행
- **효과** — 저장 유실·중복 발행·중복 소비·처리 실패를 4개 장치가 각각 차단 (Outbox·트랜잭션 결합 / `@SchedulerLock` 분산 락 / `UNIQUE(event_id, receiver_id)` 멱등 / 재시도 2회 + DLT)
- **링크** — [#256](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/256)

#### 무중단 배포 중 장기 연결 유지 — Nginx 동적 upstream watcher

- **문제** — ECS 롤링 배포마다 태스크 IP가 바뀌는데 Nginx upstream은 기동 시점 값으로 고정됨
- **선택지** — ① 배포마다 Nginx 컨테이너 재시작 ② Nginx Plus 동적 resolve(유료) ③ Cloud Map 조회 + 변경 시에만 reload하는 watcher 스크립트
- **채택** — ③. Cloud Map을 15초 주기 조회 → IP 목록 정렬 비교 → 실제 변경 시에만 upstream 블록 덮어쓰고 reload
- **효과** — 배포 중에도 SSE·WebSocket 장기 연결 유지. 정렬 없는 비교로 매번 reload되던 문제, `mv` 교체 시 Nginx가 옛 inode를 읽던 함정도 해결
- **링크** — [#242](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/242)

</details>

<details>
<summary><h3>의상·추천·챗봇 — 김하빈</h3></summary>

#### 구매 링크 정보 추출 — OG 태그 우선, LLM 폴백

- **문제** — "LLM 기반 추출" 요구였지만 LLM 호출은 느리고 비용·실패 위험이 있음
- **선택지** — ① 전량 LLM 파싱 ② 쇼핑몰별 스크래핑 규칙 하드코딩 ③ OG 메타태그 우선 + 실패 시에만 LLM
- **채택** — ③. jsoup로 OG 먼저 파싱(정확도↑·비용 0), 못 얻을 때만 본문을 LLM에 전달. https + 도메인 화이트리스트로 SSRF 차단
- **효과** — 대부분 요청이 LLM 없이 처리. 여기서 만든 `LlmClient`·인증·요청 형식을 추천/챗봇이 재사용
- **링크** — [#152](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/152) · [#241](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/241)

#### 규칙 · LLM · 조합기 3단 추천

- **문제** — 추천을 전부 LLM에 맡기면 없는 옷을 지어내거나 종류를 빠뜨리고, 호출 실패 시 추천 자체가 불가
- **선택지** — ① 순수 규칙 기반 ② 순수 LLM ③ 규칙으로 뼈대 + LLM은 판단 영역만 + 조합기로 마감
- **채택** — ③. 규칙(착용 종류 결정) → LLM(색·소재·분위기만) → 조합기(후보에서 최종 한 벌, 매 요청). LLM 이상 시 규칙 롤백, 누락 종류는 코드가 보유 의상으로 채움, 호출 실패해도 200 응답
- **효과** — LLM 장애가 추천 실패로 이어지지 않음. 폴백은 API 키를 의도적으로 무효화해 검증
- **링크** — [#97](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/97) · [#215](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/215)

#### 캐시 경계를 한 칸 앞으로

- **문제** — 최종 결과까지 캐싱하면 "다른 옷 추천"(재요청)이 캐시에 막히고, 캐싱을 안 하면 매 요청 LLM 호출로 느림
- **선택지** — ① 캐싱 안 함 ② 최종 조합 결과까지 캐싱 ③ LLM 후보까지만 캐싱하고 조합기는 매 요청 실행
- **채택** — ③. 입력값 전체(날씨+옷장+민감도)를 ID 순 정렬 후 SHA-256 해시 → 캐시 키, Redis TTL 3시간. 폴백 결과는 미저장
- **효과** — 캐시 적중 시 3.9초 → 0.02초(약 175배). 옷 한 벌만 추가돼도 키가 바뀌어 자동 미스, 일시 장애가 3시간 고착되지 않음
- **링크** — [#264](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/264) · [#301](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/301)

</details>

<details>
<summary><h3>사용자·인증 — 신홍규</h3></summary>

#### JWT + Redis 세션 레지스트리

- **문제** — 로그아웃된 토큰, 기기 수 초과로 밀려난 세션, 권한이 바뀐 사용자의 옛 토큰은 서명 검증만으로 구분 불가
- **선택지** — ① 순수 stateless JWT(짧은 만료로 완화) ② DB 세션 테이블 ③ Redis 세션 레지스트리 + Lua 원자 연산
- **채택** — ③. `UserSessionRegistry`(HASH `refreshJti`·`issuedAt` + ZSET 인덱스), 두 키 모두 `{userId}` 해시 태그로 Redis Cluster 동일 슬롯 배치. 발급·전체 교체·기기 초과 회수·개별/전체 회수 5개 연산을 Lua로
- **효과** — 서명이 유효해도 세션이 없으면 거부. read-modify-write 경쟁 차단
- **링크** — [#94](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/94) · [#194](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/194)

#### Refresh Token Rotation — 재사용 탐지 시 양측 로그아웃

- **문제** — refresh token 유출 시 공격자와 정상 사용자를 서버가 구분할 수 없음
- **선택지** — ① 회전 없이 장기 refresh token ② 회전만 하고 옛 토큰은 거부 ③ 회전 + 옛 토큰 사용 감지 시 사용자 전체 세션 회수
- **채택** — ③. 토큰은 1회 사용 후 즉시 교체, jti 불일치 요청이 오면 해당 요청만이 아니라 전체 세션 회수. 비교+교체는 `compare-and-rotate.lua`로 원자 처리
- **효과** — 유출 토큰 재사용 자동 차단. 동시 재발급 2건이 서로의 쓰기를 덮어써 정상 토큰이 오판되던 경합 제거
- **링크** — [#34](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/34) · [#194](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/194)

#### 소셜 로그인의 세 가지 방어

- **문제** — 소셜 연동 과정에 계정 탈취·세션 전환·동시성 취약점이 잠재
- **채택** — (1) 이미 다른 사용자에 묶인 계정 연동 거부 (2) 이메일 병합 시 기존 비밀번호 무작위화 + 전 세션 회수 (3) 동시 연동은 DB 유니크 제약 위반을 예외로 변환. 인가 요청 상태는 서버 세션이 아니라 쿠키에 저장
- **효과** — 로그인 세션 무단 전환, 선점 가입을 통한 탈취, 리다이렉트가 다른 인스턴스로 가는 경우까지 방어
- **링크** — [#125](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/125) · [#251](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/251) · [#269](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/269) · [#304](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/304)

</details>

<details>
<summary><h3>소셜·검색 — 이경신</h3></summary>

#### Elasticsearch 검색 전환과 튜닝

- **문제** — `LIKE` 검색은 형태소 분석·정확도 조정이 불가
- **선택지** — ① `LIKE` 유지 ② PostgreSQL 전문검색(`tsvector`) ③ Elasticsearch + Nori
- **채택** — ③. `LIKE` → `match` + Nori 색인. 본문·착장 이름을 `copy_to`로 `searchText` 단일 필드에 모아 교차 매칭 해결. `minimum_should_match`를 토큰 ≤2개 100% / ≥3개 75%로 실측 조정
- **효과** — `"민트색"`→`["민트","색"]` 분해로 `"색"`만 맞아도 매칭되던 문제 해결(`_analyze`로 원인이 `match` 기본 OR임을 규명), 정밀도 손실 없이 긴 검색어 recall 회복
- **링크** — [#187](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/187) · [#235](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/235)

#### alias 무중단 인덱스 마이그레이션

- **문제** — 색인 매핑 변경 시 재색인 동안 검색이 끊기거나 구버전 데이터가 노출됨
- **선택지** — ① 다운타임 잡고 재색인 ② `_reindex` API ③ 새 인덱스 + DB 재색인 + alias 원자 전환
- **채택** — ③. `_reindex`는 `_source`만 옮겨 `copy_to` 기반 `searchText`가 비므로 미사용. 새 인덱스 생성 → DB에서 재색인 → refresh → alias `remove+add` 원자 전환 → 이전 세대 1개 보존
- **효과** — 무중단 전환. 주 1회 자동 재색인(대상=alias)과 관리자 트리거 마이그레이션(대상=새 인덱스)을 목적별로 분리
- **링크** — [#220](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/220) · [#270](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/270) · [#273](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/273) · [#293](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/293)

#### DM 대화방 단일 키 — `conversation_id`로 Sort 제거

- **문제** — 발신/수신 양방향 OR 조건이 `BitmapOr`로 합쳐져, 인덱스에 정렬이 있어도 매 페이지 quicksort가 붙음
- **선택지** — ① OR 쿼리 + 정렬 유지 ② 대화방을 별도 엔티티로 승격 ③ 두 UUID를 사전순 결합한 `conversation_id` 단일 등치 조건
- **채택** — ③. Index Scan + Sort 제거. 엔티티 승격은 보류 — 두 사용자로부터 결정론적 복원 가능하고, 그룹 DM 요구가 실제 생기면 그때가 맞다고 판단
- **효과** — 페이지네이션에서 정렬 연산 제거
- **링크** — [#212](https://github.com/sb11-code-rangers/sb11-otboo-team4/pull/212)

</details>

## Wiki

> 프로젝트 상세 문서는 [GitHub Wiki](https://github.com/sb11-code-rangers/sb11-otboo-team4/wiki)에 정리되어 있습니다.

| 문서 | 내용                                                                                     |
| :---: |------------------------------------------------------------------------------------------|
| [Home](https://github.com/sb11-code-rangers/sb11-otboo-team4/wiki) | 위키 인덱스                                                                              |
| [Architecture](https://github.com/sb11-code-rangers/sb11-otboo-team4/wiki/Architecture) | 시스템 구성도, 컴포넌트 관계, 배포 토폴로지, 요청·이벤트 데이터 흐름                     |
| [Tech Stack](https://github.com/sb11-code-rangers/sb11-otboo-team4/wiki/Tech-Stack) | 언어·프레임워크·인프라 선정 이유와 대안 비교                                             |
| [ADR](https://github.com/sb11-code-rangers/sb11-otboo-team4/wiki/ADR) | 주요 설계 결정 기록                                                                      |
| [API](https://github.com/sb11-code-rangers/sb11-otboo-team4/wiki/API) | 도메인별 엔드포인트 명세, 요청/응답 스키마, 인증 흐름, 공통 에러 코드                    |
| [ERD](https://github.com/sb11-code-rangers/sb11-otboo-team4/wiki/ERD) | 테이블 스키마, 엔티티 관계, 인덱스 설계와 근거                                           |
| [Convention](https://github.com/sb11-code-rangers/sb11-otboo-team4/wiki/Convention) | 코드 스타일, 패키지 구조, 커밋·브랜치·PR 규칙, 예외·응답 처리 규약                       |
| [Workflow](https://github.com/sb11-code-rangers/sb11-otboo-team4/wiki/Workflow) | 브랜치 전략, PR·코드리뷰 프로세스, 협업 자동화                                           |
| [Troubleshooting](https://github.com/sb11-code-rangers/sb11-otboo-team4/wiki/Troubleshooting) | 팀원별 담당 영역에서 마주친 이슈와 해결 과정, 공통 장애 대응                               |
| [Guide](https://github.com/sb11-code-rangers/sb11-otboo-team4/wiki/Guide) | 검색 엔진, 통합 테스트, 동적 쿼리, 테스트 데이터, 파일 스토리지 등 개발 환경 설정 가이드 |

<div align="right">

<sub>**License** [MIT](https://github.com/sb11-code-rangers/sb11-otboo-team4/blob/main/LICENSE) · **Team** [Code Rangers](https://github.com/sb11-code-rangers) · 2026</sub>

</div>
