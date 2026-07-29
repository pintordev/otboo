# QueryDSL 사용 가이드

> 이전 프로젝트(monew)에서 실제로 검증된 패턴을 그대로 가져왔습니다. `docs/conventions.md` §6(커서 페이지네이션), §2-2(네이밍)와 함께 봅니다.

## 1. 설정

`JPAQueryFactory`는 전역 빈 하나로 등록하고, 각 Repository는 이 빈을 주입받아 씁니다.

```java
// global/config/QuerydslConfig.java
@Configuration
public class QuerydslConfig {

  @Bean
  public JPAQueryFactory jpaQueryFactory(EntityManager em) {
    return new JPAQueryFactory(em);
  }
}
```

## 2. Repository 구조

기본 CRUD는 `JpaRepository`, 동적 조건이 필요한 조회는 `CustomRepository` 인터페이스 + `Impl` 구현체로 분리합니다. 최종
Repository는 둘을 함께 상속합니다.

```
repository/
  querydsl/
    FeedCustomRepository.java        # 인터페이스
    impl/
      FeedCustomRepositoryImpl.java  # 구현체 (@Repository)
  FeedRepository.java                # extends JpaRepository<Feed, UUID>, FeedCustomRepository
```

```java
// domain/social/feed/repository/querydsl/FeedCustomRepository.java
public interface FeedCustomRepository {

  CursorPageResponse<FeedDto> getFeeds(FeedListParams params);
}
```

## 3. 커서 페이지네이션 구현

`limit + 1`개를 조회해 `hasNext`를 판단하고, 다음 커서 값은 마지막 행에서 뽑습니다. 정렬 기준이 여러 개면(`createdAt`/`likeCount` 등) 커서
비교식도 정렬 기준별로 분기합니다.

```java
// domain/social/feed/repository/querydsl/impl/FeedCustomRepositoryImpl.java
package com.sprint.mission.otboo.domain.social.feed.repository.querydsl.impl;

import static com.sprint.mission.otboo.domain.social.feed.entity.QFeed.feed;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedDto;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedListParams;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedSortBy;
import com.sprint.mission.otboo.domain.social.feed.repository.querydsl.FeedCustomRepository;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.dto.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
public class FeedCustomRepositoryImpl implements FeedCustomRepository {

  private final JPAQueryFactory queryFactory;

  @Override
  public CursorPageResponse<FeedDto> getFeeds(FeedListParams params) {
    List<FeedDto> raw = queryFactory
        .select(Projections.constructor(
            FeedDto.class,
            feed.id,
            feed.authorId,
            feed.content,
            feed.likeCount,
            feed.createdAt
        ))
        .from(feed)
        .where(
            eqAuthorId(params.authorIdEqual()),
            isNotDeleted(),
            cursorCondition(params)
        )
        .orderBy(
            buildOrderSpecifier(params.sortBy(), params.sortDirection()),
            buildIdOrderSpecifier(params.sortDirection())
        )
        .limit(params.limit() + 1L)
        .fetch();

    boolean hasNext = raw.size() > params.limit();
    List<FeedDto> data = hasNext ? raw.subList(0, params.limit()) : raw;

    String nextCursor = null;
    UUID nextIdAfter = null;
    if (hasNext && !data.isEmpty()) {
      FeedDto last = data.get(data.size() - 1);
      nextCursor = extractCursor(last, params.sortBy());
      nextIdAfter = last.id();
    }

    return new CursorPageResponse<>(
        data, nextCursor, nextIdAfter, hasNext, data.size(),
        params.sortBy().name(), params.sortDirection()
    );
  }

  private BooleanExpression eqAuthorId(UUID authorId) {
    return authorId == null ? null : feed.authorId.eq(authorId);
  }

  private BooleanExpression isNotDeleted() {
    return feed.softDeletable.deletedAt.isNull();
  }

  private BooleanExpression cursorCondition(FeedListParams params) {
    if (params.cursor() == null) {
      return null;
    }
    boolean isAsc = params.sortDirection() == SortDirection.ASCENDING;
    return switch (params.sortBy()) {
      case CREATED_AT ->
          cursorExpr(feed.createdAt, Instant.parse(params.cursor()), params.idAfter(), isAsc);
      case LIKE_COUNT ->
          cursorExpr(feed.likeCount, Long.parseLong(params.cursor()), params.idAfter(), isAsc);
    };
  }

  private BooleanExpression cursorExpr(
      com.querydsl.core.types.dsl.ComparableExpression<Instant> field, Instant value, UUID idAfter,
      boolean isAsc
  ) {
    return isAsc
        ? field.gt(value).or(field.eq(value).and(feed.id.gt(idAfter)))
        : field.lt(value).or(field.eq(value).and(feed.id.lt(idAfter)));
  }

  private BooleanExpression cursorExpr(
      com.querydsl.core.types.dsl.NumberExpression<Long> field, Long value, UUID idAfter,
      boolean isAsc
  ) {
    return isAsc
        ? field.gt(value).or(field.eq(value).and(feed.id.gt(idAfter)))
        : field.lt(value).or(field.eq(value).and(feed.id.lt(idAfter)));
  }

  private OrderSpecifier<?> buildOrderSpecifier(FeedSortBy sortBy, SortDirection direction) {
    Order order = direction == SortDirection.ASCENDING ? Order.ASC : Order.DESC;
    return switch (sortBy) {
      case CREATED_AT -> new OrderSpecifier<>(order, feed.createdAt);
      case LIKE_COUNT -> new OrderSpecifier<>(order, feed.likeCount);
    };
  }

  private OrderSpecifier<?> buildIdOrderSpecifier(SortDirection direction) {
    Order order = direction == SortDirection.ASCENDING ? Order.ASC : Order.DESC;
    return new OrderSpecifier<>(order, feed.id);
  }

  private String extractCursor(FeedDto last, FeedSortBy sortBy) {
    return switch (sortBy) {
      case CREATED_AT -> last.createdAt().toString();
      case LIKE_COUNT -> String.valueOf(last.likeCount());
    };
  }
}
```

