package com.sprint.mission.otboo.batch.orphanedimage;

import com.sprint.mission.otboo.batch.orphanedimage.config.OrphanedImageCleanupProperties;
import com.sprint.mission.otboo.domain.authuser.user.repository.ProfileRepository;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.repository.ClothesRepository;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import com.sprint.mission.otboo.global.file.properties.FileProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

@Slf4j
@Component
public class OrphanedImageFinder {

  private final S3Client s3Client;
  private final ProfileRepository profileRepository;
  private final ClothesRepository clothesRepository;
  private final FeedRepository feedRepository;
  private final Clock clock;
  private final OrphanedImageCleanupProperties properties;
  private final String bucket;

  public OrphanedImageFinder(S3Client s3Client, ProfileRepository profileRepository,
      ClothesRepository clothesRepository, FeedRepository feedRepository, Clock clock,
      OrphanedImageCleanupProperties properties, FileProperties fileProperties) {
    this.s3Client = s3Client;
    this.profileRepository = profileRepository;
    this.clothesRepository = clothesRepository;
    this.feedRepository = feedRepository;
    this.clock = clock;
    this.properties = properties;
    this.bucket = fileProperties.s3().bucket();
  }

  public record Result(List<String> orphanedKeys, boolean capped) {

  }

  public Result find() {
    Instant now = Instant.now(clock);
    Instant graceThreshold = now.minus(Duration.ofHours(properties.gracePeriodHours()));

    List<S3Object> allObjects = properties.s3Prefixes().stream()
        .flatMap(prefix -> s3Client
            .listObjectsV2Paginator(ListObjectsV2Request.builder()
                .bucket(bucket).prefix(prefix).build())
            .contents().stream())
        .toList();

    Set<String> referenced = new HashSet<>();
    referenced.addAll(profileRepository.findAllProfileImageUrls());
    referenced.addAll(clothesRepository.findAllImageUrls());
    referenced.addAll(feedRepository.findAllOotdImageKeys());

    List<String> candidates = allObjects.stream()
        .filter(obj -> obj.lastModified().isBefore(graceThreshold))
        .map(S3Object::key)
        .filter(key -> !referenced.contains(key))
        .toList();

    double ratio = allObjects.isEmpty() ? 0.0 : (double) candidates.size() / allObjects.size();
    boolean capped = candidates.size() > properties.maxDeleteAbsolute()
        || ratio > properties.maxDeleteRatio();

    if (capped) {
      log.warn("유실 이미지 삭제 후보({}개, 전체 대비 {}%)가 안전 상한을 초과해 이번 회차는 건너뜀",
          candidates.size(), Math.round(ratio * 100));
      return new Result(List.of(), true);
    }
    return new Result(candidates, false);
  }
}