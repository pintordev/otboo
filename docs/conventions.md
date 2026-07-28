# 옷장을 부탁해(Otboo) 코딩 컨벤션

> **기준**: [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) 준수. 아래 규칙은 otboo 도메인·JWT 인증·`docs/api-docs.json` 실제 계약을 기준으로 작성했습니다.
> 이 문서는 **코드 레벨** 컨벤션의 단일 기준입니다. 브랜치/커밋/PR/라벨/ADR 등 **협업 프로세스**는 `프로젝트 수행 계획서 (초안).md`의 [🧩 규칙 수립]이 기준이며, 여기서는 요약만 다룹니다(11번 참고). 41개 엔드포인트 전체 DTO 정의는 `docs/dto-spec.md`를 참고하세요 — 여기 나오는 예시(`ClothesCreateRequest` 등)와 동일한 정의입니다.

### 적용 방법

**IntelliJ IDEA**

1. [intellij-java-google-style.xml](https://github.com/google/styleguide/blob/gh-pages/intellij-java-google-style.xml) 다운로드
2. `Settings > Editor > Code Style > Java > ⚙️ > Import Scheme > IntelliJ IDEA code style XML`
3. `Scheme: GoogleStyle > ⚙️ > Copy to Project > Apply` — `.idea/`에 저장되어 팀 전체 공유
4. PR 생성 전 `Reformat Code` (`Cmd+Alt+L` / `Ctrl+Alt+L`) 실행 + CodeRabbit 1차 리뷰 반영

---

## 0. Git 워크플로우 (Fork 기반)

**Fork 기반**입니다 — `origin`은 각자의 개인 fork, `upstream`은 팀 레포(`sb11-code-rangers/sb11-otboo-team4`)입니다. 팀원은 각자 GitHub에서 팀 레포를 fork한 뒤 로컬에 `origin`(내 fork)/`upstream`(팀 레포) 두 리모트를 등록하고 시작합니다.

### 워크플로우 (10단계)

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

브랜치/커밋/PR 세부 규칙은 13번(Git 컨벤션)을 참고합니다.

---

## 1. 패키지 & 클래스 구조

### 전체 구조

대분류는 `domain` / `global` / `external` 세 가지입니다(사전기간 ADR로 확정).

```
com.sprint.mission.otboo/
├── global/
│   ├── config/
│   │   ├── JpaConfig.java               # @EnableJpaAuditing
│   │   ├── QuerydslConfig.java          # JPAQueryFactory bean
│   │   ├── SecurityConfig.java          # Spring Security + JWT 필터 체인, CSRF 정책
│   │   ├── SwaggerConfig.java           # springdoc-openapi
│   │   ├── FeignConfig.java             # Feign Client 공통 설정(타임아웃/로깅/에러 디코더)
│   │   └── WebSocketConfig.java         # STOMP 엔드포인트(/ws) 등록
│   ├── dto/
│   │   ├── CursorPageResponse.java      # 커서 페이지네이션 공통 응답 (6번 참고)
│   │   └── ErrorResponse.java           # {exceptionName, message, details} — 4번 참고
│   ├── entity/
│   │   └── SoftDeletable.java           # @Embeddable deletedAt + delete() (필요한 엔티티만 컴포지션)
│   ├── exception/
│   │   ├── OtbooException.java          # 추상 기본 예외, status를 직접 보유
│   │   └── GlobalExceptionHandler.java  # @RestControllerAdvice, 공통 케이스만 처리
│   ├── security/
│   │   ├── JwtTokenProvider.java        # 토큰 발급/검증
│   │   └── JwtAuthenticationFilter.java # Authorization: Bearer 파싱
│   └── sse/
│       └── SseEmitterRepository.java    # 사용자별 SseEmitter 관리 (LastEventId 재연결 포함)
├── domain/                              # 대분류는 GitHub 도메인 라벨(auth-user/clothes-recommend/weather-notification/social) 기준
│   ├── authuser/                        # 라벨: auth-user
│   │   ├── user/                        # 회원가입/로그인/권한/계정잠금 — 인증 관리 태그
│   │   └── profile/                     # 프로필(위치·온도민감도 등) — user와 1:1, 프로필 관리 태그
│   ├── clothesrecommend/                # 라벨: clothes-recommend
│   │   ├── clothes/                     # 의상 관리 태그
│   │   ├── attributedef/                # 의상 속성 정의(어드민) 태그
│   │   └── recommendation/              # 추천 알고리즘, (선택) LLM 챗봇 태그
│   ├── weathernotification/             # 라벨: weather-notification
│   │   ├── weather/                     # 날씨 조회, 위치(좌표) 변환, 배치 태그
│   │   └── notification/                # 알림 태그, event/ — NotificationEventListener (SSE + Kafka 발행)
│   └── social/                          # 라벨: social
│       ├── feed/                        # OOTD 피드, 댓글, 좋아요 태그 (Comment는 별도 태그가 없어 feed/ 안에 포함)
│       ├── follow/                      # 팔로우 관리 태그
│       └── directmessage/               # DirectMessage 태그, event/ — DirectMessageSentEvent
└── external/
    ├── kma/                             # 기상청 단기예보 Open API
    │   ├── KmaWeatherClient.java        # Feign
    │   └── dto/
    ├── kakao/                           # Kakao 로컬 API(좌표→행정구역), 소셜 로그인은 Spring Security OAuth2 Client가 처리하므로 별도 Feign 불필요
    │   ├── KakaoLocalClient.java        # Feign
    │   └── dto/
    ├── purchase/                        # 구매 링크 기반 의상 정보 추출 `심화`
    │   ├── PurchasePageClient.java      # Feign (범용 크롤링 대상 도메인마다 분리 검토)
    │   └── dto/
    └── llm/                             # LLM 추천 고도화 / 챗봇 `심화`
        ├── LlmClient.java               # Feign
        └── dto/
```

> `domain/` 하위 각 유닛(user, profile, clothes, attributedef, recommendation, weather, notification, feed, follow, directmessage)은 `docs/api-docs.json`에 자기 API 태그가 별도로 있는 단위 기준입니다 — 태그가 없는 것(Comment 등)은 태그를 공유하는 유닛 안에 포함됩니다. 각 유닛은 아래 [도메인 구조](#도메인-구조)와 동일한 전 레이어(controller/dto/entity/exception/mapper/repository/service)를 갖고, `event/`는 REST 엔드포인트가 없는 내부 이벤트 발행/구독 클래스에만 예외적으로 사용합니다.

### 테스트 구조

main 패키지 구조를 그대로 미러링합니다. 테스트 유형별 어노테이션은 위치로 구분합니다.

```
src/test/java/com/sprint/mission/otboo/
├── global/
│   └── exception/
│       └── GlobalExceptionHandlerTest.java
├── domain/
│   ├── authuser/
│   │   └── user/
│   │       ├── controller/
│   │       │   └── UserControllerTest.java   # @WebMvcTest
│   │       ├── repository/
│   │       │   └── UserRepositoryTest.java   # @DataJpaTest (Testcontainers PostgreSQL)
│   │       └── service/
│   │           └── UserServiceTest.java      # @ExtendWith(MockitoExtension.class)
│   └── ...
└── external/
    └── kma/
        └── KmaWeatherClientTest.java          # 실제 API 호출 기반 검증 테스트 (9번 참고)
```

| 위치 | 어노테이션 | 특징 |
|------|-----------|------|
| `service/` | `@ExtendWith(MockitoExtension.class)` | Mock 의존성, 빠름 |
| `repository/` | `@DataJpaTest` | 실제 DB(Testcontainers PostgreSQL), JPA 레이어만 로드 |
| `controller/` | `@WebMvcTest` | MockMvc, 서비스는 `@MockBean` |
| `external/*Client` | 실제 API 호출 검증 테스트 | 기상청/Kakao/LLM 응답 스펙 변경 조기 감지 |

### 도메인 구조

각 도메인은 독립적인 패키지 안에서 Controller → Service → Repository 레이어로 구성합니다. DTO는 `dto/` 하위 패키지로 분리하고, 요청/응답을 명확히 구분합니다. Controller에서 요청 DTO는 반드시 `@Valid`를 붙입니다.

```
domain/
  clothesrecommend/
    clothes/
      controller/
        api/
          ClothesApi.java           # Swagger 인터페이스 (@Tag, @Operation)
          examples/
            ClothesExamples.java    # @ExampleObject에 쓰는 예시 JSON 상수 모음
        ClothesController.java      # implements ClothesApi
      dto/
        ClothesCreateRequest.java
        ClothesUpdateRequest.java
        ClothesListParams.java      # 목록 조회 조건 (커서 + 필터)
        ClothesDto.java
      entity/
        Clothes.java                 # JPA Entity
        ClothesAttribute.java        # Clothes-AttributeDef 값 매핑
      exception/
        ClothesException.java        # 추상 중간 예외
        ClothesNotFoundException.java
      mapper/
        ClothesMapper.java
      repository/
        querydsl/
          impl/ClothesCustomRepositoryImpl.java
          ClothesCustomRepository.java
        ClothesRepository.java       # extends JpaRepository + ClothesCustomRepository
      service/
        ClothesService.java
```

Swagger 어노테이션(`@Tag`, `@Operation`, `@ApiResponses`)은 `controller/api/*Api` 인터페이스에만 작성합니다. `@Operation` summary/description과 `@ApiResponse` responseCode·description은 `docs/api-docs.json`의 해당 엔드포인트 기준으로 정확히 일치시킵니다(자유 변경 금지 — FE가 이 스펙 그대로 구현돼 있음).

- DTO는 `record`를 사용합니다.

```java
public record ClothesCreateRequest(
    @NotNull UUID ownerId,
    @NotBlank String name,
    @NotNull ClothesType type,
    List<ClothesAttributeDto> attributes
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

- 이미지 업로드가 있는 API(`POST /api/clothes`, `PATCH /api/clothes/{clothesId}`, `PATCH /api/users/{userId}/profiles`)는 `multipart/form-data`로 `request`(JSON) + `image`(binary) 파트를 함께 받습니다.

```java
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<ClothesDto> create(
    @RequestPart @Valid ClothesCreateRequest request,
    @RequestPart(required = false) MultipartFile image
) { ... }
```

---

## 2. JPA Entity

PK는 UUID, 시간 타입은 `Instant`로 통일합니다(`LocalDateTime` 금지). Spring Data Auditing(`@EnableJpaAuditing`) 사용. PK 생성은 **`@GeneratedValue(strategy = GenerationType.UUID)`**(JPA/Hibernate 자동 생성)로 통일합니다 — 애플리케이션에서 직접 `UUID.randomUUID()`를 필드에 할당하지 않습니다. Hibernate 6+는 UUID 생성을 DB 왕복 없이 메모리에서 처리하므로 `save()`/`persist()` 호출 시점에 이미 ID가 채워집니다.

> **설계 노트**: 상속 계층(`BaseEntity`/`BaseUpdatableEntity`/`BaseSoftDeletableEntity`) 자체를 두지 않습니다(팀원 리뷰로 확정). `id`/`createdAt`/`updatedAt`은 필드 1~2개짜리라 굳이 `@Embeddable`로 감싸지 않고 **각 엔티티가 직접 선언**합니다 — `@Embeddable`은 `SoftDeletable`처럼 상태+행동이 있는 값 객체에만 사용합니다(단일 필드 래핑은 실무에서도 잘 안 씀).

```java
// global/entity/SoftDeletable.java — 소프트 삭제가 필요한 엔티티에만 선택적으로 붙이는 값 객체
@Getter
@Embeddable
public class SoftDeletable {

    private Instant deletedAt;

    public void delete() { this.deletedAt = Instant.now(); }
    public boolean isDeleted() { return deletedAt != null; }
}
```

각 엔티티는 `@Id`, `@CreatedDate`/`@LastModifiedDate` 필드를 직접 선언하고, 소프트 삭제가 필요할 때만 `SoftDeletable`을 `@Embedded`로 조합합니다. `@CreatedDate`/`@LastModifiedDate`가 동작하려면 **엔티티마다 `@EntityListeners(AuditingEntityListener.class)`를 직접 선언**해야 합니다(상속이 아니므로 자동 적용되지 않음). 생성은 **정적 팩토리 메서드**로 합니다. `@Builder`/`@SuperBuilder`는 외부에 노출하지 않고 **`private`로만** 사용해 정적 팩토리 메서드 내부 구현에서 활용할 수 있습니다(필드가 많은 엔티티의 가독성용, 팀원 리뷰로 확정) — 생성 진입점은 항상 정적 팩토리 메서드 하나로 고정됩니다. Setter 금지 — 상태 변경은 의도가 드러나는 메서드명으로.

```java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "users")
@Entity
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;   // BCryptPasswordEncoder 해싱값만 저장 — 멘토 피드백 #6

    @Enumerated(EnumType.STRING)
    private Role role;

    private boolean locked;

    public static User register(String email, String encodedPassword, String name) {
        User user = new User();
        user.email = email;
        user.password = encodedPassword;
        user.role = Role.USER;
        user.locked = false;
        return user;
    }

    public void lock() { this.locked = true; }
    public void unlock() { this.locked = false; }
    public void changePassword(String encodedPassword) { this.password = encodedPassword; }
}
```

필드가 많아 위처럼 하나씩 대입하면 가독성이 떨어지는 엔티티는, `@Builder`/`@SuperBuilder`를 `private`으로 붙여 정적 팩토리 메서드 내부에서만 사용합니다 — 외부에서는 여전히 정적 팩토리 메서드 하나로만 생성합니다.

```java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "profiles")
@Entity
public class Profile {

