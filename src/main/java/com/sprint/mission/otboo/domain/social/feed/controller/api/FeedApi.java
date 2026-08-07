package com.sprint.mission.otboo.domain.social.feed.controller.api;

import com.sprint.mission.otboo.domain.social.feed.dto.CommentCreateRequest;
import com.sprint.mission.otboo.domain.social.feed.dto.CommentDto;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedCommentParams;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedCreateRequest;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedDto;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedListParams;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedUpdateRequest;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.security.details.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
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
  ResponseEntity<CursorPageResponse<FeedDto>> getFeedList(FeedListParams params,
      UserPrincipal principal);

  @Operation(summary = "피드 좋아요", description = "피드 좋아요 API")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "피드 좋아요 성공"),
      @ApiResponse(responseCode = "400", description = "피드 좋아요 실패"),
      @ApiResponse(responseCode = "404", description = "피드 없음")
  })
  ResponseEntity<Void> likeFeed(UUID feedId, UserPrincipal principal);

  @Operation(summary = "피드 좋아요 취소", description = "피드 좋아요 취소 API")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "피드 좋아요 취소 성공"),
      @ApiResponse(responseCode = "400", description = "피드 좋아요 취소 실패"),
      @ApiResponse(responseCode = "404", description = "피드 없음")
  })
  ResponseEntity<Void> unlikeFeed(UUID feedId, UserPrincipal principal);

  @Operation(summary = "피드 댓글 등록", description = "피드 댓글 등록 API")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "피드 댓글 등록 성공"),
      @ApiResponse(responseCode = "400", description = "피드 댓글 등록 실패"),
      @ApiResponse(responseCode = "403", description = "작성자 불일치"),
      @ApiResponse(responseCode = "404", description = "피드 없음")
  })
  ResponseEntity<CommentDto> createFeedComment(
      UUID feedId, CommentCreateRequest request, UserPrincipal principal);

  @Operation(summary = "피드 댓글 조회", description = "피드 댓글 조회 API")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "피드 댓글 조회 성공"),
      @ApiResponse(responseCode = "400", description = "피드 댓글 조회 실패"),
      @ApiResponse(responseCode = "404", description = "피드 없음")
  })
  ResponseEntity<CursorPageResponse<CommentDto>> getFeedComments(
      UUID feedId, FeedCommentParams params);

  @Operation(summary = "피드 수정", description = "피드 수정 API")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "피드 수정 성공"),
      @ApiResponse(responseCode = "400", description = "피드 수정 실패"),
      @ApiResponse(responseCode = "403", description = "작성자 불일치"),
      @ApiResponse(responseCode = "404", description = "피드 없음")
  })
  ResponseEntity<FeedDto> updateFeed(
      UUID feedId, FeedUpdateRequest request, UserPrincipal principal);

  @Operation(summary = "피드 삭제", description = "피드 삭제 API")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "피드 삭제 성공"),
      @ApiResponse(responseCode = "400", description = "피드 삭제 실패"),
      @ApiResponse(responseCode = "403", description = "작성자 불일치"),
      @ApiResponse(responseCode = "404", description = "피드 없음")
  })
  ResponseEntity<Void> deleteFeed(UUID feedId, UserPrincipal principal);
}