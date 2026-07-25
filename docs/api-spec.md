# API 스펙 요약

> `docs/api-docs.json`(OpenAPI 3.1, 41개 엔드포인트)을 사람이 훑기 쉽게 정리한 표입니다. 요청/응답 DTO의 Java `record` 정의는 `docs/dto-spec.md`, 원본 스키마는 이 파일 또는 Swagger UI를 참고하세요. 담당자는 R&R 기준.

## 인증 관리 (5) — 신홍규

| Method | Path | 설명 | 비고 |
| --- | --- | --- | --- |
| POST | `/api/auth/sign-in` | 로그인 | `multipart/form-data` (`username`, `password`) — JSON 아님 |
| POST | `/api/auth/sign-out` | 로그아웃 | |
| POST | `/api/auth/refresh` | 토큰 재발급 | `REFRESH_TOKEN` 쿠키에서 읽음 |
| POST | `/api/auth/reset-password` | 비밀번호 초기화 | 임시 비밀번호 이메일 발송 |
| GET | `/api/auth/csrf-token` | CSRF 토큰 조회 | `XSRF-TOKEN` 쿠키 발급, 204 응답 |

## 프로필 관리 (7) — 신홍규

| Method | Path | 설명 | 비고 |
| --- | --- | --- | --- |
| GET | `/api/users` | 계정 목록 조회(어드민) | 커서 페이지네이션, `emailLike`/`roleEqual`/`locked` 필터 |
| POST | `/api/users` | 회원가입 | JSON, 비밀번호 BCrypt 해싱 |
| PATCH | `/api/users/{userId}/role` | 권한 수정(어드민) | |
| GET | `/api/users/{userId}/profiles` | 프로필 조회 | |
| PATCH | `/api/users/{userId}/profiles` | 프로필 수정 | `multipart/form-data` (`request` + `image`) |
| PATCH | `/api/users/{userId}/password` | 비밀번호 변경 | |
| PATCH | `/api/users/{userId}/lock` | 계정 잠금 상태 변경(어드민) | |

## 의상 관리 (5) — 김하빈

| Method | Path | 설명 | 비고 |
| --- | --- | --- | --- |
| GET | `/api/clothes` | 옷 목록 조회 | `ownerId` 필수, `typeEqual` 필터 |
| POST | `/api/clothes` | 옷 등록 | `multipart/form-data` |
| PATCH | `/api/clothes/{clothesId}` | 옷 수정 | `multipart/form-data` |
| DELETE | `/api/clothes/{clothesId}` | 옷 삭제 | 하드 삭제, 피드 `ootds`에서만 제거(erd.md 참고) |
| GET | `/api/clothes/extractions` | 구매 링크로 옷 정보 불러오기 `심화` | 쿼리 `url` |

## 의상 속성 정의 (4) — 김하빈

| Method | Path | 설명 | 비고 |
| --- | --- | --- | --- |
| GET | `/api/clothes/attribute-defs` | 목록 조회 | 페이지네이션 없음(전체 반환), 정렬만 지원 |
| POST | `/api/clothes/attribute-defs` | 등록(어드민) | |
| PATCH | `/api/clothes/attribute-defs/{definitionId}` | 수정(어드민) | |
| DELETE | `/api/clothes/attribute-defs/{definitionId}` | 삭제(어드민) | 사용 중이면 409 (erd.md 참고) |

## 추천 관리 (1) — 김하빈

| Method | Path | 설명 | 비고 |
| --- | --- | --- | --- |
| GET | `/api/recommendations` | 추천 조회 | 쿼리 `weatherId` 필수, 저장 없이 매번 계산 |

## 피드 관리 (8) — 이경신

