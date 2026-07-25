# DTO 정의서

> `docs/api-docs.json`(41개 엔드포인트) 스키마 + `otboo-fe/src/lib/api/types.ts`(필수/선택 여부 대조)를 기준으로 만든 1차 초안입니다. `record`/검증 규칙은 `docs/conventions.md`를 따릅니다. 실제 구현 시 필드가 늘거나 검증 강도가 바뀔 수 있습니다 — 특히 `@NotBlank`/`@Size` 등 세부 제약은 스펙에 명시되지 않아 합리적으로 추정한 값이니 사전기간에 팀 확인 필요.
>
> 패키지 경로는 `conventions.md` §1의 구조를 따릅니다. Cursor 목록 응답은 도메인별 클래스를 따로 만들지 않고 전부 `CursorPageResponse<T>`(제네릭)를 재사용합니다.

---

## 0. 공통 (`global/dto`, `global/type`)

```java
// global/dto/CursorPageResponse.java
public record CursorPageResponse<T>(
    List<T> data,
    String nextCursor,
    UUID nextIdAfter,
    boolean hasNext,
    long totalCount,
    String sortBy,
    SortDirection sortDirection
) {}

// global/dto/ErrorResponse.java
public record ErrorResponse(
    String exceptionName,
    String message,
    Map<String, Object> details
) {}

// global/type/SortDirection.java
public enum SortDirection { ASCENDING, DESCENDING }
```

### 공유 요약 DTO — `UserSummary` / `AuthorDto`

`docs/api-docs.json`엔 `UserSummary`(팔로우/DM 상대 표기용)와 `AuthorDto`(피드/댓글 작성자 표기용)가 **필드가 완전히 동일**한 별도 스키마로 정의돼 있습니다.

```java
public record UserSummary(
    UUID userId,
    String name,
    String profileImageUrl
) {}
```

> 구현 시 `AuthorDto`를 별도로 만들지 않고 `UserSummary` 하나로 통일하는 걸 권장합니다(사전기간 ADR 후보) — 응답 바디의 JSON 모양은 동일하므로 FE에는 영향 없음.

---

## 1. 인증 (`domain/auth/dto`) — 신홍규

```java
public record SignInRequest(
    @NotBlank String username,   // 이메일
    @NotBlank String password
) {}
// 주의: 컨트롤러는 이 record를 multipart/form-data로 받음 (JSON 아님) — conventions.md §3 참고

public record ResetPasswordRequest(
    @NotBlank @Email String email
) {}

public record JwtDto(
    UserDto userDto,
    String accessToken
) {}
```

---

## 2. 사용자·프로필 (`domain/user/dto`, `domain/user/profile/dto`) — 신홍규

```java
public enum Role { USER, ADMIN }

public record UserCreateRequest(
    @NotBlank @Size(max = 20) String name,
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 64) String password
) {}

public record UserRoleUpdateRequest(
    @NotNull Role role
) {}

public record UserLockUpdateRequest(
    @NotNull Boolean locked
) {}

public record ChangePasswordRequest(
    @NotBlank @Size(min = 8, max = 64) String password
) {}

public record UserListParams(
    String cursor,
    UUID idAfter,
    @NotNull @Min(1) @Max(100) Integer limit,
    @NotNull UserSortBy sortBy,          // email | createdAt
    @NotNull SortDirection sortDirection,
    String emailLike,
    Role roleEqual,
    Boolean locked
) {}

public enum UserSortBy {
    @JsonProperty("email")     EMAIL,
    @JsonProperty("createdAt") CREATED_AT
}

public record UserDto(
    UUID id,
    Instant createdAt,
    String email,
    String name,
    Role role,
    boolean locked
    // linkedOAuthProviders: List<OAuthProvider> — 컬럼으로 저장하지 않고 SocialAccount를 조회해 매핑 시점에 채움
    //   (conventions.md §2-1 "소셜 로그인 계정 연동" 참고, Entity가 아니라 SocialAccountRepository.findProvidersByUserId(id) 등으로 조립)
) {}
```

> `dormant`/`lastLoginAt`(휴면 계정용)은 이 DTO에 넣지 않습니다 — 내부 전용 필드라 Entity에만 존재합니다 (`conventions.md` §2-1 참고).

