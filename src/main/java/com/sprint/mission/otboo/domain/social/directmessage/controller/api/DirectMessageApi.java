package com.sprint.mission.otboo.domain.social.directmessage.controller.api;

import com.sprint.mission.otboo.domain.social.directmessage.dto.DirectMessageDto;
import com.sprint.mission.otboo.domain.social.directmessage.dto.DirectMessageParams;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.security.details.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "DirectMessage", description = "DirectMessage API")
public interface DirectMessageApi {

  @Operation(summary = "DM 목록 조회", description = "DM 목록 조회 API")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "DM 목록 조회 성공"),
      @ApiResponse(responseCode = "400", description = "DM 목록 조회 실패")
  })
  ResponseEntity<CursorPageResponse<DirectMessageDto>> getDms(
      DirectMessageParams params, UserPrincipal principal);
}