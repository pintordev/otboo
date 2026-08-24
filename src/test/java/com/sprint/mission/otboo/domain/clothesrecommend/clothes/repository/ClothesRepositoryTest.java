package com.sprint.mission.otboo.domain.clothesrecommend.clothes.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesType;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.Clothes;
import com.sprint.mission.otboo.global.config.JpaConfig;
import com.sprint.mission.otboo.global.config.QuerydslConfig;
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
@DisplayName("ClothesRepository")
class ClothesRepositoryTest {

  @Autowired
  private ClothesRepository clothesRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  @Nested
  @DisplayName("FindActiveByOwnerId")
  class FindActiveByOwnerId {

    @Test
    @DisplayName("소프트 삭제된 의상은 제외하고 반환한다")
    void 소프트_삭제된_의상은_제외하고_반환한다() {
      // given
      UUID ownerId = UUID.randomUUID();
      Clothes active = clothesRepository.save(Clothes.create(ownerId, "활성", ClothesType.TOP));
      Clothes deleted = clothesRepository.save(Clothes.create(ownerId, "삭제됨", ClothesType.TOP));
      deleted.delete();
      clothesRepository.save(deleted);
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      List<Clothes> result = clothesRepository.findActiveByOwnerId(ownerId);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).getId()).isEqualTo(active.getId());
    }

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
      List<Clothes> result = clothesRepository.findActiveByOwnerId(ownerId);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).getName()).isEqualTo("내 옷");
    }
  }

  @Nested
  @DisplayName("FindActiveByOwnerIdAndTypeIn")
  class FindActiveByOwnerIdAndTypeIn {

    @Test
    @DisplayName("지정한 타입 목록에 속한 활성 의상만 반환한다")
    void 지정한_타입_목록에_속한_활성_의상만_반환한다() {
      // given
      UUID ownerId = UUID.randomUUID();
      UUID otherOwnerId = UUID.randomUUID();
      clothesRepository.save(Clothes.create(ownerId, "상의", ClothesType.TOP));
      clothesRepository.save(Clothes.create(ownerId, "하의", ClothesType.BOTTOM));
      clothesRepository.save(Clothes.create(ownerId, "모자", ClothesType.HAT));
      clothesRepository.save(Clothes.create(otherOwnerId, "남의 상의", ClothesType.TOP));
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      List<Clothes> result = clothesRepository.findActiveByOwnerIdAndTypeIn(
          ownerId, List.of(ClothesType.TOP, ClothesType.BOTTOM));

      // then
      assertThat(result).hasSize(2)
          .extracting(Clothes::getType)
          .containsExactlyInAnyOrder(ClothesType.TOP, ClothesType.BOTTOM);
      assertThat(result).extracting(Clothes::getOwnerId).containsOnly(ownerId);
    }

    @Test
    @DisplayName("소프트 삭제된 의상은 타입이 일치해도 제외한다")
    void 소프트_삭제된_의상은_타입이_일치해도_제외한다() {
      // given
      UUID ownerId = UUID.randomUUID();
      Clothes deleted = clothesRepository.save(Clothes.create(ownerId, "삭제된 상의", ClothesType.TOP));
      deleted.delete();
      clothesRepository.save(deleted);
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      List<Clothes> result = clothesRepository.findActiveByOwnerIdAndTypeIn(
          ownerId, List.of(ClothesType.TOP));

      // then
      assertThat(result).isEmpty();
    }
  }
}
