package com.sprint.mission.otboo.domain.social.directmessage.mapper;

import com.sprint.mission.otboo.domain.social.common.dto.UserSummary;
import com.sprint.mission.otboo.domain.social.directmessage.dto.DirectMessageDto;
import com.sprint.mission.otboo.domain.social.directmessage.entity.DirectMessage;
import org.springframework.stereotype.Component;

@Component
public class DirectMessageMapper {

  public DirectMessageDto toDto(DirectMessage message, UserSummary sender, UserSummary receiver) {
    return new DirectMessageDto(
        message.getId(),
        message.getCreatedAt(),
        sender,
        receiver,
        message.getContent()
    );
  }
}