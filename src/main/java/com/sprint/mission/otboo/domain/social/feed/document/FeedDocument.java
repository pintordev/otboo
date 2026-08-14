package com.sprint.mission.otboo.domain.social.feed.document;

import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

@Getter
@Setting(settingPath = "elasticsearch/feed-settings.json")
@Document(indexName = "feeds")
public class FeedDocument {

  @Id
  private String id;

  @Field(type = FieldType.Text, analyzer = "korean")
  private String content;

  @Field(type = FieldType.Keyword)
  private String authorId;

  @Field(type = FieldType.Keyword)
  private SkyStatus skyStatus;

  @Field(type = FieldType.Keyword)
  private PrecipitationType precipitationType;

  @Field(type = FieldType.Date)
  private Instant createdAt;

  @Field(type = FieldType.Long)
  private long likeCount;

  public static FeedDocument from(Feed feed) {
    FeedDocument doc = new FeedDocument();
    doc.id = feed.getId().toString();
    doc.content = feed.getContent();
    doc.authorId = feed.getAuthorId().toString();
    doc.skyStatus = feed.getSkyStatus();
    doc.precipitationType = feed.getPrecipitationType();
    doc.createdAt = feed.getCreatedAt();
    doc.likeCount = feed.getLikeCount();
    return doc;
  }
}
