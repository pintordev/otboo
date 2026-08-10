package com.sprint.mission.otboo.domain.social.directmessage.dto;

import com.sprint.mission.otboo.domain.social.common.dto.UserSummary;
import java.time.Instant;
import java.util.UUID;

public record DirectMessageDto(
    UUID id,
    Instant createdAt,
    UserSummary sender,
    UserSummary receiver,
    String content
) {

}