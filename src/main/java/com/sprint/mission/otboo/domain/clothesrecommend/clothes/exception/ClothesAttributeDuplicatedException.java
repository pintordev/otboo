package com.sprint.mission.otboo.domain.clothesrecommend.clothes.exception;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class ClothesAttributeDuplicatedException extends ClothesException {

  private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;
  private static final String MESSAGE = "중복된 속성 정의입니다.";

  private ClothesAttributeDuplicatedException(Map<String, Object> details) {
    super(STATUS, MESSAGE, details);
  }

  public static ClothesAttributeDuplicatedException withDefinitionId(UUID definitionId) {
    return new ClothesAttributeDuplicatedException(Map.of("definitionId", definitionId));
  }
}
