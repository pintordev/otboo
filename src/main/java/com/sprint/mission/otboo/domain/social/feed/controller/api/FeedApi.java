package com.sprint.mission.otboo.domain.social.feed.controller.api;

import com.sprint.mission.otboo.domain.social.feed.dto.FeedCreateRequest;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedDto;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedListParams;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.security.jwt.filter.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "피드 관리", description = "피드 관련 API")
public interface FeedApi {

  @Operation(summary = "피드 등록", description = "피드 등록 API")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "피드 등록 성공"),
      @ApiResponse(responseCode = "400", description = "피드 등록 실패"),
      @ApiResponse(responseCode = "403", description = "작성자 불일치")
  })
  ResponseEntity<FeedDto> createFeed(FeedCreateRequest request, UserPrincipal principal);

  @Operation(summary = "피드 목록 조회", description = "피드 목록 조회 API")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "피드 목록 조회 성공"),
      @ApiResponse(responseCode = "400", description = "피드 목록 조회 실패")
  })
  ResponseEntity<CursorPageResponse<FeedDto>> getFeedList(FeedListParams params);
}