    @Id
    private UUID id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;   // User와 1:1, PK가 곧 FK — @MapsId로 User의 PK를 그대로 공유(별도 UUID 채번 없음)

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    private Instant updatedAt;

    // ... latitude, longitude, x, y, locationNames, temperatureSensitivity, profileImageUrl 등

    @Builder(access = AccessLevel.PRIVATE)
    private Profile(User user, int temperatureSensitivity) {
        this.user = user;
        this.temperatureSensitivity = temperatureSensitivity;
    }

    public static Profile create(User user) {
        return Profile.builder()
            .user(user)
            .temperatureSensitivity(3)   // 기본값
            .build();
    }
}
```

- 연관관계 fetch 전략은 **LAZY**로 통일. 필요 시 JPQL fetch join으로 명시적 로딩.
- **단방향 `@ManyToOne`만 사용**, `@OneToMany` 금지 — 역방향 조회는 Repository 쿼리로.
- `@JoinColumn(name = "...")`은 항상 명시, 컬럼명은 `{필드명}_id` 규칙.
- cascade 범위는 도메인별 ADR 결정에 따름(예: User 삭제 시 Feed/Comment/Follow 처리 방식).

### 2-1. 도메인별 데이터 운영(삭제/보존) 전략

회원 탈퇴 엔드포인트가 없으므로(아래 참고) User는 하드 삭제 대상이 아닙니다. 나머지 도메인은 **웬만하면 소프트 삭제**를 기본으로 하고, 복구·감사 가치가 없는 순수 관계/휘발성/시스템 데이터만 하드 삭제로 예외를 둡니다(팀원 리뷰로 확정) — 소프트 삭제는 상속이 아니라 [2번 컴포지션](#2-jpa-entity)(`SoftDeletable`)으로 붙입니다.

| 도메인 | 전략 | 근거 |
| --- | --- | --- |
| **User** | 하드 삭제 없음(엔드포인트 자체가 없음), 휴면 계정 기능은 범위 제외 | 회원 탈퇴 API 미제공, `dormant`/`lastLoginAt`은 팀원 리뷰로 제거(erd.md 설계 노트 1) |
| **Clothes** | **소프트** (`SoftDeletable`) | 개인 소유 콘텐츠라 실수 삭제 복구 가치가 있음. `Feed.ootds`가 게시 시점 JSONB 스냅샷이라 `Clothes` 소프트/하드 여부와 무관하게 기존 피드 표시 유지(erd.md 설계 노트 3). 이미지(S3 등) 파일은 소프트 삭제 시점엔 정리하지 않고, 이후 배치로 정리 |
| **ClothesAttributeDef** | 하드 삭제 + **사용 중이면 삭제 차단(409)** | 삭제 자체가 이미 참조 카운트로 막혀 있어 하드 삭제해도 고아 참조가 생기지 않음 |
| **Feed** | **소프트** (`SoftDeletable`) | 사용자 콘텐츠, 실수 삭제 복구·운영 감사 가치가 있음. Comment/Like는 별도로 cascade 삭제하지 않음 — Feed가 소프트 삭제되면 서비스 레이어에서 이미 조회 시 404 처리되므로 고아 상태로 남아도 노출되지 않음(추후 배치로 정리 가능). `weather_id`를 FK로 걸지는 미결정(erd.md 설계 노트 4) |
| **FeedLike** | 하드 삭제 | 단순 토글(좋아요/취소) 관계 테이블, 복구할 콘텐츠가 없음 |
| **Comment** | ⚠️ **수정/삭제 API 자체가 스펙에 없음**(`GET`/`POST`만 존재) — 추가되면 Feed와 동일하게 소프트 삭제 검토 | `docs/api-docs.json`에 `PATCH`·`DELETE /api/feeds/{feedId}/comments/{commentId}`가 없음 |
| **Follow** | 하드 삭제 | 단순 토글(팔로우/언팔로우) 관계 테이블, 복구할 콘텐츠가 없음 |
| **Notification** | 하드 삭제 | 일회성/휘발성 데이터라 복구 가치 없음. `read_at` 컬럼은 제거(erd.md 설계 노트 6) — `DELETE /api/notifications/{notificationId}`는 물리 삭제로 확정, 별도 읽음 정리 배치 불필요 |
| **DirectMessage** | 삭제 API 없음 — 무기한 보관 (개인정보 보관기간 정책은 이번 프로젝트 범위 밖으로 명시만) | 스펙에 삭제 엔드포인트 없음 |
| **Weather** | 하드 삭제 + retention 배치(예: 7일 지난 예보 데이터 정리) | 사용자 콘텐츠가 아니라 배치로 쌓이는 시계열 예보 데이터, 무한 보관은 비효율 |

```java
// Clothes — 소프트 삭제가 필요한 엔티티는 SoftDeletable을 추가로 조합
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
public class Clothes {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Embedded
    private SoftDeletable softDeletable = new SoftDeletable();

