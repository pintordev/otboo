package com.sprint.mission.otboo.external.purchase.exception;

import com.sprint.mission.otboo.global.exception.OtbooException;
import java.util.Map;
import org.springframework.http.HttpStatus;

public abstract class PurchasePageException extends OtbooException {

  protected PurchasePageException(HttpStatus status, String message, Map<String, Object> details) {
    super(status, message, details);
  }
}
