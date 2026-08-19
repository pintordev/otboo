package com.sprint.mission.otboo.external.llm;

import com.sprint.mission.otboo.external.llm.dto.LlmExtractionRequest;
import com.sprint.mission.otboo.external.llm.dto.LlmExtractionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "llmClient", url = "${external.llm.base-url:https://openrouter.ai/api/v1}", configuration = LlmFeignConfig.class)
public interface LlmClient {

  @PostMapping("/chat/completions")
  LlmExtractionResponse extract(@RequestBody LlmExtractionRequest request);
}
