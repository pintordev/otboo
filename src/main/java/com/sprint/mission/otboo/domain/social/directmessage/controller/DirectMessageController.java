package com.sprint.mission.otboo.domain.social.directmessage.controller;

import com.sprint.mission.otboo.domain.social.directmessage.controller.api.DirectMessageApi;
import com.sprint.mission.otboo.domain.social.directmessage.dto.DirectMessageDto;
import com.sprint.mission.otboo.domain.social.directmessage.dto.DirectMessageParams;
import com.sprint.mission.otboo.domain.social.directmessage.service.DirectMessageService;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.security.details.CurrentUser;
import com.sprint.mission.otboo.security.details.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/direct-messages")
@RequiredArgsConstructor
public class DirectMessageController implements DirectMessageApi {

  private final DirectMessageService directMessageService;

  @GetMapping
  @Override
  public ResponseEntity<CursorPageResponse<DirectMessageDto>> getDms(
      @Valid @ModelAttribute DirectMessageParams params,
      @CurrentUser UserPrincipal principal) {
    CursorPageResponse<DirectMessageDto> response =
        directMessageService.getDirectMessages(principal.userId(), params);
    return ResponseEntity.ok(response);
  }
}