    // ...

    public void delete() { softDeletable.delete(); }
    public boolean isDeleted() { return softDeletable.isDeleted(); }
}
```

조회 쿼리는 항상 `deletedAt IS NULL` 조건을 Repository 쿼리 메서드/QueryDSL에서 명시적으로 포함합니다(`@Where` 전역 필터는 숨겨진 동작이라 지양). 단건 조회에서 소프트 삭제된 행이 조회되면 서비스 레이어에서 `NotFoundException`으로 처리합니다.

#### 소셜 로그인 계정 연동 — `SOCIAL_ACCOUNT` 테이블

`UserDto.linkedOAuthProviders`는 FE 타입엔 있지만 `api-docs.json` 스키마엔 없는 필드입니다(수행계획서 [알려진 계약 차이] 참고). `User`에 배열 컬럼을 추가하는 대신, 별도 `SocialAccount` 엔티티(`user_id`, `provider`, `provider_id`)로 관리하고 **응답 생성 시점에 조회해서 파생**시킵니다.

```java
// domain/authuser/user/entity/SocialAccount.java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private OAuthProvider provider;   // GOOGLE, KAKAO

    private String providerId;

    public static SocialAccount link(User user, OAuthProvider provider, String providerId) {
        SocialAccount account = new SocialAccount();
        account.user = user;
        account.provider = provider;
        account.providerId = providerId;
        return account;
    }
}
```

`(provider, provider_id)`에 UNIQUE 제약을 걸어 같은 소셜 계정이 다른 유저에 중복 연동되지 않게 합니다.

#### 비밀번호 초기화 — Redis 토큰화

`POST /api/auth/reset-password`는 임시 비밀번호를 이메일로 보내는 대신, Redis에 재설정 토큰을 발급합니다. `User` Entity에는 관련 컬럼(`tempPassword`/`tempPasswordExpiresAt`)을 두지 않습니다(팀원 리뷰로 DB 컬럼 → Redis 이동, erd.md 설계 노트 10).

- Redis 키: `password-reset:{token}` → value: `userId`, TTL 30분(예시)
- 재설정 링크/토큰을 이메일로 전달, 토큰 유효기간 내 요청만 비밀번호 변경 허용
- 로그인 로직에는 임시 비밀번호 검증 분기가 없습니다 — 토큰 검증 후 즉시 `User.changePassword()`로 실제 비밀번호를 교체합니다.

#### 비정규화 카운터 — `Feed.likeCount`/`commentCount`

목록 조회마다 `COUNT(*)`를 실행하지 않도록 `Feed`에 카운터 컬럼을 둡니다. 좋아요/댓글 생성·삭제 트랜잭션 안에서 **반드시 함께 증감**해야 실제 값과 어긋나지 않습니다.

```java
@Transactional
public void like(UUID feedId, UUID userId) {
    feedLikeRepository.save(FeedLike.create(feedId, userId));
    feedRepository.incrementLikeCount(feedId);   // @Modifying 벌크 업데이트로 원자적 증감
}
```

### 2-2. DB 제약/인덱스 네이밍 규칙

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| Primary Key | `PK_{TABLE}` | `PK_USERS` |
| Foreign Key | `FK_{참조테이블}_TO_{현재테이블}_{순번}` | `FK_users_TO_feeds_1` |
| Check | `CHK_{table}_{column}` | `CHK_users_role` |
| Unique | `UQ_{table}_{columns}` | `UQ_follows_follower_followee` |
| Index | `IDX_{table}_{columns}` | `IDX_feeds_author_created` |

같은 테이블에서 같은 참조 테이블로 FK가 여러 개면(`FOLLOW.follower_id`/`followee_id`처럼 둘 다 `users` 참조) `{순번}`으로 구분합니다(`FK_users_TO_follows_1`, `FK_users_TO_follows_2`).

### 2-3. Flyway 마이그레이션

스키마는 Flyway가 관리합니다(`ddl-auto: validate`, Hibernate 자동 DDL 미사용). 마이그레이션 파일은 `src/main/resources/db/migration/V{n}__{설명}.sql`에 둡니다.

**이미 적용된(커밋된) 마이그레이션 파일은 절대 수정하지 않습니다** — 스키마를 바꿔야 하면 새 버전 파일(`V{n+1}__...`)을 추가합니다. 기존 파일을 고치면 이미 적용받은 팀원/환경과 체크섬이 어긋나 Flyway가 마이그레이션을 거부합니다.

---

## 3. 인증 — JWT

otboo는 **JWT Bearer 토큰** 기반입니다. 커스텀 인증 헤더는 사용하지 않습니다.

```
Authorization: Bearer {accessToken}
```

- 로그인(`POST /api/auth/sign-in`)은 **`multipart/form-data`**로 `username`/`password`를 받습니다(JSON 아님 — FE가 이미 이렇게 구현됨).
- 액세스 토큰은 응답 바디(`JwtDto.accessToken`)로, 리프레시 토큰은 **`REFRESH_TOKEN` httpOnly 쿠키**로 관리합니다. `POST /api/auth/refresh`는 쿠키에서 리프레시 토큰을 읽어 재발급합니다.
- CSRF 토큰은 `GET /api/auth/csrf-token` 호출 시 `XSRF-TOKEN` 쿠키로 발급합니다.
- `JwtAuthenticationFilter`가 `Authorization` 헤더를 파싱해 `SecurityContext`에 인증 정보를 채웁니다. Controller에서 현재 사용자 ID가 필요하면 커스텀 리졸버(`@CurrentUserId UUID userId` 형태)로 받습니다 — `String`으로 UUID를 다루지 않습니다.
- 소셜 로그인은 Spring Security OAuth2 Client 표준 흐름(`/oauth2/authorization/{google|kakao}`)을 그대로 사용하며 커스텀 REST 엔드포인트를 만들지 않습니다. OAuth2 로그인 성공 후 SPA로 토큰을 넘기는 방식은 3차 스프린트 착수 전 ADR로 확정합니다.

### ⚠️ 요청 바디의 `authorId`/`ownerId`/`followerId`는 반드시 서버에서 검증

`FeedCreateRequest.authorId`, `CommentCreateRequest.authorId`, `ClothesCreateRequest.ownerId`, `FollowCreateRequest.followerId` 등은 **요청 바디 필드로 전달**됩니다(서버가 토큰만으로 유추하지 않음). 이 값을 그대로 신뢰해 저장하면 다른 사용자를 사칭한 요청이 가능해집니다. **Service 레이어에서 반드시 `@CurrentUserId`로 얻은 인증된 사용자 ID와 요청 바디의 ID가 일치하는지 검증**하고, 불일치 시 `403 Forbidden`을 반환합니다.

```java
@Transactional
public FeedResponse create(FeedCreateRequest request, UUID currentUserId) {
    if (!request.authorId().equals(currentUserId)) {
        throw FeedForbiddenException.authorMismatch(currentUserId, request.authorId());
    }
    ...
}
```

---

## 4. 공통 응답 포맷

성공 응답은 DTO를 직접 반환합니다(별도 래퍼 클래스 없음). "ApiResponse"는 계획 문서에서 **공통 예외 처리 인프라 전체**(`ErrorResponse` + `GlobalExceptionHandler`)를 가리키는 말이지, 성공 응답을 감싸는 클래스가 아닙니다 — 실수로 래퍼 클래스를 만들지 않도록 주의.

에러 응답만 공통 포맷 `ErrorResponse`로 통일합니다. **필드는 `docs/api-docs.json`의 실제 스키마 기준**(`timestamp`/`code`/`status` 필드는 없음):

```java
// global/dto/ErrorResponse.java
public record ErrorResponse(
    String exceptionName,           // 예외 클래스 simple name
    String message,
    Map<String, Object> details     // 어떤 값이 원인인지 (예: {"clothesId": "uuid-..."})
) {}
```

```java
// 성공 — 데이터 있음: DTO 직접 반환
return ResponseEntity.ok(clothesDto);