```java
public enum Gender { MALE, FEMALE, OTHER }

public record ProfileUpdateRequest(
    String name,
    Gender gender,
    LocalDate birthDate,
    WeatherAPILocation location,
    @Min(1) @Max(5) Integer temperatureSensitivity
) {}
// 전부 nullable — 부분 수정(partial update) 요청

public record ProfileDto(
    UUID userId,
    String name,
    Gender gender,
    LocalDate birthDate,
    WeatherAPILocation location,
    Integer temperatureSensitivity,
    String profileImageUrl
) {}
```

---

## 3. 의상 (`domain/clothes/dto`) — 김하빈

```java
public enum ClothesType {
    TOP, BOTTOM, DRESS, OUTER, UNDERWEAR, ACCESSORY, SHOES, SOCKS, HAT, BAG, SCARF, ETC
}

public record ClothesAttributeDto(       // 요청 시 사용 — 값만 전달
    @NotNull UUID definitionId,
    @NotBlank String value
) {}

public record ClothesAttributeWithDefDto( // 응답 시 사용 — 정의 정보까지 포함
    UUID definitionId,
    String definitionName,
    List<String> selectableValues,
    String value
) {}

public record ClothesCreateRequest(
    @NotNull UUID ownerId,
    @NotBlank @Size(max = 100) String name,
    @NotNull ClothesType type,
    List<ClothesAttributeDto> attributes
) {}
// multipart/form-data: "request" 파트(JSON) + "image" 파트(binary, 선택)
// purchaseUrl은 이 요청 DTO에 없음 — 구매링크 자동추출(GET /api/clothes/extractions)이 별도로 채우는 내부 전용 컬럼(conventions.md §2-1, erd.md CLOTHES.purchase_url 참고)

public record ClothesUpdateRequest(
    String name,
    ClothesType type,
    List<ClothesAttributeDto> attributes
) {}
// 전부 nullable — 부분 수정

public record ClothesListParams(
    String cursor,
    UUID idAfter,
    @NotNull @Min(1) @Max(100) Integer limit,
    ClothesType typeEqual,
    @NotNull UUID ownerId
) {}

public record ClothesDto(
    UUID id,
    UUID ownerId,
    String name,
    String imageUrl,
    ClothesType type,
    List<ClothesAttributeWithDefDto> attributes
) {}
```

### 의상 속성 정의 (`domain/clothes/attributedef/dto`) — 김하빈

```java
public record ClothesAttributeDefCreateRequest(
    @NotBlank String name,
    @NotEmpty List<String> selectableValues
) {}

public record ClothesAttributeDefUpdateRequest(
    String name,
    List<String> selectableValues
) {}
// 전부 nullable — 부분 수정

public record ClothesAttributeDefListParams(
    @NotNull AttributeDefSortBy sortBy,   // createdAt | name
    @NotNull SortDirection sortDirection,
    String keywordLike
) {}

public enum AttributeDefSortBy {
    @JsonProperty("createdAt") CREATED_AT,
    @JsonProperty("name")      NAME
}

public record ClothesAttributeDefDto(
    UUID id,
    String name,
    List<String> selectableValues,
    Instant createdAt
) {}
```

> 목록 조회(`GET /api/clothes/attribute-defs`)는 커서 페이지네이션이 아니라 **배열을 그대로 반환**합니다(`CursorPageResponse` 아님) — `docs/api-docs.json` 스키마 기준.

---

## 4. 추천 (`domain/recommendation/dto`) — 김하빈

```java
public record RecommendationParams(
    @NotNull UUID weatherId
) {}

public record RecommendationDto(
    UUID weatherId,
    UUID userId,
    List<OotdDto> clothes
) {}
```

`OotdDto`는 아래 5번(피드) 정의를 그대로 재사용합니다(피드의 "착장"과 추천의 "착장"이 같은 모양).

---

## 5. 피드·댓글 (`domain/feed/dto`) — 이경신