## 4. DTO에서 방어할 것 (`*ListParams`)

QueryDSL 구현체에 도달하기 전에, 요청 파라미터 record 자체에서 잘못된 조합을 막습니다(monew `CommentQueryCondition` 패턴). `@Valid`가
Controller에서 이 검증을 자동으로 태워줍니다.

- **`cursor`/`idAfter`는 함께 오거나 함께 없어야 함** — 하나만 있으면 커서 조회 로직이 `NullPointerException` 없이 조용히 이상하게 동작할
  수 있음
- **`cursor` 포맷이 `sortBy`와 맞는지** — `LIKE_COUNT`면 숫자 문자열, `CREATED_AT`이면 `Instant.parse` 가능한 문자열이어야
  함. 안 그러면 리포지토리 구현체 안에서 `NumberFormatException`/`DateTimeParseException`이 500으로 새어나감
- **`limit`은 `@Min(1)`(+필요 시 `@Max`)** — 0 이하나 과도하게 큰 값으로 전체 스캔되는 것 방지
- **`sortBy`/`sortDirection`은 `@NotNull`** — 기본값이 필요하면 Controller에서 파라미터 기본값(`defaultValue`)으로 채우고,
  DTO 자체는 널을 허용하지 않음

```java
// domain/social/feed/dto/FeedListParams.java
public record FeedListParams(
        String cursor,
        UUID idAfter,
        @NotNull @Min(1) Integer limit,
        @NotNull FeedSortBy sortBy,
        @NotNull SortDirection sortDirection,
        String keywordLike,
        SkyStatus skyStatusEqual,
        PrecipitationType precipitationTypeEqual,
        UUID authorIdEqual
    ) {

  @AssertTrue(message = "cursor, idAfter는 함께 전달되어야 합니다")
  public boolean isCursorAndIdAfterConsistent() {
    return (cursor == null && idAfter == null) || (cursor != null && idAfter != null);
  }

  @AssertTrue(message = "likeCount 기준 커서는 숫자여야 합니다")
  public boolean isCursorFormatValidForSortBy() {
    if (cursor == null || sortBy != FeedSortBy.LIKE_COUNT) {
      return true;
    }
    try {
      Long.parseLong(cursor);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
```

