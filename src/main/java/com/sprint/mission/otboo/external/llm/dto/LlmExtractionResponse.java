package com.sprint.mission.otboo.external.llm.dto;

import java.util.List;

public record LlmExtractionResponse(
    List<Choice> choices
) {

  public record Choice(
      Message message
  ) {

  }

  public record Message(
      String role,
      String content
  ) {

  }

  public String getContent() {
    if (choices == null || choices.isEmpty()) {
      return null;
    }
    return choices.get(0).message().content();
  }
}
