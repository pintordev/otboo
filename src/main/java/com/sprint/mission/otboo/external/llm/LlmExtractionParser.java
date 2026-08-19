package com.sprint.mission.otboo.external.llm;

import com.sprint.mission.otboo.external.llm.dto.LlmExtractedClothesInfo;
import com.sprint.mission.otboo.external.llm.dto.LlmExtractionResponse;
import com.sprint.mission.otboo.external.llm.exception.LlmApiException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class LlmExtractionParser {

  private final ObjectMapper objectMapper;

  public LlmExtractionParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public LlmExtractedClothesInfo parse(LlmExtractionResponse response) {
    if (response == null || response.choices() == null || response.choices().isEmpty()) {
      throw LlmApiException.parseFailed();
    }
    String content = response.getContent();
    if (content == null || content.isBlank()) {
      throw LlmApiException.parseFailed();
    }

    try {
      String json = stripCodeFence(content);
      JsonNode node = objectMapper.readTree(json);
      String name = node.has("name") ? node.get("name").asString(null) : null;
      String imageUrl = node.has("imageUrl") ? node.get("imageUrl").asString(null) : null;
      return new LlmExtractedClothesInfo(name, imageUrl);
    } catch (Exception e) {
      throw LlmApiException.parseFailed();
    }
  }

  private String stripCodeFence(String content) {
    String stripped = content.strip();
    if (stripped.startsWith("```")) {
      stripped = stripped.replaceFirst("```\\w*\\n?", "");
      if (stripped.endsWith("```")) {
        stripped = stripped.substring(0, stripped.lastIndexOf("```"));
      }
    }
    return stripped.strip();
  }
}
