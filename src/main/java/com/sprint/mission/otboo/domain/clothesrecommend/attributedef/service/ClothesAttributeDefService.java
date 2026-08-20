package com.sprint.mission.otboo.domain.clothesrecommend.attributedef.service;

import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.dto.AttributeDefSortBy;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.dto.ClothesAttributeDefCreateRequest;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.dto.ClothesAttributeDefDto;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.dto.ClothesAttributeDefListParams;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.dto.ClothesAttributeDefUpdateRequest;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.entity.ClothesAttributeDef;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.entity.ClothesAttributeDefValue;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.exception.ClothesAttributeDefNameDuplicatedException;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.exception.ClothesAttributeDefNotFoundException;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.exception.ClothesAttributeDefValueInvalidException;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.mapper.ClothesAttributeDefMapper;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.repository.ClothesAttributeDefRepository;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.repository.ClothesAttributeDefValueRepository;
import com.sprint.mission.otboo.global.dto.SortDirection;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class ClothesAttributeDefService {

  private final ClothesAttributeDefRepository clothesAttributeDefRepository;
  private final ClothesAttributeDefValueRepository clothesAttributeDefValueRepository;
  private final ClothesAttributeDefMapper clothesAttributeDefMapper;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public ClothesAttributeDefDto create(ClothesAttributeDefCreateRequest request) {
    String name = request.name().trim();
    List<String> selectableValues = validateSelectableValues(request.selectableValues());

    if (clothesAttributeDefRepository.existsByName(name)) {
      throw ClothesAttributeDefNameDuplicatedException.withName(name);
    }

    ClothesAttributeDef definition;
    try {
      definition = clothesAttributeDefRepository.saveAndFlush(
          ClothesAttributeDef.create(name));
    } catch (DataIntegrityViolationException e) {
      if (e.getMessage() != null && e.getMessage().contains("uq_clothes_attribute_defs_name")) {
        throw ClothesAttributeDefNameDuplicatedException.withName(name);
      }
      throw e;
    }

    List<ClothesAttributeDefValue> values =
        IntStream.range(0, selectableValues.size())
            .mapToObj(i ->
                ClothesAttributeDefValue.create(definition, selectableValues.get(i), i))
            .toList();
    List<ClothesAttributeDefValue> savedValues =
        clothesAttributeDefValueRepository.saveAll(values);

    List<UUID> receiverIds = userRepository.findAllIds();
    if (!receiverIds.isEmpty()) {
      eventPublisher.publishEvent(new NotificationRequestedEvent(
          Set.copyOf(receiverIds), "의상 속성 추가",
          name + " 의상 속성이 새로 추가되었습니다.", NotificationLevel.INFO));
    }

    log.info("의상 속성 정의 등록 완료 definitionId={}, name={}", definition.getId(), name);
    return clothesAttributeDefMapper.toDto(definition, savedValues);
  }

  private List<String> validateSelectableValues(List<String> rawValues) {
    List<String> trimmed = rawValues.stream()
        .map(String::trim)
        .filter(v -> !v.isEmpty())
        .toList();

    if (trimmed.isEmpty()) {
      throw ClothesAttributeDefValueInvalidException.empty();
    }
    if (new LinkedHashSet<>(trimmed).size() != trimmed.size()) {
      throw ClothesAttributeDefValueInvalidException.duplicated(trimmed);
    }
    return trimmed;
  }

  public List<ClothesAttributeDefDto> getAll(ClothesAttributeDefListParams params) {
    Sort sort = toSort(params);
    String keyword = params.keywordLike();

    List<ClothesAttributeDef> definitions =
        (keyword == null || keyword.isBlank())
            ? clothesAttributeDefRepository.findAll(sort)
            : clothesAttributeDefRepository.findAllByNameContainingIgnoreCase(
                keyword.trim(), sort);

    if (definitions.isEmpty()) {
      return List.of();
    }

    List<UUID> definitionIds = definitions.stream()
        .map(ClothesAttributeDef::getId)
        .toList();
    Map<UUID, List<ClothesAttributeDefValue>> valuesByDefId =
        clothesAttributeDefValueRepository.findAllByDefinitionIds(definitionIds).stream()
            .collect(Collectors.groupingBy(v -> v.getDefinition().getId()));

    return definitions.stream()
        .map(def -> clothesAttributeDefMapper.toDto(
            def, valuesByDefId.getOrDefault(def.getId(), List.of())))
        .toList();
  }

  private Sort toSort(ClothesAttributeDefListParams params) {
    String property = params.sortBy() == AttributeDefSortBy.NAME ? "name" : "createdAt";
    Sort.Direction direction =
        params.sortDirection() == SortDirection.DESCENDING
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;
    return Sort.by(direction, property);
  }

  @Transactional
  public ClothesAttributeDefDto update(UUID definitionId,
      ClothesAttributeDefUpdateRequest request) {
    ClothesAttributeDef definition = clothesAttributeDefRepository.findById(definitionId)
        .orElseThrow(() -> ClothesAttributeDefNotFoundException.withId(definitionId));

    String newName = request.name().trim();
    if (!definition.getName().equals(newName)
        && clothesAttributeDefRepository.existsByName(newName)) {
      throw ClothesAttributeDefNameDuplicatedException.withName(newName);
    }
    definition.changeName(newName);

    try {
      clothesAttributeDefRepository.saveAndFlush(definition);
    } catch (DataIntegrityViolationException e) {
      if (e.getMessage() != null
          && e.getMessage().contains("uq_clothes_attribute_defs_name")) {
        throw ClothesAttributeDefNameDuplicatedException.withName(newName);
      }
      throw e;
    }

    List<String> newValues = validateSelectableValues(request.selectableValues());
    clothesAttributeDefValueRepository.deleteAllByDefinitionId(definitionId);

    List<ClothesAttributeDefValue> values =
        IntStream.range(0, newValues.size())
            .mapToObj(i -> ClothesAttributeDefValue.create(definition, newValues.get(i), i))
            .toList();
    List<ClothesAttributeDefValue> savedValues =
        clothesAttributeDefValueRepository.saveAll(values);

    return clothesAttributeDefMapper.toDto(definition, savedValues);
  }

  @Transactional
  public void delete(UUID definitionId) {
    ClothesAttributeDef definition = clothesAttributeDefRepository.findById(definitionId)
        .orElseThrow(() -> ClothesAttributeDefNotFoundException.withId(definitionId));

    clothesAttributeDefValueRepository.deleteAllByDefinitionId(definitionId);
    clothesAttributeDefRepository.delete(definition);

    log.info("의상 속성 정의 삭제 완료 definitionId={}", definitionId);
  }
}