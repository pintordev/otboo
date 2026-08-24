package com.sprint.mission.otboo.domain.clothesrecommend.clothes.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.entity.ClothesAttributeDef;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.repository.ClothesAttributeDefRepository;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.ClothesAttribute;
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
@DisplayName("ClothesAttributeRepository")
class ClothesAttributeRepositoryTest {

  @Autowired
  private ClothesAttributeRepository clothesAttributeRepository;

  @Autowired
  private ClothesAttributeDefRepository clothesAttributeDefRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  @Nested
  @DisplayName("DeleteAllByClothesId")
  class DeleteAllByClothesId {

    @Test
    @DisplayName("속성 정의가 지연 로딩 프록시로 채워져도 의상 ID로 전부 삭제한다")
    void 속성_정의가_지연_로딩_프록시로_채워져도_의상_ID로_전부_삭제한다() {
      // given
      UUID clothesId = UUID.randomUUID();
      ClothesAttributeDef definition =
          clothesAttributeDefRepository.save(ClothesAttributeDef.create("색상"));
      clothesAttributeRepository.save(
          ClothesAttribute.create(clothesId, definition, "블랙"));

      // 영속성 컨텍스트를 비워야 재조회 시 definition이 지연 로딩 프록시로 채워진다
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      clothesAttributeRepository.deleteAllByClothesId(clothesId);
      testEntityManager.flush();

      // then
      List<ClothesAttribute> remained =
          clothesAttributeRepository.findAllByClothesIdWithDefinition(clothesId);
      assertThat(remained).isEmpty();
    }

    @Test
    @DisplayName("다른 의상의 속성은 삭제하지 않는다")
    void 다른_의상의_속성은_삭제하지_않는다() {
      // given
      UUID targetClothesId = UUID.randomUUID();
      UUID otherClothesId = UUID.randomUUID();
      ClothesAttributeDef definition =
          clothesAttributeDefRepository.save(ClothesAttributeDef.create("소재"));
      clothesAttributeRepository.save(
          ClothesAttribute.create(targetClothesId, definition, "면"));
      clothesAttributeRepository.save(
          ClothesAttribute.create(otherClothesId, definition, "울"));

      testEntityManager.flush();
      testEntityManager.clear();

      // when
      clothesAttributeRepository.deleteAllByClothesId(targetClothesId);
      testEntityManager.flush();

      // then
      assertThat(clothesAttributeRepository.findAllByClothesIdWithDefinition(targetClothesId))
          .isEmpty();
      assertThat(clothesAttributeRepository.findAllByClothesIdWithDefinition(otherClothesId))
          .hasSize(1);
    }

    @Test
    @DisplayName("삭제 직후 같은 의상·정의 조합을 다시 저장해도 유니크 제약을 위반하지 않는다")
    void 삭제_직후_같은_의상_정의_조합을_다시_저장해도_유니크_제약을_위반하지_않는다() {
      // given
      UUID clothesId = UUID.randomUUID();
      ClothesAttributeDef definition =
          clothesAttributeDefRepository.save(ClothesAttributeDef.create("색상"));
      clothesAttributeRepository.save(
          ClothesAttribute.create(clothesId, definition, "블랙"));

      testEntityManager.flush();
      testEntityManager.clear();

      // when: 의상 수정 시의 "기존 속성 전체 삭제 후 재등록" 흐름과 동일하다
      clothesAttributeRepository.deleteAllByClothesId(clothesId);
      clothesAttributeRepository.saveAll(
          List.of(ClothesAttribute.create(clothesId, definition, "네이비")));
      testEntityManager.flush();

      // then
      List<ClothesAttribute> attributes =
          clothesAttributeRepository.findAllByClothesIdWithDefinition(clothesId);
      assertThat(attributes).hasSize(1);
      assertThat(attributes.get(0).getValue()).isEqualTo("네이비");
    }
  }

  @Nested
  @DisplayName("속성 정의 지연 로딩")
  class LazyDefinition {

    @Test
    @DisplayName("조회한 속성의 정의를 지연 로딩으로 읽을 수 있다")
    void 조회한_속성의_정의를_지연_로딩으로_읽을_수_있다() {
      // given
      UUID clothesId = UUID.randomUUID();
      ClothesAttributeDef definition =
          clothesAttributeDefRepository.save(ClothesAttributeDef.create("소재"));
      ClothesAttribute saved = clothesAttributeRepository.save(
          ClothesAttribute.create(clothesId, definition, "면"));

      testEntityManager.flush();
      testEntityManager.clear();

      // when: definition은 프록시로 채워지므로 접근 시점에 초기화된다
      ClothesAttribute found = clothesAttributeRepository.findById(saved.getId())
          .orElseThrow();

      // then
      assertThat(found.getDefinition().getName()).isEqualTo("소재");
    }
  }
}
