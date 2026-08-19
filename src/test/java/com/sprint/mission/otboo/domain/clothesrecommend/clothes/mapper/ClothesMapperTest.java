package com.sprint.mission.otboo.domain.clothesrecommend.clothes.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.entity.ClothesAttributeDef;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.entity.ClothesAttributeDefValue;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesDto;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesType;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.Clothes;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.ClothesAttribute;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.sprint.mission.otboo.external.llm.dto.LlmExtractedClothesInfo;
import com.sprint.mission.otboo.external.purchase.dto.PurchasePageResponse;

class ClothesMapperTest {

  ClothesMapper clothesMapper;

  @BeforeEach
  void setUp() {
    clothesMapper = new ClothesMapper();
  }

  @Nested
  @DisplayName("DTO 변환")
  class ToDto {

    @Test
    @DisplayName("속성이 없는 의상을 DTO로 변환하면 빈 속성 목록을 반환한다")
    void 속성이_없는_의상을_DTO로_변환하면_빈_속성_목록을_반환한다() {
      // given
      Clothes clothes = Clothes.create(UUID.randomUUID(), "기본 티셔츠", ClothesType.TOP);

      // when
      ClothesDto result = clothesMapper.toDto(clothes, List.of(), Map.of());

      // then
      assertThat(result.id()).isEqualTo(clothes.getId());
      assertThat(result.name()).isEqualTo("기본 티셔츠");
      assertThat(result.type()).isEqualTo(ClothesType.TOP);
      assertThat(result.attributes()).isEmpty();
    }

    @Test
    @DisplayName("속성이 있는 의상을 DTO로 변환하면 정의 정보가 포함된 속성을 반환한다")
    void 속성이_있는_의상을_DTO로_변환하면_정의_정보가_포함된_속성을_반환한다() {
      // given
      Clothes clothes = Clothes.create(UUID.randomUUID(), "검정 바지", ClothesType.BOTTOM);

      ClothesAttributeDef colorDef = ClothesAttributeDef.create("색상");
      ClothesAttribute attribute =
          ClothesAttribute.create(clothes.getId(), colorDef, "블랙");

      ClothesAttributeDefValue value1 =
          ClothesAttributeDefValue.create(colorDef, "블랙", 0);
      ClothesAttributeDefValue value2 =
          ClothesAttributeDefValue.create(colorDef, "화이트", 1);

      Map<UUID, List<ClothesAttributeDefValue>> defValuesMap =
          Map.of(colorDef.getId(), List.of(value1, value2));

      // when
      ClothesDto result = clothesMapper.toDto(clothes, List.of(attribute), defValuesMap);

      // then
      assertThat(result.attributes()).hasSize(1);
      assertThat(result.attributes().get(0).definitionName()).isEqualTo("색상");
      assertThat(result.attributes().get(0).value()).isEqualTo("블랙");
      assertThat(result.attributes().get(0).selectableValues())
          .containsExactly("블랙", "화이트");
    }

    @Test
    @DisplayName("imageUrl이 없는 의상을 DTO로 변환하면 imageUrl이 null이다")
    void imageUrl이_없는_의상을_DTO로_변환하면_imageUrl이_null이다() {
      // given
      Clothes clothes = Clothes.create(UUID.randomUUID(), "모자", ClothesType.HAT);

      // when
      ClothesDto result = clothesMapper.toDto(clothes, List.of(), Map.of());

      // then
      assertThat(result.imageUrl()).isNull();
    }
    @Test
    @DisplayName("PurchasePageResponse를_ClothesDto로_변환한다")
    void PurchasePageResponse를_ClothesDto로_변환한다() {
      // given
      PurchasePageResponse ogResult = new PurchasePageResponse(
          "데님 자켓", "https://image.musinsa.com/goods/001.jpg", "설명", "무신사");

      // when
      ClothesDto result = clothesMapper.toDto(ogResult);

      // then
      assertThat(result.id()).isNull();
      assertThat(result.name()).isEqualTo("데님 자켓");
      assertThat(result.imageUrl()).isEqualTo("https://image.musinsa.com/goods/001.jpg");
    }

    @Test
    @DisplayName("LlmExtractedClothesInfo를_ClothesDto로_변환한다")
    void LlmExtractedClothesInfo를_ClothesDto로_변환한다() {
      // given
      LlmExtractedClothesInfo info = new LlmExtractedClothesInfo("후드티", null);

      // when
      ClothesDto result = clothesMapper.toDto(info);

      // then
      assertThat(result.id()).isNull();
      assertThat(result.name()).isEqualTo("후드티");
      assertThat(result.imageUrl()).isNull();
    }
  }
}
