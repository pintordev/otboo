package com.sprint.mission.otboo.domain.social.feed.controller;

import com.sprint.mission.otboo.domain.social.feed.controller.api.FeedApi;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedCreateRequest;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedDto;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedListParams;
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
  public ResponseEntity<Void> likeFeed(
      @PathVariable UUID feedId,
      @CurrentUser UserPrincipal principal) {
    feedService.like(feedId, principal.userId());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{feedId}/like")
  public ResponseEntity<Void> unlikeFeed(
      @PathVariable UUID feedId,
      @CurrentUser UserPrincipal principal) {
    feedService.unlike(feedId, principal.userId());
    return ResponseEntity.noContent().build();
  }
}
