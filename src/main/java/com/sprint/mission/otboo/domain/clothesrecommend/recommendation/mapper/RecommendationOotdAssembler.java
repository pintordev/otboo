package com.sprint.mission.otboo.domain.clothesrecommend.recommendation.mapper;

import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.entity.ClothesAttributeDefValue;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.repository.ClothesAttributeDefValueRepository;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesAttributeWithDefDto;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.Clothes;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.ClothesAttribute;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.repository.ClothesAttributeRepository;
import com.sprint.mission.otboo.domain.social.feed.dto.OotdDto;
import com.sprint.mission.otboo.global.file.util.FileUrlResolver;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class RecommendationOotdAssembler {

  private final ClothesAttributeRepository clothesAttributeRepository;
  private final ClothesAttributeDefValueRepository clothesAttributeDefValueRepository;
  private final FileUrlResolver fileUrlResolver;

  public List<OotdDto> toOotdDtoList(List<Clothes> selectedClothes) {
    if (selectedClothes.isEmpty()) {
      return List.of();
    }

    List<UUID> clothesIds = selectedClothes.stream()
        .map(Clothes::getId)
        .toList();

    List<ClothesAttribute> allAttributes =
        clothesAttributeRepository.findAllByClothesIdsWithDefinition(clothesIds);
    Map<UUID, List<ClothesAttribute>> attributesByClothesId = allAttributes.stream()
        .collect(Collectors.groupingBy(ClothesAttribute::getClothesId));

    List<UUID> definitionIds = allAttributes.stream()
        .map(attr -> attr.getDefinition().getId())
        .distinct()
        .toList();
    Map<UUID, List<ClothesAttributeDefValue>> defValuesByDefId = definitionIds.isEmpty()
        ? Map.of()
        : clothesAttributeDefValueRepository.findAllByDefinitionIds(definitionIds).stream()
            .collect(Collectors.groupingBy(v -> v.getDefinition().getId()));

    return selectedClothes.stream()
        .map(clothes -> {
          List<ClothesAttribute> attrs = attributesByClothesId
              .getOrDefault(clothes.getId(), List.of());

          List<ClothesAttributeWithDefDto> attrDtos = attrs.stream()
              .map(attr -> {
                List<String> selectableValues = defValuesByDefId
                    .getOrDefault(attr.getDefinition().getId(), List.of())
                    .stream()
                    .map(ClothesAttributeDefValue::getValue)
                    .toList();

                return new ClothesAttributeWithDefDto(
                    attr.getDefinition().getId(),
                    attr.getDefinition().getName(),
                    selectableValues,
                    attr.getValue());
              })
              .toList();

          return new OotdDto(
              clothes.getId(),
              clothes.getName(),
              fileUrlResolver.resolve(clothes.getImageUrl()),
              clothes.getType(),
              attrDtos);
        })
        .toList();
  }
}