package com.sprint.mission.otboo.external.purchase.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class PurchasePageFetchException extends PurchasePageException {

  private static final String MESSAGE = "구매 페이지를 불러올 수 없습니다.";

  private PurchasePageFetchException(Map<String, Object> details) {
    super(HttpStatus.BAD_GATEWAY, MESSAGE, details);
  }

  public static PurchasePageFetchException wrap(Throwable cause) {
    PurchasePageFetchException exception = new PurchasePageFetchException(Map.of());
    exception.initCause(cause);
    return exception;
  }
}
