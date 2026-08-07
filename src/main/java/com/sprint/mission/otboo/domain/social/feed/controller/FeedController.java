package com.sprint.mission.otboo.domain.social.feed.controller;

import com.sprint.mission.otboo.domain.social.feed.controller.api.FeedApi;
import com.sprint.mission.otboo.domain.social.feed.dto.CommentCreateRequest;
import com.sprint.mission.otboo.domain.social.feed.dto.CommentDto;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedCommentParams;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedCreateRequest;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedDto;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedListParams;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedUpdateRequest;
import com.sprint.mission.otboo.domain.social.feed.service.CommentService;
import com.sprint.mission.otboo.domain.social.feed.service.FeedService;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.security.details.CurrentUser;
import com.sprint.mission.otboo.security.details.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/feeds")
@RestController
public class FeedController implements FeedApi {

  private final FeedService feedService;
  private final CommentService commentService;

  @PostMapping
  @Override
  public ResponseEntity<FeedDto> createFeed(
      @Valid @RequestBody FeedCreateRequest request,
      @CurrentUser UserPrincipal principal) {
    log.debug("피드 등록 요청: userId={}", principal.userId());
    FeedDto created = feedService.create(request, principal.userId());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping
  @Override
  public ResponseEntity<CursorPageResponse<FeedDto>> getFeedList(
      @Valid @ModelAttribute FeedListParams params,
      @CurrentUser UserPrincipal principal) {
    log.debug("피드 목록 조회 요청: limit={}, sortBy={}", params.limit(), params.sortBy());
    CursorPageResponse<FeedDto> result = feedService.getFeeds(params, principal.userId());
    return ResponseEntity.ok(result);
  }

  @PostMapping("/{feedId}/like")
  @Override
  public ResponseEntity<Void> likeFeed(
      @PathVariable UUID feedId,
      @CurrentUser UserPrincipal principal) {
    feedService.like(feedId, principal.userId());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{feedId}/like")
  @Override
  public ResponseEntity<Void> unlikeFeed(
      @PathVariable UUID feedId,
      @CurrentUser UserPrincipal principal) {
    feedService.unlike(feedId, principal.userId());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{feedId}/comments")
  @Override
  public ResponseEntity<CommentDto> createFeedComment(
      @PathVariable UUID feedId,
      @Valid @RequestBody CommentCreateRequest request,
      @CurrentUser UserPrincipal principal) {
    CommentDto response = commentService.create(feedId, request, principal.userId());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/{feedId}/comments")
  @Override
  public ResponseEntity<CursorPageResponse<CommentDto>> getFeedComments(
      @PathVariable UUID feedId,
      @Valid @ModelAttribute FeedCommentParams params) {
    CursorPageResponse<CommentDto> response = commentService.getComments(feedId, params);
    return ResponseEntity.ok(response);
  }

  @PatchMapping("/{feedId}")
  @Override
  public ResponseEntity<FeedDto> updateFeed(
      @PathVariable UUID feedId,
      @Valid @RequestBody FeedUpdateRequest request,
      @CurrentUser UserPrincipal principal) {
    FeedDto response = feedService.update(feedId, request, principal.userId());
    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{feedId}")
  @Override
  public ResponseEntity<Void> deleteFeed(
      @PathVariable UUID feedId,
      @CurrentUser UserPrincipal principal) {
    feedService.delete(feedId, principal.userId());
    return ResponseEntity.noContent().build();
  }
}
