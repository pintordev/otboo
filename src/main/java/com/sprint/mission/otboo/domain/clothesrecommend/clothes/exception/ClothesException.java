package com.sprint.mission.otboo.domain.clothesrecommend.clothes.exception;

import com.sprint.mission.otboo.global.exception.OtbooException;
import java.util.Map;
import org.springframework.http.HttpStatus;

public abstract class ClothesException extends OtbooException {

  protected ClothesException(HttpStatus status, String message, Map<String, Object> details) {
    super(status, message, details);
  }

  protected ClothesException(HttpStatus status, String message, Map<String, Object> details,
      Throwable cause) {
    super(status, message, details, cause);
  }
}