// 성공 — 201 Created
return ResponseEntity.status(HttpStatus.CREATED).body(clothesDto);

// 성공 — 데이터 없음 (삭제, 좋아요 등)
return ResponseEntity.noContent().build();

// global/exception/GlobalExceptionHandler.java — 공통 케이스
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OtbooException.class)
    public ResponseEntity<ErrorResponse> handleOtbooException(OtbooException e) {
        return ResponseEntity
            .status(e.getStatus())
            .body(new ErrorResponse(e.getClass().getSimpleName(), e.getMessage(), e.getDetails()));
    }
}
```

상태 코드는 `ErrorCode`가 아니라 **예외 자신이 들고 있습니다**(아래 5번) — `GlobalExceptionHandler`는 `e.getStatus()`만 읽으면 되므로 도메인이 늘어나도 수정할 필요가 없습니다. 도메인에 일반 `ErrorResponse` 포맷을 벗어나는 특수 케이스(추가 헤더, 다른 응답 바디 등)가 있으면 해당 도메인 `exception/` 패키지에 별도 `@RestControllerAdvice(basePackages = "...")`를 두어 그 케이스만 먼저 처리하고, 나머지는 `GlobalExceptionHandler`가 그대로 처리합니다.

```java
// domain/clothesrecommend/clothes/exception/ClothesExceptionHandler.java — 특수 케이스만 도메인에서 처리
@RestControllerAdvice(basePackages = "com.sprint.mission.otboo.domain.clothesrecommend.clothes")
public class ClothesExceptionHandler {

