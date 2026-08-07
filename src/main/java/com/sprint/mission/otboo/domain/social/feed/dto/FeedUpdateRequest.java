package com.sprint.mission.otboo.domain.social.feed.dto;

import jakarta.validation.constraints.NotBlank;

public record FeedUpdateRequest(
    @NotBlank String content
) {

}