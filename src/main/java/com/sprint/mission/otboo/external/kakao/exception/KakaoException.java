package com.sprint.mission.otboo.external.kakao.exception;

import com.sprint.mission.otboo.global.exception.OtbooException;
import java.util.Map;
import org.springframework.http.HttpStatus;

public abstract class KakaoException extends OtbooException {

  protected KakaoException(HttpStatus status, String message, Map<String, Object> details) {
    super(status, message, details);
  }
}