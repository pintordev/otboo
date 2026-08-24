package com.sprint.mission.otboo.domain.clothesrecommend.clothes.mapper;

import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.entity.ClothesAttributeDef;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.entity.ClothesAttributeDefValue;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesAttributeWithDefDto;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesDto;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.Clothes;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.ClothesAttribute;
import com.sprint.mission.otboo.global.file.util.FileUrlResolver;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.sprint.mission.otboo.external.llm.dto.LlmExtractedClothesInfo;
import com.sprint.mission.otboo.external.purchase.dto.PurchasePageResponse;

@Component
@RequiredArgsConstructor
public class ClothesMapper {

  private final FileUrlResolver fileUrlResolver;

  public ClothesDto toDto(Clothes clothes, List<ClothesAttribute> attributes,
      Map<UUID, List<ClothesAttributeDefValue>> defValuesByDefId) {
    List<ClothesAttributeWithDefDto> attributeDtos = attributes.stream()
        .map(attr -> toAttributeWithDefDto(attr, defValuesByDefId))
        .toList();

    return new ClothesDto(
        clothes.getId(),
        clothes.getOwnerId(),
        clothes.getName(),
        fileUrlResolver.resolve(clothes.getImageUrl()),
        clothes.getType(),
        attributeDtos
    );
  }

  // 구매 링크 추출 결과는 외부 사이트의 절대 URL이므로 FileUrlResolver를 거치지 않는다
  public ClothesDto toDto(PurchasePageResponse ogResult) {
    return new ClothesDto(null, null, ogResult.title(), ogResult.imageUrl(), null, List.of());
  }

  public ClothesDto toDto(LlmExtractedClothesInfo info) {
    return new ClothesDto(null, null, info.name(), info.imageUrl(), null, List.of());
  }

  private ClothesAttributeWithDefDto toAttributeWithDefDto(
      ClothesAttribute attribute,
      Map<UUID, List<ClothesAttributeDefValue>> defValuesByDefId) {
    ClothesAttributeDef definition = attribute.getDefinition();
    UUID defId = definition.getId();

    List<String> selectableValues = defValuesByDefId
        .getOrDefault(defId, List.of()).stream()
        .sorted(Comparator.comparingInt(ClothesAttributeDefValue::getSortOrder))
        .map(ClothesAttributeDefValue::getValue)
        .toList();

    return new ClothesAttributeWithDefDto(
        defId,
        definition.getName(),
        selectableValues,
        attribute.getValue()
    );
  }
}
