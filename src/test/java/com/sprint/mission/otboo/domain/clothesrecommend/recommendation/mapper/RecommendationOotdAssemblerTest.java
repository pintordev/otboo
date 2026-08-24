package com.sprint.mission.otboo.domain.clothesrecommend.recommendation.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.entity.ClothesAttributeDef;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.entity.ClothesAttributeDefValue;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.repository.ClothesAttributeDefValueRepository;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesType;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.Clothes;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.ClothesAttribute;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.repository.ClothesAttributeRepository;
import com.sprint.mission.otboo.domain.social.feed.dto.OotdDto;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationOotdAssembler")
class RecommendationOotdAssemblerTest {

  @Mock
  ClothesAttributeRepository clothesAttributeRepository;

  @Mock
  ClothesAttributeDefValueRepository clothesAttributeDefValueRepository;

  private final FileProperties fileProperties = new FileProperties(
      FileImplType.LOCAL, "http://localhost:8080/uploads", 5242880, Set.of("jpg"), null, null);

  RecommendationOotdAssembler assembler;

  @BeforeEach
  void setUp() {
    assembler = new RecommendationOotdAssembler(
        clothesAttributeRepository,
        clothesAttributeDefValueRepository,
        new FileUrlResolver(fileProperties));
  }

  @Nested
  @DisplayName("OotdDto 변환")
  class ToOotdDtoList {

    @Test
    @DisplayName("빈 목록을 전달하면 빈 목록을 반환한다")
    void 빈_목록을_전달하면_빈_목록을_반환한다() {
      // when
      List<OotdDto> result = assembler.toOotdDtoList(List.of());

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("imageUrl에 저장 키가 담긴 의상은 완전한 URL로 변환한다")
    void imageUrl에_저장_키가_담긴_의상은_완전한_URL로_변환한다() {
      // given
      Clothes clothes = Clothes.create(UUID.randomUUID(), "가디건", ClothesType.OUTER);
      clothes.changeImageUrl("clothes/0e1f2a3b-4c5d-6e7f-8a9b-0c1d2e3f4a5b.jpg");

      given(clothesAttributeRepository.findAllByClothesIdsWithDefinition(anyList()))
          .willReturn(List.of());

      // when
      List<OotdDto> result = assembler.toOotdDtoList(List.of(clothes));

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).imageUrl()).isEqualTo(
          "http://localhost:8080/uploads/clothes/0e1f2a3b-4c5d-6e7f-8a9b-0c1d2e3f4a5b.jpg");
    }

    @Test
    @DisplayName("imageUrl이 없는 의상은 imageUrl이 null이다")
    void imageUrl이_없는_의상은_imageUrl이_null이다() {
      // given
      Clothes clothes = Clothes.create(UUID.randomUUID(), "모자", ClothesType.HAT);

      given(clothesAttributeRepository.findAllByClothesIdsWithDefinition(anyList()))
          .willReturn(List.of());

      // when
      List<OotdDto> result = assembler.toOotdDtoList(List.of(clothes));

      // then
      assertThat(result.get(0).imageUrl()).isNull();
    }

    @Test
    @DisplayName("속성이 있는 의상은 정의 이름과 선택 가능한 값이 함께 채워진다")
    void 속성이_있는_의상은_정의_이름과_선택_가능한_값이_함께_채워진다() {
      // given
      Clothes clothes = Clothes.create(UUID.randomUUID(), "검정 바지", ClothesType.BOTTOM);

      ClothesAttributeDef colorDef = ClothesAttributeDef.create("색상");
      ClothesAttribute attribute =
          ClothesAttribute.create(clothes.getId(), colorDef, "블랙");

      given(clothesAttributeRepository.findAllByClothesIdsWithDefinition(anyList()))
          .willReturn(List.of(attribute));
      given(clothesAttributeDefValueRepository.findAllByDefinitionIds(anyList()))
          .willReturn(List.of(
              ClothesAttributeDefValue.create(colorDef, "블랙", 0),
              ClothesAttributeDefValue.create(colorDef, "화이트", 1)));

      // when
      List<OotdDto> result = assembler.toOotdDtoList(List.of(clothes));

      // then
      assertThat(result.get(0).attributes()).hasSize(1);
      assertThat(result.get(0).attributes().get(0).definitionName()).isEqualTo("색상");
      assertThat(result.get(0).attributes().get(0).value()).isEqualTo("블랙");
      assertThat(result.get(0).attributes().get(0).selectableValues())
          .containsExactly("블랙", "화이트");
    }
  }
}
