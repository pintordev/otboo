package com.sprint.mission.otboo.batch.orphanedimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.sprint.mission.otboo.batch.orphanedimage.config.OrphanedImageCleanupProperties;
import com.sprint.mission.otboo.domain.authuser.user.repository.ProfileRepository;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.repository.ClothesRepository;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import com.sprint.mission.otboo.global.file.properties.FileImplType;
import com.sprint.mission.otboo.global.file.properties.FileProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

@ExtendWith(MockitoExtension.class)
class OrphanedImageFinderTest {

  @Mock
  private S3Client s3Client;
  @Mock
  private ProfileRepository profileRepository;
  @Mock
  private ClothesRepository clothesRepository;
  @Mock
  private FeedRepository feedRepository;
  private Clock clock;
  private OrphanedImageFinder finder;

  private static final Instant NOW = Instant.parse("2026-08-20T02:00:00Z");

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(NOW, ZoneOffset.UTC);
    finder = newFinder(List.of("profile/"));
  }

  // 안전 상한(maxDeleteRatio)을 검증하는 전용 테스트 외에는 100%로 열어둬서, 작은 픽스처 크기가
  // 우연히 비율 상한에 걸려 capped()가 튀는 일이 없게 한다.
  private OrphanedImageFinder newFinder(List<String> prefixes) {
    return newFinder(prefixes, 1.0, 500);
  }

  private OrphanedImageFinder newFinder(List<String> prefixes, double maxDeleteRatio,
      int maxDeleteAbsolute) {
    OrphanedImageCleanupProperties properties =
        new OrphanedImageCleanupProperties(prefixes, 24, 100, maxDeleteRatio, maxDeleteAbsolute);
    FileProperties fileProperties = new FileProperties(FileImplType.LOCAL,
        "http://localhost:8080/uploads", 5242880, Set.of("png"), null,
        new FileProperties.S3("otboo-uploads", "ap-northeast-2"));
    return new OrphanedImageFinder(s3Client, profileRepository, clothesRepository, feedRepository,
        clock, properties, fileProperties);
  }

  @Nested
  @DisplayName("유실 키 계산")
  class Find {

    @Test
    @DisplayName("DB에_참조가_없는_키만_유실로_판단한다")
    void DB에_참조가_없는_키만_유실로_판단한다() {
      // given
      ListObjectsV2Iterable page = pageOf(
          s3Object("profile/a.png", NOW.minus(Duration.ofDays(2))),
          s3Object("profile/b.png", NOW.minus(Duration.ofDays(2))));
      given(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class))).willReturn(page);
      given(profileRepository.findAllProfileImageUrls()).willReturn(Set.of("profile/a.png"));
      given(clothesRepository.findAllImageUrls()).willReturn(Set.of());
      given(feedRepository.findAllOotdImageKeys()).willReturn(Set.of());

      // when
      OrphanedImageFinder.Result result = finder.find();

      // then
      assertThat(result.orphanedKeys()).containsExactly("profile/b.png");
      assertThat(result.capped()).isFalse();
    }

    @Test
    @DisplayName("유예_기간_이내에_업로드된_키는_참조가_없어도_이번_회차에서_제외한다")
    void 유예_기간_이내에_업로드된_키는_참조가_없어도_이번_회차에서_제외한다() {
      // given — 방금(1시간 전) 업로드돼 아직 DB 커밋 전일 수 있는 파일
      ListObjectsV2Iterable page = pageOf(
          s3Object("profile/fresh.png", NOW.minus(Duration.ofHours(1))));
      given(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class))).willReturn(page);
      given(profileRepository.findAllProfileImageUrls()).willReturn(Set.of());
      given(clothesRepository.findAllImageUrls()).willReturn(Set.of());
      given(feedRepository.findAllOotdImageKeys()).willReturn(Set.of());

      // when
      OrphanedImageFinder.Result result = finder.find();

      // then
      assertThat(result.orphanedKeys()).isEmpty();
    }

    @Test
    @DisplayName("유실_대상이_안전_상한을_초과하면_삭제_후보를_비운채_capped를_true로_반환한다")
    void 유실_대상이_안전_상한을_초과하면_삭제_후보를_비운채_capped를_true로_반환한다() {
      // given — 전체 10개 중 4개가 유실 후보(40% > maxDeleteRatio 30%)
      finder = newFinder(List.of("profile/"), 0.3, 500);
      List<S3Object> objects = IntStream.range(0, 10)
          .mapToObj(i -> s3Object("profile/" + i + ".png", NOW.minus(Duration.ofDays(2))))
          .toList();
      ListObjectsV2Iterable page = pageOf(objects.toArray(new S3Object[0]));
      given(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class))).willReturn(page);
      Set<String> referenced = IntStream.range(4, 10)
          .mapToObj(i -> "profile/" + i + ".png").collect(Collectors.toSet());
      given(profileRepository.findAllProfileImageUrls()).willReturn(referenced);
      given(clothesRepository.findAllImageUrls()).willReturn(Set.of());
      given(feedRepository.findAllOotdImageKeys()).willReturn(Set.of());

      // when
      OrphanedImageFinder.Result result = finder.find();

      // then
      assertThat(result.capped()).isTrue();
      assertThat(result.orphanedKeys()).isEmpty();
    }

    @Test
    @DisplayName("s3Prefixes에_설정된_여러_prefix를_모두_리스팅해서_합친다")
    void s3Prefixes에_설정된_여러_prefix를_모두_리스팅해서_합친다() {
      // given
      finder = newFinder(List.of("profile/", "clothes/"));
      // argThat()가 등록 시점에 null 플레이스홀더로 호출을 시도해볼 수 있어 null-safe하게 짠다.
      ArgumentMatcher<ListObjectsV2Request> isProfilePrefix =
          req -> req != null && "profile/".equals(req.prefix());
      ArgumentMatcher<ListObjectsV2Request> isClothesPrefix =
          req -> req != null && "clothes/".equals(req.prefix());
      ListObjectsV2Iterable profilePage = pageOf(
          s3Object("profile/a.png", NOW.minus(Duration.ofDays(2))));
      ListObjectsV2Iterable clothesPage = pageOf(
          s3Object("clothes/x.png", NOW.minus(Duration.ofDays(2))));
      given(s3Client.listObjectsV2Paginator(argThat(isProfilePrefix))).willReturn(profilePage);
      given(s3Client.listObjectsV2Paginator(argThat(isClothesPrefix))).willReturn(clothesPage);
      given(profileRepository.findAllProfileImageUrls()).willReturn(Set.of());
      given(clothesRepository.findAllImageUrls()).willReturn(Set.of());
      given(feedRepository.findAllOotdImageKeys()).willReturn(Set.of());

      // when
      OrphanedImageFinder.Result result = finder.find();

      // then
      assertThat(result.orphanedKeys())
          .containsExactlyInAnyOrder("profile/a.png", "clothes/x.png");
    }

    @Test
    @DisplayName("Clothes_테이블이나_Feed_OOTD_스냅샷_중_하나에라도_참조된_키는_유실로_판단하지_않는다")
    void Clothes_테이블이나_Feed_OOTD_스냅샷_중_하나에라도_참조된_키는_유실로_판단하지_않는다() {
      // given — clothes/old.png는 Clothes 교체로 컬럼 참조는 사라졌지만 과거 피드 스냅샷이 아직 참조
      finder = newFinder(List.of("clothes/"));
      ListObjectsV2Iterable page = pageOf(
          s3Object("clothes/old.png", NOW.minus(Duration.ofDays(2))),
          s3Object("clothes/new.png", NOW.minus(Duration.ofDays(2))));
      given(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class))).willReturn(page);
      given(profileRepository.findAllProfileImageUrls()).willReturn(Set.of());
      given(clothesRepository.findAllImageUrls()).willReturn(Set.of("clothes/new.png"));
      given(feedRepository.findAllOotdImageKeys()).willReturn(Set.of("clothes/old.png"));

      // when
      OrphanedImageFinder.Result result = finder.find();

      // then — 둘 다 어딘가엔 참조돼 있어 유실 후보에서 빠진다
      assertThat(result.orphanedKeys()).isEmpty();
    }
  }

  private ListObjectsV2Iterable pageOf(S3Object... objects) {
    // contents()는 final 메서드다. 이 프로젝트 Mockito(5.x) 기본 mock maker는 final도 가로채
    // 스텁 안 하면 null을 반환하므로 iterator()가 아니라 contents() 자체를 스텁해야 한다.
    // SdkIterable<S3Object>는 iterator() 하나뿐인 함수형 인터페이스라 람다로 바로 만들 수 있다.
    ListObjectsV2Iterable iterable = mock(ListObjectsV2Iterable.class);
    given(iterable.contents()).willReturn(() -> List.of(objects).iterator());
    return iterable;
  }

  private S3Object s3Object(String key, Instant lastModified) {
    return S3Object.builder().key(key).lastModified(lastModified).build();
  }
}