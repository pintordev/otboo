package com.sprint.mission.otboo.domain.clothesrecommend.attributedef.exception;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class ClothesAttributeDefValueInvalidException extends AttributeDefException {

  private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;

  private ClothesAttributeDefValueInvalidException(String message, Map<String, Object> details) {
    super(STATUS, message, details);
  }

  public static ClothesAttributeDefValueInvalidException empty() {
    return new ClothesAttributeDefValueInvalidException(
        "선택 가능한 값을 1개 이상 입력해야 합니다.", Map.of());
  }

  public static ClothesAttributeDefValueInvalidException duplicated(List<String> selectableValues) {
    return new ClothesAttributeDefValueInvalidException(
        "선택 가능한 값은 중복될 수 없습니다.", Map.of("selectableValues", selectableValues));
  }
}
