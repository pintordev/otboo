package com.sprint.mission.otboo.domain.clothesrecommend.clothes.mapper;

import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.entity.ClothesAttributeDef;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.entity.ClothesAttributeDefValue;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesAttributeWithDefDto;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesDto;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.Clothes;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.ClothesAttribute;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import com.sprint.mission.otboo.external.llm.dto.LlmExtractedClothesInfo;
import com.sprint.mission.otboo.external.purchase.dto.PurchasePageResponse;

@Component
public class ClothesMapper {

  public ClothesDto toDto(Clothes clothes, List<ClothesAttribute> attributes,
      Map<UUID, List<ClothesAttributeDefValue>> defValuesByDefId) {
    List<ClothesAttributeWithDefDto> attributeDtos = attributes.stream()
        .map(attr -> toAttributeWithDefDto(attr, defValuesByDefId))
        .toList();

    return new ClothesDto(
        clothes.getId(),
        clothes.getOwnerId(),
        clothes.getName(),
        clothes.getImageUrl(),
        clothes.getType(),
        attributeDtos
    );
  }

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
