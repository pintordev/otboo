package com.sprint.mission.otboo.domain.social.directmessage.dto;

import com.sprint.mission.otboo.domain.social.common.dto.CursorListParams;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record DirectMessageParams(
    @NotNull UUID userId,
    String cursor,
    UUID idAfter,
    @NotNull @Min(1) @Max(100) Integer limit
) implements CursorListParams {

}