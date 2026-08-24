package com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.entity.ClothesAttributeDef;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("의상 속성")
class ClothesAttributeTest {

  @Nested
  @DisplayName("생성")
  class Create {

    @Test
    @DisplayName("정적 팩토리 메서드로 생성하면 전달한 필드가 채워진다")
    void 정적_팩토리_메서드로_생성하면_전달한_필드가_채워진다() {
      // given
      UUID clothesId = UUID.randomUUID();
      ClothesAttributeDef definition = ClothesAttributeDef.create("색상");

      // when
      ClothesAttribute attribute = ClothesAttribute.create(clothesId, definition, "블랙");

      // then
      assertThat(attribute.getClothesId()).isEqualTo(clothesId);
      assertThat(attribute.getDefinition()).isEqualTo(definition);
      assertThat(attribute.getValue()).isEqualTo("블랙");
    }
  }
}
