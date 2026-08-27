package com.sprint.mission.otboo.domain.social.feed.mapper;

import com.sprint.mission.otboo.domain.social.common.dto.UserSummary;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedDto;
import com.sprint.mission.otboo.domain.social.feed.dto.OotdDto;
import com.sprint.mission.otboo.domain.social.feed.dto.OotdSnapshot;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.PrecipitationDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.TemperatureDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WeatherSummaryDto;
import com.sprint.mission.otboo.global.file.util.FileUrlResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeedMapper {

  private final FileUrlResolver fileUrlResolver;

  public FeedDto toDto(Feed feed, UserSummary author, boolean likedByMe) {
    return new FeedDto(
        feed.getId(),
        feed.getCreatedAt(),
        feed.getUpdatedAt(),
        author,
        toWeatherSummaryDto(feed),
        toOotdDtos(feed.getOotds()),
        feed.getContent(),
        feed.getLikeCount(),
        feed.getCommentCount(),
        likedByMe
    );
  }

  private WeatherSummaryDto toWeatherSummaryDto(Feed feed) {
    if (feed.getSkyStatus() == null) {
      return null;
    }
    return new WeatherSummaryDto(
        feed.getWeatherId(),
        feed.getSkyStatus(),
        new PrecipitationDto(
            feed.getPrecipitationType(),
            feed.getPrecipitationAmount(),
            feed.getPrecipitationProbability()
        ),
        new TemperatureDto(
            feed.getTemperatureCurrent(),
            feed.getTemperatureCompared(),
            feed.getTemperatureMin(),
            feed.getTemperatureMax()
        )
    );
  }

  private List<OotdDto> toOotdDtos(List<OotdSnapshot> snapshots) {
    if (snapshots == null) {
      return List.of();
    }
    return snapshots.stream()
        .map(s -> new OotdDto(
            s.clothesId(), s.name(), resolveImageUrl(s.imageUrl()), s.type(), s.attributes()))
        .toList();
  }

  /**
   * 스냅샷에는 원본 키가 저장되지만, 이 수정 전에 등록된 피드에는 resolve된 URL이 담겨 있다. {@code resolve}는 멱등이 아니라 완전한 URL에 다시
   * 씌우면 접두사가 중복되므로, 멱등인 {@code extractKey}를 먼저 태워 두 경우를 모두 처리한다.
   */
  private String resolveImageUrl(String imageUrl) {
    return fileUrlResolver.resolve(fileUrlResolver.extractKey(imageUrl));
  }
}
