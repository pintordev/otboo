package com.sprint.mission.otboo.domain.authuser.user.dto.request;

import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import com.sprint.mission.otboo.global.dto.SortDirection;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.util.StringUtils;

public record UserListParams(

    String cursor,

    UUID idAfter,

    @Min(value = 1, message = "limit은 1 이상이어야 합니다.")
    @Max(value = 100, message = "limit은 100을 넘을 수 없습니다.")
    Integer limit,

    @Pattern(regexp = "^(email|createdAt)$", message = "sortBy는 email 또는 createdAt만 가능합니다.")
    String sortBy,

    SortDirection sortDirection,

    @Size(max = 255, message = "emailLike는 255자를 넘을 수 없습니다.")
    String emailLike,

    Role roleEqual,

    Boolean locked
) {

  private static final int DEFAULT_LIMIT = 10;
  private static final String DEFAULT_SORT_BY = "email";
  private static final SortDirection DEFAULT_SORT_DIRECTION = SortDirection.ASCENDING;

  public UserListParams {
    if (limit == null) {
      limit = DEFAULT_LIMIT;
    }
    if (!StringUtils.hasText(sortBy)) {
      sortBy = DEFAULT_SORT_BY;
    }
    if (sortDirection == null) {
      sortDirection = DEFAULT_SORT_DIRECTION;
    }
  }

  @AssertTrue(message = "cursor와 idAfter는 함께 제공되거나 함께 생략되어야 합니다.")
  public boolean isCursorPairValid() {
    return (cursor == null) == (idAfter == null);
  }

  @AssertTrue(message = "createdAt 기준 커서는 Instant로 파싱 가능한 값이어야 합니다.")
  public boolean isCursorFormatValidForSortBy() {
    if (cursor == null || !"createdAt".equals(sortBy)) {
      return true;
    }
    try {
      Instant.parse(cursor);
      return true;
    } catch (DateTimeParseException e) {
      return false;
    }
  }
}
