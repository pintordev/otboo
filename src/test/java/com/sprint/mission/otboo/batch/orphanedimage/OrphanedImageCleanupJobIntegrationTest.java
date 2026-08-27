package com.sprint.mission.otboo.batch.orphanedimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.repository.ProfileRepository;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesType;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.Clothes;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.repository.ClothesRepository;
import com.sprint.mission.otboo.domain.social.feed.dto.OotdSnapshot;
import com.sprint.mission.otboo.domain.social.feed.dto.WeatherSnapshot;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.global.testcontainers.IntegrationTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.DeletedObject;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

@SpringBootTest
@ActiveProfiles("test")
@SpringBatchTest
class OrphanedImageCleanupJobIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private JobOperatorTestUtils jobOperatorTestUtils;

  @Autowired
  @Qualifier("orphanedImageCleanupJob")
  private Job orphanedImageCleanupJob;

  @Autowired
  private UserRepository userRepository;
  @Autowired
  private ProfileRepository profileRepository;
  @Autowired
  private ClothesRepository clothesRepository;
  @Autowired
  private FeedRepository feedRepository;

  @MockitoBean
  private S3Client s3Client;

  @BeforeEach
  void setUp() {
    cleanUpTables();
    jobOperatorTestUtils.setJob(orphanedImageCleanupJob);
  }

  @AfterEach
  void tearDown() {
    // @SpringBootTest는 트랜잭션이 자동 롤백되지 않고 실제 커밋되므로, 같은 Testcontainers DB를
    // 공유하는 다른 테스트 클래스와 충돌하지 않도록 종료 시점에도 정리한다
    cleanUpTables();
  }

  private void cleanUpTables() {
    feedRepository.deleteAll();
    clothesRepository.deleteAll();
    profileRepository.deleteAll();
    userRepository.deleteAll();
  }

  private User persistUser(String name) {
    return userRepository.save(User.create(name, UUID.randomUUID() + "@otboo.cc", "password"));
  }

  private ListObjectsV2Iterable pageOf(S3Object... objects) {
    ListObjectsV2Iterable iterable = mock(ListObjectsV2Iterable.class);
    given(iterable.contents()).willReturn(() -> List.of(objects).iterator());
    return iterable;
  }

  private S3Object s3Object(String key, Instant lastModified) {
    return S3Object.builder().key(key).lastModified(lastModified).build();
  }

  private ArgumentMatcher<ListObjectsV2Request> prefixIs(String prefix) {
    return req -> req != null && prefix.equals(req.prefix());
  }

  @Nested
  @DisplayName("유실 이미지 정리 Job 실행")
  class Run {

    @Test
    @DisplayName("Profile_Clothes_Feed_스냅샷_중_하나에라도_참조된_키는_보존하고_참조_없는_키만_삭제한다")
    void Profile_Clothes_Feed_스냅샷_중_하나에라도_참조된_키는_보존하고_참조_없는_키만_삭제한다() throws Exception {
      // given
      Instant graceOld = Instant.now().minus(Duration.ofDays(2));
      Instant withinGrace = Instant.now().minus(Duration.ofHours(1));

      User author = persistUser("작성자");
      Profile profile = Profile.create(author);
      profile.changeProfileImageUrl("profile/ref.png");
      profileRepository.save(profile);

      Clothes clothes = Clothes.create(author.getId(), "상의", ClothesType.TOP);
      clothes.changeImageUrl("clothes/ref.png");
      clothesRepository.save(clothes);

      // clothes/snapshot.png는 Clothes 테이블 참조는 없지만 과거 피드 스냅샷이 참조 중
      WeatherSnapshot weatherSnapshot = new WeatherSnapshot(
          SkyStatus.CLEAR, PrecipitationType.NONE, 0.0, 0.0, 20.0, 1.0, 15.0, 25.0);
      OotdSnapshot ootdSnapshot = new OotdSnapshot(
          UUID.randomUUID(), "과거 상의", "clothes/snapshot.png", ClothesType.TOP, List.of());
      feedRepository.save(Feed.create(author.getId(), UUID.randomUUID(), "오늘의 착장",
          weatherSnapshot, List.of(ootdSnapshot)));

      ListObjectsV2Iterable profilePage = pageOf(
          s3Object("profile/ref.png", graceOld),
          s3Object("profile/orphan.png", graceOld),
          s3Object("profile/fresh.png", withinGrace));
      ListObjectsV2Iterable clothesPage = pageOf(
          s3Object("clothes/ref.png", graceOld),
          s3Object("clothes/snapshot.png", graceOld));
      given(s3Client.listObjectsV2Paginator(argThat(prefixIs("profile/")))).willReturn(profilePage);
      given(s3Client.listObjectsV2Paginator(argThat(prefixIs("clothes/")))).willReturn(clothesPage);
      given(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).willReturn(
          DeleteObjectsResponse.builder()
              .deleted(DeletedObject.builder().key("profile/orphan.png").build())
              .build());

      // when
      JobExecution execution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());

      // then
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
      ArgumentMatcher<DeleteObjectsRequest> deletesOnlyOrphan = req ->
          req.delete().objects().size() == 1
              && req.delete().objects().get(0).key().equals("profile/orphan.png");
      verify(s3Client).deleteObjects(argThat(deletesOnlyOrphan));
    }

    @Test
    @DisplayName("유실_후보가_안전_상한을_초과하면_아무것도_삭제하지_않는다")
    void 유실_후보가_안전_상한을_초과하면_아무것도_삭제하지_않는다() throws Exception {
      // given — 10개 전부 미참조(100% > maxDeleteRatio 30%)
      Instant graceOld = Instant.now().minus(Duration.ofDays(2));
      S3Object[] objects = IntStream.range(0, 10)
          .mapToObj(i -> s3Object("profile/" + i + ".png", graceOld))
          .toArray(S3Object[]::new);

      ListObjectsV2Iterable profilePage = pageOf(objects);
      ListObjectsV2Iterable clothesPage = pageOf();
      given(s3Client.listObjectsV2Paginator(argThat(prefixIs("profile/")))).willReturn(profilePage);
      given(s3Client.listObjectsV2Paginator(argThat(prefixIs("clothes/")))).willReturn(clothesPage);

      // when
      JobExecution execution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());

      // then
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
      verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }
  }
}