package com.sprint.mission.otboo.domain.social.feed.controller;

import com.sprint.mission.otboo.domain.social.feed.controller.api.FeedApi;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedCreateRequest;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedDto;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedListParams;
import com.sprint.mission.otboo.domain.social.feed.service.FeedService;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.security.jwt.filter.CurrentUser;
import com.sprint.mission.otboo.global.security.jwt.filter.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
      @Valid @ModelAttribute FeedListParams params) {
    log.debug("피드 목록 조회 요청: limit={}, sortBy={}", params.limit(), params.sortBy());
    CursorPageResponse<FeedDto> result = feedService.getFeeds(params);
    return ResponseEntity.ok(result);
  }
}