package com.sprint.mission.otboo.domain.social.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.navercorp.fixturemonkey.jakarta.validation.plugin.JakartaValidationPlugin;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesDto;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.service.ClothesService;
import com.sprint.mission.otboo.domain.social.feed.dto.OotdSnapshot;
import com.sprint.mission.otboo.domain.social.feed.exception.ClothesOwnershipException;
import com.sprint.mission.otboo.domain.social.feed.exception.OotdNotFoundException;
import com.sprint.mission.otboo.global.file.properties.FileImplType;
import com.sprint.mission.otboo.global.file.properties.FileProperties;
import com.sprint.mission.otboo.global.file.util.FileUrlResolver;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("OotdSnapshotProvider")
class OotdSnapshotProviderTest {

  static final FixtureMonkey fm = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .plugin(new JakartaValidationPlugin())
      .build();

  static final String BASE_URL = "http://localhost:8080/uploads";

  static final FileUrlResolver fileUrlResolver = new FileUrlResolver(
      new FileProperties(FileImplType.LOCAL, BASE_URL, 5242880, Set.of("jpg"), null, null));

  @Mock
  ClothesService clothesService;

  OotdSnapshotProvider ootdSnapshotProvider;

  @BeforeEach
  void setUp() {
    ootdSnapshotProvider = new OotdSnapshotProvider(clothesService, fileUrlResolver);
  }

  @Nested
  @DisplayName("readOotds")
  class ReadOotds {

    @Test
    @DisplayName("clothesIds로 조회한 ClothesDto를 OotdSnapshot으로 변환해 반환한다")
    void clothesIds로_조회한_ClothesDto를_OotdSnapshot으로_변환해_반환한다() {
      // given
      UUID authorId = UUID.randomUUID();
      UUID clothesId1 = UUID.randomUUID();
      UUID clothesId2 = UUID.randomUUID();
      List<UUID> clothesIds = List.of(clothesId1, clothesId2);

      ClothesDto dto1 = fm.giveMeBuilder(ClothesDto.class)
          .set("id", clothesId1)
          .set("ownerId", authorId)
          .set("name", "패딩")
          .sample();
      ClothesDto dto2 = fm.giveMeBuilder(ClothesDto.class)
          .set("id", clothesId2)
          .set("ownerId", authorId)
          .set("name", "청바지")
          .sample();
      when(clothesService.getClothesByIds(clothesIds)).thenReturn(List.of(dto1, dto2));

      // when
      List<OotdSnapshot> result = ootdSnapshotProvider.readOotds(clothesIds, authorId);

      // then
      assertThat(result).hasSize(2);
      assertThat(result.get(0).clothesId()).isEqualTo(clothesId1);
      assertThat(result.get(0).name()).isEqualTo("패딩");
      assertThat(result.get(1).clothesId()).isEqualTo(clothesId2);
      assertThat(result.get(1).name()).isEqualTo("청바지");
    }

    @Test
    @DisplayName("resolve된 imageUrl은 원본 키로 변환해 저장한다")
    void resolve된_imageUrl은_원본_키로_변환해_저장한다() {
      // given
      UUID authorId = UUID.randomUUID();
      UUID clothesId = UUID.randomUUID();
      List<UUID> clothesIds = List.of(clothesId);
      ClothesDto dto = fm.giveMeBuilder(ClothesDto.class)
          .set("id", clothesId)
          .set("ownerId", authorId)
          .set("imageUrl", BASE_URL + "/clothes/uuid.jpg")
          .sample();
      when(clothesService.getClothesByIds(clothesIds)).thenReturn(List.of(dto));

      // when
      List<OotdSnapshot> result = ootdSnapshotProvider.readOotds(clothesIds, authorId);

      // then
      assertThat(result.get(0).imageUrl()).isEqualTo("clothes/uuid.jpg");
    }

    @Test
    @DisplayName("imageUrl이 null이면 null을 그대로 저장한다")
    void imageUrl이_null이면_null을_그대로_저장한다() {
      // given
      UUID authorId = UUID.randomUUID();
      UUID clothesId = UUID.randomUUID();
      List<UUID> clothesIds = List.of(clothesId);
      ClothesDto dto = fm.giveMeBuilder(ClothesDto.class)
          .set("id", clothesId)
          .set("ownerId", authorId)
          .set("imageUrl", null)
          .sample();
      when(clothesService.getClothesByIds(clothesIds)).thenReturn(List.of(dto));

      // when
      List<OotdSnapshot> result = ootdSnapshotProvider.readOotds(clothesIds, authorId);

      // then
      assertThat(result.get(0).imageUrl()).isNull();
    }

    @Test
    @DisplayName("빈 clothesIds면 빈 리스트를 반환한다")
    void 빈_clothesIds면_빈_리스트를_반환한다() {
      // given
      UUID authorId = UUID.randomUUID();

      // when
      List<OotdSnapshot> result = ootdSnapshotProvider.readOotds(List.of(), authorId);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("일부 clothesId를 조회할 수 없으면 OotdNotFoundException을 던진다")
    void 일부_clothesId를_조회할_수_없으면_OotdNotFoundException을_던진다() {
      // given
      UUID authorId = UUID.randomUUID();
      UUID existingId = UUID.randomUUID();
      UUID missingId = UUID.randomUUID();
      List<UUID> clothesIds = List.of(existingId, missingId);

      ClothesDto dto = fm.giveMeBuilder(ClothesDto.class)
          .set("id", existingId)
          .set("ownerId", authorId)
          .sample();
      when(clothesService.getClothesByIds(clothesIds)).thenReturn(List.of(dto));

      // when & then
      assertThatThrownBy(() -> ootdSnapshotProvider.readOotds(clothesIds, authorId))
          .isInstanceOf(OotdNotFoundException.class)
          .satisfies(ex -> {
            OotdNotFoundException e = (OotdNotFoundException) ex;
            assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getDetails()).isEmpty();
          });
    }

    @Test
    @DisplayName("작성자 소유가 아닌 의상이 포함되면 ClothesOwnershipException을 던진다")
    void 작성자_소유가_아닌_의상이_포함되면_ClothesOwnershipException을_던진다() {
      // given
      UUID authorId = UUID.randomUUID();
      UUID otherOwnerId = UUID.randomUUID();
      UUID clothesId1 = UUID.randomUUID();
      UUID clothesId2 = UUID.randomUUID();
      List<UUID> clothesIds = List.of(clothesId1, clothesId2);

      ClothesDto mine = fm.giveMeBuilder(ClothesDto.class)
          .set("id", clothesId1)
          .set("ownerId", authorId)
          .sample();
      ClothesDto others = fm.giveMeBuilder(ClothesDto.class)
          .set("id", clothesId2)
          .set("ownerId", otherOwnerId)
          .sample();
      when(clothesService.getClothesByIds(clothesIds)).thenReturn(List.of(mine, others));

      // when & then
      assertThatThrownBy(() -> ootdSnapshotProvider.readOotds(clothesIds, authorId))
          .isInstanceOf(ClothesOwnershipException.class)
          .satisfies(ex -> {
            ClothesOwnershipException e = (ClothesOwnershipException) ex;
            assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(e.getDetails()).isEmpty();
          });
    }
  }
}