    @ExceptionHandler(ClothesAttributeDefInUseException.class)
    public ResponseEntity<ErrorResponse> handleAttributeDefInUse(ClothesAttributeDefInUseException e) {
        // 예: 참조 중인 Clothes 목록처럼 details 이상의 정보를 응답에 더 채워야 할 때만 도메인에서 별도 처리
        return ResponseEntity.status(e.getStatus())
            .body(new ErrorResponse(e.getClass().getSimpleName(), e.getMessage(), e.getDetails()));
    }
}
```

---

## 5. 커스텀 예외

도메인별 구체 예외는 자신의 HTTP 상태 코드와 메시지를 `private static final` 필드로 직접 들고, 팩토리 메서드로 원인을 명시합니다. 별도 `ErrorCode` enum은 두지 않습니다.

```java
public abstract class OtbooException extends RuntimeException {
    @Getter private final HttpStatus status;
    @Getter private final Map<String, Object> details;

    protected OtbooException(HttpStatus status, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.details = details;
    }
}

public abstract class ClothesException extends OtbooException {
    protected ClothesException(HttpStatus status, String message, Map<String, Object> details) {
        super(status, message, details);
    }
}

public class ClothesNotFoundException extends ClothesException {

    private static final HttpStatus STATUS = HttpStatus.NOT_FOUND;  
    private static final String MESSAGE = "이미 사용 중인 이메일입니다.";
  
