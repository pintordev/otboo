package com.sprint.mission.otboo.domain.clothesrecommend.clothes.repository.querydsl.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesListParams;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesType;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.Clothes;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.repository.ClothesRepository;
import com.sprint.mission.otboo.global.config.JpaConfig;
import com.sprint.mission.otboo.global.config.QuerydslConfig;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, QuerydslConfig.class})
@DisplayName("ClothesCustomRepositoryImpl")
class ClothesCustomRepositoryImplTest {

  @Autowired
  private ClothesRepository clothesRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  private ClothesListParams params(UUID ownerId, int limit, ClothesType type,
      String cursor, UUID idAfter) {
    return new ClothesListParams(cursor, idAfter, limit, type, ownerId);
  }

  private void setCreatedAt(UUID clothesId, Instant createdAt) {
    testEntityManager.getEntityManager()
        .createNativeQuery("update clothes set created_at = :createdAt where id = :id")
        .setParameter("createdAt", createdAt)
        .setParameter("id", clothesId)
        .executeUpdate();
  }

  @Nested
  @DisplayName("FindByCursor")
  class FindByCursor {

    @Test
    @DisplayName("첫 페이지 조회 시 최신순으로 limit개를 반환하고 hasNext를 표시한다")
    void 첫_페이지_조회_시_최신순으로_limit개를_반환하고_hasNext를_표시한다() {
      // given
      UUID ownerId = UUID.randomUUID();
      List<UUID> ids = createClothesWithAscendingCreatedAt(ownerId, 5);
      testEntityManager.clear();

      // when
      CursorPageResponse<Clothes> result =
          clothesRepository.findClothes(params(ownerId, 3, null, null, null));

      // then
      assertThat(result.data()).hasSize(3);
      assertThat(result.data()).extracting(Clothes::getId)
          .containsExactly(ids.get(4), ids.get(3), ids.get(2));
      assertThat(result.hasNext()).isTrue();
      assertThat(result.totalCount()).isEqualTo(5L);
      assertThat(result.nextCursor()).isNotNull();
      assertThat(result.nextIdAfter()).isEqualTo(ids.get(2));
    }

    @Test
    @DisplayName("커서를 넘기면 다음 페이지를 최신순으로 이어서 반환한다")
    void 커서를_넘기면_다음_페이지를_최신순으로_이어서_반환한다() {
      // given
      UUID ownerId = UUID.randomUUID();
      List<UUID> ids = createClothesWithAscendingCreatedAt(ownerId, 5);
      testEntityManager.clear();

      CursorPageResponse<Clothes> firstPage =
          clothesRepository.findClothes(params(ownerId, 3, null, null, null));

      // when
      CursorPageResponse<Clothes> secondPage = clothesRepository.findClothes(
          params(ownerId, 3, null, firstPage.nextCursor(), firstPage.nextIdAfter()));

      // then
      assertThat(secondPage.data()).extracting(Clothes::getId)
          .containsExactly(ids.get(1), ids.get(0));
      assertThat(secondPage.hasNext()).isFalse();
    }

    private List<UUID> createClothesWithAscendingCreatedAt(UUID ownerId, int count) {
      List<UUID> ids = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        Clothes clothes = clothesRepository.save(Clothes.create(ownerId, "옷" + i, ClothesType.TOP));
        ids.add(clothes.getId());
      }
      testEntityManager.flush();
      for (int i = 0; i < count; i++) {
        setCreatedAt(ids.get(i), Instant.parse("2026-08-20T00:00:00Z").plusSeconds(i));
      }
      testEntityManager.flush();
      return ids;
    }

    @Test
    @DisplayName("마지막 페이지는 hasNext가 false이다")
    void 마지막_페이지는_hasNext가_false이다() {
      // given
      UUID ownerId = UUID.randomUUID();
      clothesRepository.save(Clothes.create(ownerId, "옷", ClothesType.TOP));
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      CursorPageResponse<Clothes> result =
          clothesRepository.findClothes(params(ownerId, 10, null, null, null));

      // then
      assertThat(result.data()).hasSize(1);
      assertThat(result.hasNext()).isFalse();
      assertThat(result.nextCursor()).isNull();
      assertThat(result.nextIdAfter()).isNull();
    }
  }

  @Nested
  @DisplayName("FilterByType")
  class FilterByType {

    @Test
    @DisplayName("타입을 지정하면 해당 타입의 의상만 반환한다")
    void 타입을_지정하면_해당_타입의_의상만_반환한다() {
      // given
      UUID ownerId = UUID.randomUUID();
      UUID otherOwnerId = UUID.randomUUID();
      clothesRepository.save(Clothes.create(ownerId, "상의", ClothesType.TOP));
      clothesRepository.save(Clothes.create(ownerId, "하의", ClothesType.BOTTOM));
      clothesRepository.save(Clothes.create(otherOwnerId, "남의 상의", ClothesType.TOP));
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      CursorPageResponse<Clothes> result =
          clothesRepository.findClothes(params(ownerId, 10, ClothesType.TOP, null, null));

      // then
      assertThat(result.data()).hasSize(1);
      assertThat(result.data().get(0).getType()).isEqualTo(ClothesType.TOP);
      assertThat(result.data().get(0).getOwnerId()).isEqualTo(ownerId);
      assertThat(result.totalCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("타입을 지정하지 않으면 모든 타입을 반환한다")
    void 타입을_지정하지_않으면_모든_타입을_반환한다() {
      // given
      UUID ownerId = UUID.randomUUID();
      clothesRepository.save(Clothes.create(ownerId, "상의", ClothesType.TOP));
      clothesRepository.save(Clothes.create(ownerId, "하의", ClothesType.BOTTOM));
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      CursorPageResponse<Clothes> result =
          clothesRepository.findClothes(params(ownerId, 10, null, null, null));

      // then
      assertThat(result.data()).hasSize(2);
    }
  }

  @Nested
  @DisplayName("SoftDeleteExclusion")
  class SoftDeleteExclusion {

    @Test
    @DisplayName("소프트 삭제된 의상은 목록과 총 개수에서 제외된다")
    void 소프트_삭제된_의상은_목록과_총_개수에서_제외된다() {
      // given
      UUID ownerId = UUID.randomUUID();
      Clothes active = clothesRepository.save(Clothes.create(ownerId, "활성", ClothesType.TOP));
      Clothes deleted = clothesRepository.save(Clothes.create(ownerId, "삭제됨", ClothesType.TOP));
      deleted.delete();
      clothesRepository.save(deleted);
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      CursorPageResponse<Clothes> result =
          clothesRepository.findClothes(params(ownerId, 10, null, null, null));

      // then
      assertThat(result.data()).hasSize(1);
      assertThat(result.data().get(0).getId()).isEqualTo(active.getId());
      assertThat(result.totalCount()).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("OwnerScope")
  class OwnerScope {

    @Test
    @DisplayName("다른 소유자의 의상은 반환하지 않는다")
    void 다른_소유자의_의상은_반환하지_않는다() {
      // given
      UUID ownerId = UUID.randomUUID();
      UUID otherOwnerId = UUID.randomUUID();
      clothesRepository.save(Clothes.create(ownerId, "내 옷", ClothesType.TOP));
      clothesRepository.save(Clothes.create(otherOwnerId, "남의 옷", ClothesType.TOP));
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      CursorPageResponse<Clothes> result =
          clothesRepository.findClothes(params(ownerId, 10, null, null, null));

      // then
      assertThat(result.data()).hasSize(1);
      assertThat(result.data().get(0).getName()).isEqualTo("내 옷");
    }
  }
}
