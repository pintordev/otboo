package com.sprint.mission.otboo.domain.clothesrecommend.clothes.service;

import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.entity.ClothesAttributeDef;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.entity.ClothesAttributeDefValue;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.exception.ClothesAttributeDefNotFoundException;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.repository.ClothesAttributeDefRepository;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.repository.ClothesAttributeDefValueRepository;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesAttributeDto;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesCreateRequest;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesDto;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesListParams;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.Clothes;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.ClothesAttribute;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.exception.ClothesNotFoundException;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.mapper.ClothesMapper;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.repository.ClothesAttributeRepository;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.repository.ClothesRepository;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.dto.SortDirection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesUpdateRequest;


@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class ClothesService {

  private final ClothesRepository clothesRepository;
  private final ClothesAttributeRepository clothesAttributeRepository;
  private final ClothesAttributeDefRepository clothesAttributeDefRepository;
  private final ClothesAttributeDefValueRepository clothesAttributeDefValueRepository;
  private final ClothesMapper clothesMapper;

  @Transactional
  public ClothesDto create(ClothesCreateRequest request, MultipartFile image) {
    Clothes clothes = clothesRepository.save(
        Clothes.create(request.ownerId(), request.name(), request.type()));

    if (image != null && !image.isEmpty()) {
      // TODO: S3 연동 후 이미지 업로드 구현
      log.info("이미지 파일 수신됨 (아직 저장 미구현) size={}", image.getSize());
    }

    List<ClothesAttribute> savedAttributes = saveAttributes(clothes.getId(),
        request.attributes());

    Map<UUID, List<ClothesAttributeDefValue>> defValuesByDefId =
        loadDefValues(savedAttributes);

    log.info("의상 등록 완료 clothesId={}, ownerId={}", clothes.getId(), request.ownerId());
    return clothesMapper.toDto(clothes, savedAttributes, defValuesByDefId);
  }

  public CursorPageResponse<ClothesDto> getClothes(ClothesListParams params) {
    CursorPageResponse<Clothes> page = clothesRepository.findClothes(params);

    if (page.data().isEmpty()) {
      return new CursorPageResponse<>(
          List.of(), null, null, false, page.totalCount(),
          "createdAt", SortDirection.DESCENDING);
    }

    List<UUID> clothesIds = page.data().stream()
        .map(Clothes::getId)
        .toList();

    List<ClothesAttribute> allAttributes =
        clothesAttributeRepository.findAllByClothesIdsWithDefinition(clothesIds);
    Map<UUID, List<ClothesAttribute>> attributesByClothesId = allAttributes.stream()
        .collect(Collectors.groupingBy(ClothesAttribute::getClothesId));

    Map<UUID, List<ClothesAttributeDefValue>> defValuesByDefId =
        loadDefValues(allAttributes);

    List<ClothesDto> data = page.data().stream()
        .map(clothes -> clothesMapper.toDto(
            clothes,
            attributesByClothesId.getOrDefault(clothes.getId(), List.of()),
            defValuesByDefId))
        .toList();

    return new CursorPageResponse<>(
        data, page.nextCursor(), page.nextIdAfter(), page.hasNext(),
        page.totalCount(), page.sortBy(), page.sortDirection());
  }

  public List<ClothesDto> getClothesByIds(List<UUID> clothesIds) {
    List<Clothes> clothesList = clothesRepository.findAllById(clothesIds).stream()
        .filter(c -> !c.isDeleted())
        .toList();

    if (clothesList.isEmpty()) {
      return List.of();
    }

    List<UUID> foundIds = clothesList.stream().map(Clothes::getId).toList();

    List<ClothesAttribute> allAttributes =
        clothesAttributeRepository.findAllByClothesIdsWithDefinition(foundIds);
    Map<UUID, List<ClothesAttribute>> attributesByClothesId = allAttributes.stream()
        .collect(Collectors.groupingBy(ClothesAttribute::getClothesId));

    Map<UUID, List<ClothesAttributeDefValue>> defValuesByDefId =
        loadDefValues(allAttributes);

    return clothesList.stream()
        .map(clothes -> clothesMapper.toDto(
            clothes,
            attributesByClothesId.getOrDefault(clothes.getId(), List.of()),
            defValuesByDefId))
        .toList();
  }


  @Transactional
  public ClothesDto update(UUID clothesId, ClothesUpdateRequest request, MultipartFile image) {
    Clothes clothes = clothesRepository.findById(clothesId)
        .filter(c -> !c.isDeleted())
        .orElseThrow(() -> ClothesNotFoundException.withId(clothesId));

    if (request.name() != null) {
      clothes.changeName(request.name());
    }
    if (request.type() != null) {
      clothes.changeType(request.type());
    }

    if (image != null && !image.isEmpty()) {
      // TODO: S3 연동 후 이미지 업로드 구현
      log.info("이미지 파일 수신됨 (아직 저장 미구현) size={}", image.getSize());;
    }

    List<ClothesAttribute> savedAttributes;
    if (request.attributes() != null) {
      clothesAttributeRepository.deleteAllByClothesId(clothesId);
      savedAttributes = saveAttributes(clothesId, request.attributes());
    } else {
      savedAttributes = clothesAttributeRepository
          .findAllByClothesIdWithDefinition(clothesId);
    }

    Map<UUID, List<ClothesAttributeDefValue>> defValuesByDefId =
        loadDefValues(savedAttributes);

    log.info("의상 수정 완료 clothesId={}", clothesId);
    return clothesMapper.toDto(clothes, savedAttributes, defValuesByDefId);
  }

  @Transactional
  public void delete(UUID clothesId) {
    Clothes clothes = clothesRepository.findById(clothesId)
        .filter(c -> !c.isDeleted())
        .orElseThrow(() -> ClothesNotFoundException.withId(clothesId));

    clothes.delete();
    log.info("의상 삭제 완료 clothesId={}", clothesId);
  }

  private List<ClothesAttribute> saveAttributes(UUID clothesId,
      List<ClothesAttributeDto> attributeDtos) {
    if (attributeDtos == null || attributeDtos.isEmpty()) {
      return List.of();
    }

    validateNoDuplicateDefinitionIds(attributeDtos);

    List<UUID> definitionIds = attributeDtos.stream()
        .map(ClothesAttributeDto::definitionId)
        .toList();
    Map<UUID, ClothesAttributeDef> definitionsById =
        clothesAttributeDefRepository.findAllById(definitionIds).stream()
            .collect(Collectors.toMap(ClothesAttributeDef::getId, def -> def));

    List<ClothesAttribute> attributes = attributeDtos.stream()
        .map(dto -> {
          ClothesAttributeDef definition = definitionsById.get(dto.definitionId());
          if (definition == null) {
            throw ClothesAttributeDefNotFoundException.withId(dto.definitionId());
          }
          return ClothesAttribute.create(clothesId, definition, dto.value());
        })
        .toList();

    return clothesAttributeRepository.saveAll(attributes);
  }

  private void validateNoDuplicateDefinitionIds(List<ClothesAttributeDto> attributeDtos) {
    Set<UUID> seen = new HashSet<>();
    for (ClothesAttributeDto dto : attributeDtos) {
      if (!seen.add(dto.definitionId())) {
        throw new IllegalArgumentException(
            "중복된 속성 정의입니다. definitionId=" + dto.definitionId());
      }
    }
  }

  private Map<UUID, List<ClothesAttributeDefValue>> loadDefValues(
      List<ClothesAttribute> attributes) {
    if (attributes.isEmpty()) {
      return Map.of();
    }

    List<UUID> definitionIds = attributes.stream()
        .map(attr -> attr.getDefinition().getId())
        .distinct()
        .toList();

    return clothesAttributeDefValueRepository.findAllByDefinitionIds(definitionIds).stream()
        .collect(Collectors.groupingBy(v -> v.getDefinition().getId()));
  }
}