    private ClothesNotFoundException(Map<String, Object> details) {
        super(STATUS, MESSAGE, details);
    }

    public static ClothesNotFoundException withId(UUID clothesId) {
        return new ClothesNotFoundException(Map.of("clothesId", clothesId));
    }
}
```

> **`OtbooException` 상속은 컨트롤러까지 전파돼 HTTP 응답으로 나가는 예외에만 필요합니다.** 배치 Step 내부 실패, 외부 클라이언트 파싱 실패 등 컨트롤러까지 올라가지 않고 내부 로깅(`log.error` 등)에서만 소비되고 끝나는 예외는 `OtbooException`/도메인 예외 계층을 상속하지 않아도 무방합니다 — 이 계층은 `GlobalExceptionHandler`가 `status`/`details`를 읽어 `ErrorResponse`로 변환하기 위한 것이라, 애초에 그 경로를 안 타는 예외까지 굳이 맞출 필요는 없습니다. 일반 `RuntimeException`(또는 그 하위)으로 두면 됩니다.

---

## 6. 커서 페이지네이션

**FE `CursorResponse<T>`와 필드명이 정확히 일치해야 합니다**:

```java
// global/dto/CursorPageResponse.java
public record CursorPageResponse<T>(
    List<T> data,
    String nextCursor,       // 다음 페이지 없으면 null
    UUID nextIdAfter,        // 다음 페이지 없으면 null — FE는 idAfter라는 이름의 uuid로 보냄
    boolean hasNext,
    long totalCount,
    String sortBy,
    SortDirection sortDirection   // ASCENDING | DESCENDING
) {}
```

요청 파라미터는 도메인별 `*ListParams` record로 받습니다(`docs/api-docs.json` 기준, 도메인마다 정렬 기준이 다름):

```java
// domain/feed/dto/FeedListParams.java
public record FeedListParams(
    String cursor,
    UUID idAfter,
    int limit,
    FeedSortBy sortBy,             // createdAt | likeCount
    SortDirection sortDirection,
    String keywordLike,            // ES 검색 대상 (3차 스프린트, 멘토 피드백 #5)
    SkyStatus skyStatusEqual,
    PrecipitationType precipitationTypeEqual,
    UUID authorIdEqual
) {}
```

```java
@GetMapping
public ResponseEntity<CursorPageResponse<FeedDto>> getFeeds(
    @ParameterObject @ModelAttribute @Valid FeedListParams params
) {
    return ResponseEntity.ok(feedService.getFeeds(params));
}
```

정렬 기준 Enum 값은 FE 파라미터와 정확히 일치해야 하며 `@JsonProperty`로 camelCase를 명시합니다.

```java
public enum FeedSortBy {
    @JsonProperty("createdAt")   CREATED_AT,
    @JsonProperty("likeCount")   LIKE_COUNT
}

public enum SortDirection { ASCENDING, DESCENDING }
```

---

## 7. `@Transactional` 사용 규칙

Service 클래스에 `@Transactional(readOnly = true)`를 기본으로 걸고, 데이터 변경이 있는 메서드에만 `@Transactional`을 개별 적용합니다.

```java
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class ClothesService {

    private final ClothesRepository clothesRepository;

    public ClothesDto getClothes(UUID clothesId) {
        return clothesRepository.findById(clothesId)
            .map(clothesMapper::toDto)
            .orElseThrow(() -> ClothesNotFoundException.withId(clothesId));
    }

    @Transactional
    public ClothesDto create(ClothesCreateRequest request, MultipartFile image) {
        ...
    }
}
```

---

## 8. 외부 API 클라이언트 — Feign Client (멘토 피드백 #1)

**`RestTemplate` 사용 금지.** 기상청/Kakao/구매링크 크롤링/LLM 호출은 모두 `external/` 패키지의 Feign 인터페이스로 정의합니다.

```java
// external/kma/KmaWeatherClient.java
@FeignClient(name = "kma-weather", url = "${external.kma.base-url}")
public interface KmaWeatherClient {