```java
public record OotdDto(
    UUID clothesId,
    String name,
    String imageUrl,
    ClothesType type,
    List<ClothesAttributeWithDefDto> attributes
) {}

public record FeedCreateRequest(
    @NotNull UUID authorId,
    @NotNull UUID weatherId,
    @NotEmpty List<UUID> clothesIds,
    @NotBlank String content
) {}
// authorId는 반드시 인증된 사용자와 일치하는지 서버에서 검증 (conventions.md §3 "요청 바디 authorId 검증" 참고)
// clothesIds는 저장 안 함 — 서버가 각 Clothes를 조회해 OotdDto로 직렬화한 뒤 Feed.ootds(JSONB)에 스냅샷으로 저장 (erd.md 설계 노트 3)

public record FeedUpdateRequest(
    @NotBlank String content
) {}

public enum FeedSortBy {
    @JsonProperty("createdAt")  CREATED_AT,
    @JsonProperty("likeCount")  LIKE_COUNT
}

public record FeedListParams(
    String cursor,
    UUID idAfter,
    @NotNull @Min(1) @Max(100) Integer limit,
    @NotNull FeedSortBy sortBy,
    @NotNull SortDirection sortDirection,
    String keywordLike,                       // 3차 스프린트부터 ES 쿼리 대상
    SkyStatus skyStatusEqual,
    PrecipitationType precipitationTypeEqual,
    UUID authorIdEqual
) {}

public record FeedDto(
    UUID id,
    Instant createdAt,
    Instant updatedAt,
    AuthorDto author,             // 3번 "공유 요약 DTO" 참고 — UserSummary로 통일 검토
    WeatherSummaryDto weather,    // Feed.weather_id에서 조회 — FK로 걸지는 미결정 (erd.md 설계 노트 4번)
    List<OotdDto> ootds,          // Feed.ootds(JSONB)를 역직렬화 — Clothes 삭제와 무관 (erd.md 설계 노트 3번)
    String content,
    long likeCount,                // Feed.like_count 비정규화 카운터 그대로 (erd.md 설계 노트 11번)
    int commentCount,               // Feed.comment_count 비정규화 카운터 그대로
    boolean likedByMe
) {}

public record FeedCommentParams(
    @NotNull UUID feedId,
    String cursor,
    UUID idAfter,
    @NotNull @Min(1) @Max(100) Integer limit
) {}

public record CommentCreateRequest(
    @NotNull UUID feedId,
    @NotNull UUID authorId,
    @NotBlank String content
) {}
// authorId 서버 검증 필수 (위와 동일 이유). 수정/삭제 요청 DTO는 없음 — 스펙에 해당 API 자체가 없기 때문 (사전기간 확인 대상)

public record CommentDto(
    UUID id,
    Instant createdAt,
    UUID feedId,
    AuthorDto author,
    String content
) {}
```

---

## 6. 팔로우 (`domain/follow/dto`) — 이경신

```java
public record FollowCreateRequest(
    @NotNull UUID followeeId,
    @NotNull UUID followerId
) {}
// followerId 서버 검증 필수

public record FollowDto(
    UUID id,
    UserSummary followee,
    UserSummary follower
) {}

public record FollowSummaryDto(
    UUID followeeId,
    long followerCount,
    long followingCount,
    boolean followedByMe,
    UUID followedByMeId,     // 내가 팔로우 중이 아니면 null
    boolean followingMe
) {}

public record FollowingListParam(
    @NotNull UUID followerId,
    String cursor,
    UUID idAfter,
    @NotNull @Min(1) @Max(100) Integer limit,
    String nameLike
) {}

public record FollowerListParam(
    @NotNull UUID followeeId,
    String cursor,
    UUID idAfter,
    @NotNull @Min(1) @Max(100) Integer limit,
    String nameLike
) {}
```

---

## 7. DirectMessage (`domain/directmessage/dto`) — 이경신

```java
public record DirectMessageParams(
    @NotNull UUID userId,
    String cursor,
    UUID idAfter,
    @NotNull @Min(1) @Max(100) Integer limit
) {}

public record DirectMessageDto(
    UUID id,
    Instant createdAt,
    UserSummary sender,
    UserSummary receiver,
    String content
) {}

// REST 스펙엔 없음 — STOMP @MessageMapping("/direct-messages_send")의 페이로드
public record DirectMessageSendRequest(
    @NotNull UUID senderId,
    @NotNull UUID receiverId,
    @NotBlank @Size(max = 100) String content
) {}
// senderId 서버 검증 필수(WebSocket 인증 컨텍스트의 사용자와 일치해야 함)
```

