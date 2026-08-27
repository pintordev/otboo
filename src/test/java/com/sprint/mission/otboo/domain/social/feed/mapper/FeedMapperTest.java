package com.sprint.mission.otboo.domain.social.feed.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.FieldReflectionArbitraryIntrospector;
import com.sprint.mission.otboo.domain.social.common.dto.UserSummary;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedDto;
import com.sprint.mission.otboo.domain.social.feed.dto.OotdSnapshot;
import com.sprint.mission.otboo.domain.social.feed.dto.WeatherSnapshot;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.global.file.properties.FileImplType;
import com.sprint.mission.otboo.global.file.properties.FileProperties;
import com.sprint.mission.otboo.global.file.util.FileUrlResolver;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FeedMapper")
class FeedMapperTest {

  static final FixtureMonkey fm = FixtureMonkey.builder()
      .objectIntrospector(FieldReflectionArbitraryIntrospector.INSTANCE)
      .build();

  static final WeatherSnapshot DUMMY_SNAPSHOT = new WeatherSnapshot(
      SkyStatus.CLEAR, PrecipitationType.NONE,
      0.0, 0.0, 28.0, 2.0, 16.0, 31.0);

  static final String BASE_URL = "http://localhost:8080/uploads";

  static final FileUrlResolver fileUrlResolver = new FileUrlResolver(
      new FileProperties(FileImplType.LOCAL, BASE_URL, 5242880, Set.of("jpg"), null, null));

  FeedMapper feedMapper = new FeedMapper(fileUrlResolver);

  @Nested
  @DisplayName("toDto 변환")
  class ToDto {

    @Test
    @DisplayName("Feed 엔티티를 FeedDto로 변환하고 likedByMe를 전달값으로 채운다")
    void Feed_엔티티를_FeedDto로_변환하고_likedByMe를_전달값으로_채운다() {
      // given
      UUID feedId = UUID.randomUUID();
      Instant createdAt = Instant.parse("2026-08-01T09:00:00Z");
      Instant updatedAt = Instant.parse("2026-08-02T10:30:00Z");
      UserSummary author = new UserSummary(UUID.randomUUID(), "테스터", null);

      Feed feed = fm.giveMeBuilder(Feed.class)
          .set("id", feedId)
          .set("createdAt", createdAt)
          .set("updatedAt", updatedAt)
          .set("content", "오늘의 착장")
          .set("likeCount", 7L)
          .set("commentCount", 3)
          .set("skyStatus", SkyStatus.CLEAR)
          .set("precipitationType", PrecipitationType.NONE)
          .set("precipitationAmount", 0.0)
          .set("precipitationProbability", 0.0)
          .set("temperatureCurrent", 28.0)
          .set("temperatureCompared", 2.0)
          .set("temperatureMin", 16.0)
          .set("temperatureMax", 31.0)
          .set("ootds", List.of())
          .sample();

      // when
      FeedDto result = feedMapper.toDto(feed, author, false);

      // then
      assertThat(result.id()).isEqualTo(feedId);
      assertThat(result.createdAt()).isEqualTo(createdAt);
      assertThat(result.updatedAt()).isEqualTo(updatedAt);
      assertThat(result.content()).isEqualTo("오늘의 착장");
      assertThat(result.likeCount()).isEqualTo(7L);
      assertThat(result.commentCount()).isEqualTo(3);
      assertThat(result.likedByMe()).isFalse();
    }

    @Test
    @DisplayName("Feed 엔티티와 UserSummary를 받아 FeedDto의 author 필드를 올바르게 채운다")
    void Feed_엔티티와_UserSummary를_받아_FeedDto의_author_필드를_올바르게_채운다() {
      // given
      Feed feed = fm.giveMeBuilder(Feed.class)
          .set("content", "오늘의 착장")
          .set("likeCount", 0L)
          .set("commentCount", 0)
          .set("skyStatus", SkyStatus.CLEAR)
          .set("precipitationType", PrecipitationType.NONE)
          .set("precipitationAmount", 0.0)
          .set("precipitationProbability", 0.0)
          .set("temperatureCurrent", 28.0)
          .set("temperatureCompared", 2.0)
          .set("temperatureMin", 16.0)
          .set("temperatureMax", 31.0)
          .set("ootds", List.of())
          .sample();

      UserSummary mockAuthor = new UserSummary(UUID.randomUUID(), "테스트유저", "profile.png");

      // when
      FeedDto result = feedMapper.toDto(feed, mockAuthor, false);

      // then
      assertThat(result.author()).isNotNull();
      assertThat(result.author().userId()).isEqualTo(mockAuthor.userId());
      assertThat(result.author().name()).isEqualTo("테스트유저");
      assertThat(result.author().profileImageUrl()).isEqualTo("profile.png");
    }

