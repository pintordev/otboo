# FixtureMonkey 사용 가이드

> `[ADR] 테스트 픽스처 전략 결정`(GitHub Discussions)에서 EasyRandom 대신 FixtureMonkey로 확정했습니다. `docs/conventions.md` §9와 함께 봅니다.

## 1. 왜 FixtureMonkey인가

otboo DTO는 대부분 Java `record` + Jakarta Validation(`@NotNull`, `@NotBlank`, `@Size` 등) 제약이 붙어 있습니다. FixtureMonkey는 `jakarta-validation` 플러그인으로 이 제약을 인지한 유효한 값을 생성할 수 있고, 특정 필드만 오버라이드하는 플루언트 API를 제공합니다.

## 2. 기본 설정

테스트 클래스마다 매번 만들지 않도록, 슬라이스 테스트 기반 클래스나 `@BeforeEach`에서 한 번만 생성해 재사용합니다.

```java
FixtureMonkey fixtureMonkey = FixtureMonkey.builder()
    .plugin(new JakartaValidationPlugin())   // @NotNull/@NotBlank/@Size 등 제약 인지
    .build();
```

## 3. 기본 생성

```java
ClothesCreateRequest request = fixtureMonkey.giveMeOne(ClothesCreateRequest.class);
```

## 4. 특정 필드만 오버라이드

```java
ClothesCreateRequest request = fixtureMonkey.giveMeBuilder(ClothesCreateRequest.class)
    .set("type", ClothesType.TOP)
    .set("name", "테스트 상의")
    .sample();
```

## 5. 리스트 생성

```java
List<ClothesDto> clothesList = fixtureMonkey.giveMeBuilder(ClothesDto.class)
    .sampleList(5);
```

## 6. Enum/중첩 필드 지정

```java
FeedCreateRequest request = fixtureMonkey.giveMeBuilder(FeedCreateRequest.class)
    .set("authorId", userId)                          // 특정 UUID로 고정 (인증된 사용자와 일치시켜야 하는 경우)
    .set("clothesIds", List.of(clothesId1, clothesId2))
    .sample();
```

## 7. Service 단위 테스트에서 활용 예

```java
@ExtendWith(MockitoExtension.class)
class ClothesServiceTest {

    static FixtureMonkey fixtureMonkey;

    @InjectMocks ClothesService clothesService;
    @Mock ClothesRepository clothesRepository;

    @BeforeAll
    static void setUpFixtureMonkey() {
        fixtureMonkey = FixtureMonkey.builder()
            .plugin(new JakartaValidationPlugin())
            .build();
    }

    @Nested
    @DisplayName("의상 등록")
    class 의상_등록 {

        @Test
        @DisplayName("소유자가 아니면 예외 발생")
        void 소유자가_아니면_예외_발생() {
            // given
            UUID ownerId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            ClothesCreateRequest request = fixtureMonkey.giveMeBuilder(ClothesCreateRequest.class)
                .set("ownerId", ownerId)
                .sample();

            // when & then
            assertThatThrownBy(() -> clothesService.create(request, otherUserId))
                .isInstanceOf(ClothesForbiddenException.class);
        }
    }
}
```

## 8. 체크리스트

- [ ] `EasyRandom`, 수동 빌더/생성자 나열 사용 금지 — FixtureMonkey만 사용 (`conventions.md` §14 금기사항)
- [ ] `@NotNull`/`@NotBlank`가 걸린 요청 DTO는 `JakartaValidationPlugin`이 적용된 인스턴스로 생성해야 검증을 실제로 통과함
- [ ] 인증된 사용자 ID와 일치시켜야 하는 필드(`authorId`, `ownerId`, `followerId` 등)는 반드시 `.set(...)`으로 고정 — 랜덤 값 그대로 두면 3번(`authorId` 검증) 관련 테스트가 우연히 통과/실패할 수 있음
- [ ] 리스트가 필요한 테스트는 `.sampleList(n)` 사용, 개별 `sample()` 반복 호출 지양