    @GetMapping("/getUltraSrtFcst")
    KmaResponse getUltraShortForecast(@SpringQueryMap KmaForecastRequest request);
}
```

- 클라이언트 인터페이스는 `external/{provider}/` 아래에만 두고, 도메인 서비스는 이 인터페이스를 주입받아 사용 — 도메인 코드에 외부 API 세부 스펙(파라미터명, 응답 포맷)이 새어나가지 않도록 `external/{provider}/dto/`에서 도메인 DTO로 변환 후 반환.
- 타임아웃/재시도/에러 디코더는 `global/config/FeignConfig.java`에서 공통 설정.
- 외부 API 실패는 재시도 로직 + 실패 알림으로 대응(개선 사항 — 수행계획서 참고).

---

## 9. 테스트

클래스명은 `{대상클래스}Test`, 메서드명과 `@DisplayName`은 한글로 작성합니다. `@Nested` + `given / when / then` 구조를 유지합니다. **`@Nested` 블록의 클래스명은 영어로 작성**합니다(한글 식별자는 테스트 오류 가능성을 높임) — `@DisplayName`과 테스트 메서드명은 계속 한글 유지.

**테스트 픽스처는 FixtureMonkey로 생성**(멘토 피드백 #2, ADR로 EasyRandom 대신 확정 — 수동 빌더/생성자 나열 지양). 단, `then`에서 비교할 **기대 응답 DTO는 FixtureMonkey를 쓰지 않고 명시적으로 생성**합니다 — FixtureMonkey는 `when`에 넘길 입력/요청 픽스처에만 사용합니다(`docs/guide/fixture-monkey.md` §8 참고):

```java
// FixtureMonkey 예시 (jakarta-validation 플러그인으로 제약 인지 생성)
// DTO가 record라 setter가 없으므로 objectIntrospector 지정은 필수
FixtureMonkey fixtureMonkey = FixtureMonkey.builder()
    .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
    .plugin(new JakartaValidationPlugin())
    .build();
ClothesCreateRequest request = fixtureMonkey.giveMeBuilder(ClothesCreateRequest.class)
    .set("type", ClothesType.TOP)
    .sample();
```

```java
@ExtendWith(MockitoExtension.class)
class ClothesServiceTest {

    @InjectMocks ClothesService clothesService;
    @Mock ClothesRepository clothesRepository;

    @Nested
    @DisplayName("의상 등록")
    class RegisterClothes {

        @Test
        @DisplayName("소유자가 아니면 예외 발생")
        void 소유자가_아니면_예외_발생() {
            // given
            // when & then
            assertThatThrownBy(() -> clothesService.create(request, otherUserId))
                .isInstanceOf(ClothesForbiddenException.class);
        }
    }
}

// Repository 테스트 — Testcontainers 실제 PostgreSQL
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class ClothesRepositoryTest {

    @Autowired ClothesRepository clothesRepository;
}
```

외부 API(기상청/Kakao/LLM) 연동 로직은 목업뿐 아니라 **실제 API 호출 기반 검증 테스트**를 별도로 포함합니다(응답 스펙 변경을 CI에서 조기 감지). 이 테스트는 네트워크/API 키/외부 서비스 가용성에 의존해 불안정할 수 있으므로, 실제 외부 API를 처음 연동하는 시점에 `build.gradle`에 아래 설정을 추가해 기본 `test` 태스크에서 격리합니다.

```groovy
tasks.named('test') {
    useJUnitPlatform {
        excludeTags 'external'
    }
}

tasks.register('externalTest', Test) {
    group = 'verification'
    description = '실제 외부 API 호출 검증 테스트 - 기본 test 태스크에서 제외됨'
    testClassesDirs = sourceSets.test.output.classesDirs
    classpath = sourceSets.test.runtimeClasspath
    useJUnitPlatform {
        includeTags 'external'
    }
}
```

실제 네트워크 호출이 있는 테스트 클래스에는 `@Tag("external")`을 붙여 기본 `test`에서 빠지고 `externalTest`로만 실행되게 합니다(현재 `dev`에는 아직 `external/` 연동이 없어 이 설정도 없습니다 — 첫 Feign Client 추가 PR에서 함께 반영).

**`externalTest`는 CI 워크플로에 연결하지 않습니다** — 매 PR마다 외부 API를 실제로 호출하면 네트워크/키 문제로 CI가 그 API 상태에 좌우되기 때문입니다. CI(추후 구성될 `ci-pr.yml` 포함)는 기본 `test` 태스크만 돌리고, `externalTest`는 응답 스펙 변경이 의심될 때 로컬에서 `./gradlew externalTest`로 개발자가 직접 실행합니다.

---

## 10. Mapper (수동 변환)

Entity → DTO 변환은 MapStruct 없이 **수동 매퍼 클래스**로 작성합니다(팀원 리뷰로 확정) — 어노테이션 프로세서 생성 코드에 기대지 않고, 변환 로직을 코드로 직접 확인·디버깅할 수 있습니다. `mapper/` 패키지에 `@Component` 클래스로 두고 Service에서 생성자 주입받아 사용합니다. Service 안에서 직접 변환 로직을 작성하지 않습니다.

```java
@Component
public class ClothesMapper {

    public ClothesDto toDto(Clothes clothes) {
        return new ClothesDto(
            clothes.getId(),
            clothes.getOwnerId(),
            clothes.getName(),
            clothes.getImageUrl(),
            clothes.getType(),
            clothes.getAttributes().stream().map(this::toAttributeDto).toList()
        );
    }

    private ClothesAttributeWithDefDto toAttributeDto(ClothesAttribute attribute) {
        return new ClothesAttributeWithDefDto(attribute.getDefinitionId(), attribute.getValue());
    }
}

@Component
@RequiredArgsConstructor
public class FeedMapper {

    private final ClothesMapper clothesMapper;   // 다른 매퍼 조합은 생성자 주입으로 구성