---

## 8. 알림 (`domain/notification/dto`) — 김호현

```java
public enum NotificationLevel { INFO, WARNING, ERROR }

public record NotificationListParams(
    String cursor,
    UUID idAfter,
    @NotNull @Min(1) @Max(100) Integer limit
) {}

public record NotificationDto(
    UUID id,
    Instant createdAt,
    UUID receiverId,
    String title,
    String content,
    NotificationLevel level
) {}
```

> `readAt`은 이 DTO에 없습니다 — FE가 안 쓰는 내부 필드라 응답에 노출하지 않고 Entity에만 둡니다(`conventions.md` §2-1 참고).

---

## 9. 날씨 (`domain/weather/dto`) — 김호현

```java
public enum SkyStatus { CLEAR, MOSTLY_CLOUDY, CLOUDY }
public enum PrecipitationType { NONE, RAIN, RAIN_SNOW, SNOW, SHOWER }
public enum WindStrength { WEAK, MODERATE, STRONG }

public record WeatherAPILocation(
    double latitude,
    double longitude,
    int x,
    int y,
    List<String> locationNames
) {}

public record WeatherParams(
    @NotNull Double longitude,
    @NotNull Double latitude
) {}

public record PrecipitationDto(
    PrecipitationType type,
    double amount,
    double probability
) {}

public record HumidityDto(
    double current,
    double comparedToDayBefore
) {}

public record TemperatureDto(
    double current,
    double comparedToDayBefore,
    double min,
    double max
) {}

public record WindSpeedDto(
    double speed,
    WindStrength asWord
) {}

public record WeatherSummaryDto(       // Feed에 스냅샷으로 저장되는 축소 버전
    UUID weatherId,
    SkyStatus skyStatus,
    PrecipitationDto precipitation,
    TemperatureDto temperature
) {}

public record WeatherDto(              // GET /api/weathers 응답 (배열)
    UUID id,
    Instant forecastedAt,
    Instant forecastAt,
    WeatherAPILocation location,
    SkyStatus skyStatus,
    PrecipitationDto precipitation,
    HumidityDto humidity,
    TemperatureDto temperature,
    WindSpeedDto windSpeed
) {}
```

---

## 10. SSE 페이로드 (`global/sse`) — 김호현

REST 스펙엔 스키마가 없지만(`SseEmitter`는 타임아웃 설정용), 실제 이벤트 페이로드는 `NotificationDto`를 그대로 보냅니다.

```java
sseEmitter.send(SseEmitter.event()
    .id(eventId)
    .name("notifications")   // 고정 — conventions.md §12
    .data(notificationDto)); // NotificationDto 그대로
```

---

## 검증 규칙 관련 미확정 사항 (사전기간 확인)

- 비밀번호 최소 길이(`@Size(min = 8)`로 가정) — 실제 정책 확정 필요
- `keywordLike`/`nameLike` 등 검색어 최대 길이 제한 여부
- `ClothesAttributeDto.value`가 `ClothesAttributeDefDto.selectableValues`에 포함된 값인지 검증할지(서버 측 enum 검증 여부)
- 커서 페이지네이션 `limit`의 기본값/최대값(위 예시는 1~100으로 가정)
- `name`/`content` 등 `@Size(max=...)`는 DDL 기준(사용자명 20자, 의상명 100자, DM 100자)으로 반영했지만, 나머지 필드(예: `ClothesAttributeDefDto.name`, `NotificationDto.title`/`content`)는 DDL에 길이가 있어도 응답 전용 DTO라 검증 대상이 아님 — 실제 서버에서 생성할 때 DDL 길이(`title` 50자, `content` 100자)를 넘지 않는지만 내부적으로 체크

> `Feed.weather_id` FK 여부, `Notification.read_at` 존재 여부가 아직 미결정이라(`erd.md` 설계 노트 4, 6), 위 `FeedDto.weather`/`NotificationDto`의 최종 필드 구성이 바뀔 수 있습니다.
