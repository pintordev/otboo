package com.sprint.mission.otboo.external.llm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record LlmExtractionRequest(
    @NotBlank String model,
    @NotEmpty List<@Valid LlmMessage> messages
) {

  public record LlmMessage(
      @NotBlank String role,
      @NotBlank String content
  ) {

  }
}