    public FeedDto toDto(Feed feed, UUID requestUserId) {
        return new FeedDto(
            feed.getId(),
            feed.getAuthorId(),
            feed.getContent(),
            feed.isLikedBy(requestUserId)
        );
    }
}
```

---

## 11. 로깅

`@Slf4j` 사용, `System.out.println` 금지.

| 레벨 | 사용 시점 |
|------|---------|
| `log.debug` | 메서드 진입, 중간 상태값 확인 |
| `log.info` | 주요 액션 성공 (생성·삭제·배치 완료) |
| `log.warn` | 예외 발생, 비즈니스 규칙 위반 |
| `log.error` | 예상치 못한 오류, 외부 API 실패 |

> 위치 정보(위·경도)와 DM 메시지 원문은 개인정보이므로 로그에 그대로 남기지 않습니다(수행계획서 보안 섹션).

---

## 12. WebSocket / SSE 연동 규칙 (FE가 이미 고정 구현 — 반드시 그대로 맞출 것)

### WebSocket (DM)

- SockJS 엔드포인트: `/ws`, STOMP, CONNECT 헤더 `Authorization: Bearer {accessToken}`
- 발행 destination: `/pub/direct-messages_send`, body `{senderId, receiverId, content}`
- 구독 destination: `/sub/direct-messages_{두 사용자 UUID를 문자열 사전순 정렬 후 "_"로 연결}`

```java
@MessageMapping("/direct-messages_send")
public void send(DirectMessageSendRequest request) {
    DirectMessageDto saved = directMessageService.send(request);
    String destination = resolveDestination(request.senderId(), request.receiverId());
    messagingTemplate.convertAndSend(destination, saved);
}

private String resolveDestination(UUID senderId, UUID receiverId) {
    String a = senderId.toString();
    String b = receiverId.toString();
    return a.compareTo(b) < 0
        ? "/sub/direct-messages_" + a + "_" + b
        : "/sub/direct-messages_" + b + "_" + a;
}
```

### SSE (알림)

- 엔드포인트 `GET /api/sse`, `LastEventId` 쿼리 파라미터로 재연결 시 유실 이벤트 복구
- 이벤트명은 **`notifications`로 고정** (변경 금지 — FE가 이 이름으로 `addEventListener`)
- 5차 스프린트부터 Kafka 발행 → Redis 세션 관리로 전환(멘토 피드백 #4). 전환 이후에도 이벤트명/페이로드 계약은 동일하게 유지.

```java
sseEmitter.send(SseEmitter.event()
    .id(eventId)
    .name("notifications")
    .data(notificationDto));
```

---

## 13. Git 컨벤션 (요약 — 상세는 수행계획서 참고)

- 브랜치: `main` / `dev` / `{prefix}/{domain}/{description}`
- prefix: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `batch`, `deploy` (`infra`가 아니라 `deploy` — 인프라 **도메인 라벨**과 이름 충돌 방지)
- Issue 제목: 위 prefix를 대문자 대괄호로 (`[FEAT]`, `[DEPLOY]` 등)
- 머지: Squash and Merge, **2인 이상** 승인 + CI 통과
- **ADR은 Issue가 아니라 GitHub Discussions** — `[ADR] 제목` 형식, 배경/선택지/결정/영향 구조. 자세한 규칙은 수행계획서 [기술 결정 논의 방식 — ADR] 참고.
- Fork 기반 워크플로우(10단계)는 0번 참고.

---

## 14. 금기 사항

다음 패턴은 절대 사용하지 않습니다. PR 리뷰 시 즉시 reject 사유입니다.

### 의존성 주입
- `@Autowired` 필드 주입 금지 → `@RequiredArgsConstructor` + 생성자 주입

### 외부 API
- `RestTemplate` 사용 금지 → **Feign Client** (멘토 피드백 #1)

### 예외 처리
- `Optional.get()` 직접 호출 금지 → `orElseThrow()`
- 빈 catch 블록 금지 → 최소 `log.error()` 로깅
- `e.printStackTrace()` 금지 → `log.error("message", e)`

### JPA / DB
- N+1 쿼리 — fetch join / `@EntityGraph` **권장**(금지는 아님, 발생 시 트러블슈팅으로 해결)
- Entity에 `@Builder` / `@SuperBuilder`를 `public`/패키지 기본 접근자로 노출 금지 → `private`로만 사용, 생성 진입점은 정적 팩토리 메서드로 고정
- `save()` 반환값 무시 금지 → 반환된 영속 엔티티 사용

### 인증/보안
- 요청 바디의 `authorId`/`ownerId`/`followerId` 등을 검증 없이 그대로 신뢰 금지 → 인증된 사용자 ID와 일치 여부 서버 검증 (3번 참고)
- 비밀번호 평문 저장/로그 노출 금지 → `BCryptPasswordEncoder` (멘토 피드백 #6)
- SSE 이벤트명(`notifications`), STOMP destination 임의 변경 금지 → FE가 하드코딩되어 있음 (12번 참고)

### 설계
- Controller에 비즈니스 로직 금지 → Service 위임
- `String`으로 UUID 파라미터 처리 금지 → `UUID` 타입 직접 선언
- `@Scheduled` 메서드에 직접 로직 금지 → Service 메서드 위임
- Swagger 어노테이션을 Controller에 직접 작성 금지 → `controller/api/*Api` 인터페이스에만
- 성공 응답을 감싸는 별도 래퍼 클래스("ApiResponse") 신규 작성 금지 → DTO 직접 반환 (4번 참고)

### 테스트
- `@SpringBootTest` 남발 금지 → 슬라이스 테스트
- 테스트 간 상태 공유 금지 → `@BeforeEach`로 매 테스트 초기화
- 수동으로 필드 하나씩 채운 픽스처 다수 생성 금지 → FixtureMonkey (멘토 피드백 #2)

### 로깅
- `System.out.println` 금지 → `@Slf4j` + `log.info/debug/error`

### 커밋
- AI co-author 커밋 금지 — `Co-authored-by: Claude` 등 AI 귀속 문구를 커밋 메시지에 포함하지 않습니다.
- `.gitignore` 대상 파일 커밋 금지 — `git add` 전 반드시 확인.