## 5. 정렬 기준 Enum — 쿼리 파라미터 변환

`@JsonProperty("createdAt")`는 JSON 요청/응답 바디에만 적용됩니다. `sortBy=createdAt` 같은 **GET 쿼리 파라미터**는
`@ModelAttribute`로 바인딩할 때 Spring이 기본 `Enum.valueOf()`를 쓰기 때문에, `@JsonProperty` 값과 무관하게 실제 enum 상수명(
`CREATED_AT`)을 그대로 보내지 않으면 400으로 튕깁니다. FE는 camelCase(`createdAt`)로 보내므로, `Converter<String, Enum>`을
직접 등록해야 합니다(monew `WebMvcConfig.addFormatters` 패턴).

```java
// global/config/WebMvcConfig.java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  @Override
  public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(String.class, FeedSortBy.class, value -> switch (value) {
      case "createdAt" -> FeedSortBy.CREATED_AT;
      case "likeCount" -> FeedSortBy.LIKE_COUNT;
      default -> InvalidSortByException.withValue(value);
    });
    // 정렬 기준 enum이 있는 도메인마다(Clothes, Follow, User 등) 여기 계속 추가
  }
}
```

```java
// global/exception/InvalidSortByException.java
public class InvalidSortByException extends OtbooException {

  private InvalidSortByException(Map<String, Object> details) {
    super(HttpStatus.BAD_REQUEST, "지원하는 정렬 기준이 아닙니다.", details);
  }

  public static InvalidSortByException withValue(String value) {
    return new InvalidSortByException(Map.of("value", value));
  }
}
```

> Converter 없이 배포하면 FE는 항상 400을 받게 되므로, 정렬 기준 enum을 추가하는 이슈마다 이 Converter 등록도 세트로 작업해야 합니다.

## 6. RepositoryTest 주의 사항

`@DataJpaTest`는 `@Import({JpaConfig.class, QuerydslConfig.class})`를 사용합니다.

QueryDSL 프래그먼트가 존재하면 모든 `@DataJpaTest`가 JPAQueryFactory를 요구하므로, `QuerydslConfig를` 빼면 컨텍스트 로딩이 실패합니다.

```java

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, QuerydslConfig.class})
class FeedRepositoryTest {

  @Autowired
  FeedRepository feedRepository;
}
```

## 7. 체크리스트

- [ ] 동적 조건은 `null`을 반환하는 `BooleanExpression` 메서드로 분리 (`.where(...)`에 `null`이 섞이면 QueryDSL이 자동으로 무시)
- [ ] 정렬 기준이 여러 개면 커서 비교식도 정렬 기준별로 분기
- [ ] 마지막 동일 순위(tie-break)는 항상 `id`로 보정
- [ ] `limit + 1` 조회로 `hasNext` 판단, 실제 반환은 `limit`개만
- [ ] 소프트 삭제 테이블은 조회 조건에 `deletedAt IS NULL` 반드시 포함 (`conventions.md` §2-1 참고)
- [ ] Q클래스(`QFeed` 등)는 annotationProcessor가 자동 생성 — 직접 수정 금지, `jacocoExcludes`에서 커버리지 제외 대상
- [ ] `*ListParams` record에 `cursor`/`idAfter` 동시성, `cursor` 포맷, `limit` 범위를 `@AssertTrue`/`@Min`
  등으로 방어했는지 확인 (4번 참고) — Repository 구현체까지 잘못된 값이 넘어가지 않게
- [ ] 정렬 기준 enum을 새로 추가했으면 `WebMvcConfig.addFormatters`에 해당 `Converter`도 같이 등록했는지 확인 (5번 참고) — 안 하면
  FE가 항상 400
- [ ] @DataJpaTest에 @Import({JpaConfig.class, QuerydslConfig.class}) 사용