    @Test
    @DisplayName("Feed flat 컬럼을 WeatherSummaryDto로 조립한다")
    void Feed_flat_컬럼을_WeatherSummaryDto로_조립한다() {
      // given
      UserSummary author = new UserSummary(UUID.randomUUID(), "테스터", null);
      Feed feed = fm.giveMeBuilder(Feed.class)
          .set("weatherId", UUID.randomUUID())
          .set("skyStatus", SkyStatus.CLEAR)
          .set("precipitationType", PrecipitationType.NONE)
          .set("precipitationAmount", 0.0)
          .set("precipitationProbability", 0.0)
          .set("temperatureCurrent", 28.0)
          .set("temperatureCompared", 2.0)
          .set("temperatureMin", 16.0)
          .set("temperatureMax", 31.0)
          .set("ootds", List.of())
          .sample();

      // when
      FeedDto result = feedMapper.toDto(feed, author, false);

      // then
      assertThat(result.weather()).isNotNull();
      assertThat(result.weather().skyStatus()).isEqualTo(SkyStatus.CLEAR);
      assertThat(result.weather().precipitation().type()).isEqualTo(PrecipitationType.NONE);
      assertThat(result.weather().temperature().current()).isEqualTo(28.0);
      assertThat(result.weather().temperature().min()).isEqualTo(16.0);
      assertThat(result.weather().temperature().max()).isEqualTo(31.0);
    }

    @Test
    @DisplayName("skyStatus가 null이면 weather를 null로 반환한다")
    void skyStatus가_null이면_weather를_null로_반환한다() {
      // given
      UserSummary author = new UserSummary(UUID.randomUUID(), "테스터", null);
      Feed feed = fm.giveMeBuilder(Feed.class)
          .setNull("skyStatus")
          .set("ootds", List.of())
          .sample();

      // when
      FeedDto result = feedMapper.toDto(feed, author, false);

      // then
      assertThat(result.weather()).isNull();
    }

    @Test
    @DisplayName("ootds가 null이면 빈 리스트로 반환한다")
    void ootds가_null이면_빈_리스트로_반환한다() {
      // given
      UserSummary author = new UserSummary(UUID.randomUUID(), "테스터", null);
      Feed feed = Feed.create(UUID.randomUUID(), UUID.randomUUID(), "내용",
          DUMMY_SNAPSHOT, null);   // ootds = null

      // when
      FeedDto result = feedMapper.toDto(feed, author, false);

      // then
      assertThat(result.ootds()).isEmpty();
    }

    @Test
    @DisplayName("스냅샷의 원본 키를 완전한 URL로 변환해 반환한다")
    void 스냅샷의_원본_키를_완전한_URL로_변환해_반환한다() {
      // given
      OotdSnapshot snapshot = new OotdSnapshot(
          UUID.randomUUID(), "패딩", "clothes/uuid.jpg", null, List.of());
      Feed feed = Feed.create(UUID.randomUUID(), UUID.randomUUID(), "내용",
          DUMMY_SNAPSHOT, List.of(snapshot));
      UserSummary author = new UserSummary(UUID.randomUUID(), "작성자", null);

      // when
      FeedDto result = feedMapper.toDto(feed, author, false);

      // then
      assertThat(result.ootds().get(0).imageUrl())
          .isEqualTo(BASE_URL + "/clothes/uuid.jpg");
    }

    @Test
    @DisplayName("기존 스냅샷에 완전한 URL이 담겨 있어도 접두사가 중복되지 않는다")
    void 기존_스냅샷에_완전한_URL이_담겨_있어도_접두사가_중복되지_않는다() {
      // given
      OotdSnapshot snapshot = new OotdSnapshot(
          UUID.randomUUID(), "패딩", BASE_URL + "/clothes/uuid.jpg", null, List.of());
      Feed feed = Feed.create(UUID.randomUUID(), UUID.randomUUID(), "내용",
          DUMMY_SNAPSHOT, List.of(snapshot));
      UserSummary author = new UserSummary(UUID.randomUUID(), "작성자", null);

      // when
      FeedDto result = feedMapper.toDto(feed, author, false);

      // then
      assertThat(result.ootds().get(0).imageUrl())
          .isEqualTo(BASE_URL + "/clothes/uuid.jpg");
    }
  }
}