| Method | Path | 설명 | 비고 |
| --- | --- | --- | --- |
| GET | `/api/feeds` | 피드 목록 조회 | `keywordLike`(3차 스프린트부터 ES), `skyStatusEqual`/`precipitationTypeEqual`/`authorIdEqual` 필터 |
| POST | `/api/feeds` | 피드 등록 | 날씨는 스냅샷 저장(erd.md 참고) |
| PATCH | `/api/feeds/{feedId}` | 피드 수정 | 내용만 수정 가능 |
| DELETE | `/api/feeds/{feedId}` | 피드 삭제 | 댓글/좋아요 cascade |
| POST | `/api/feeds/{feedId}/like` | 좋아요 | |
| DELETE | `/api/feeds/{feedId}/like` | 좋아요 취소 | |
| GET | `/api/feeds/{feedId}/comments` | 댓글 조회 | |
| POST | `/api/feeds/{feedId}/comments` | 댓글 등록 | ⚠️ 수정/삭제 API 없음 — 사전기간 확인 필요 |

## 팔로우 관리 (5) — 이경신

| Method | Path | 설명 | 비고 |
| --- | --- | --- | --- |
| POST | `/api/follows` | 팔로우 생성 | |
| DELETE | `/api/follows/{followId}` | 팔로우 취소 | |
| GET | `/api/follows/summary` | 팔로우 요약 정보 조회 | 쿼리 `userId` |
| GET | `/api/follows/followings` | 팔로잉 목록 조회 | 쿼리 `followerId` |
| GET | `/api/follows/followers` | 팔로워 목록 조회 | 쿼리 `followeeId` |

## DirectMessage (1 REST + WebSocket) — 이경신

| Method | Path | 설명 | 비고 |
| --- | --- | --- | --- |
| GET | `/api/direct-messages` | DM 목록 조회 | 쿼리 `userId` |
| WS(STOMP) | `/pub/direct-messages_send` | 메시지 발행 | REST 아님, 본문 `{senderId, receiverId, content}` |
| WS(STOMP) | `/sub/direct-messages_{쌍}` | 메시지 구독 | `{작은UUID}_{큰UUID}` 사전순 정렬 |

## 알림 (2) — 김호현

| Method | Path | 설명 | 비고 |
| --- | --- | --- | --- |
| GET | `/api/notifications` | 알림 목록 조회 | |
| DELETE | `/api/notifications/{notificationId}` | 알림 읽음 처리 | 설명상 삭제 아님 — `readAt` 갱신(소프트) |

## SSE (1) — 김호현

| Method | Path | 설명 | 비고 |
| --- | --- | --- | --- |
| GET | `/api/sse` | 알림 구독 | 이벤트명 `notifications` 고정, `LastEventId` 쿼리로 재연결 복구 |

## 날씨 관리 (2) — 김호현

| Method | Path | 설명 | 비고 |
| --- | --- | --- | --- |
| GET | `/api/weathers` | 날씨 정보 조회 | 쿼리 `longitude`/`latitude`, 배열 응답 |
| GET | `/api/weathers/location` | 위치(좌표) 변환 | 위·경도 → 기상청 격자좌표(x,y) + 행정구역명 |

---

## 도메인별 담당·엔드포인트 수 요약

| 담당 | 도메인 | 엔드포인트 수 |
| --- | --- | --- |
| 김호현 | 날씨(2) + 알림(2) + SSE(1) | 5 |
| 김하빈 | 의상(5) + 속성정의(4) + 추천(1) | 10 |
| 신홍규 | 인증(5) + 프로필(7) | 12 |
| 이경신 | 피드(8) + 팔로우(5) + DM(1) | 14 |
| **합계** | | **41** |

## 공통 규칙

- 목록 조회는 커서 페이지네이션(`data`/`nextCursor`/`nextIdAfter`/`hasNext`/`totalCount`/`sortBy`/`sortDirection`) — `conventions.md` §6 참고
- 에러 응답은 `{exceptionName, message, details}` — `conventions.md` §4 참고
- 인증 필요 API는 `Authorization: Bearer {accessToken}` 헤더 필수
- 이미지 업로드가 있는 API는 `multipart/form-data`
