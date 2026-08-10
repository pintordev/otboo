package com.sprint.mission.otboo.domain.social.directmessage.repository.querydsl;

import com.sprint.mission.otboo.domain.social.directmessage.dto.DirectMessageParams;
import com.sprint.mission.otboo.domain.social.directmessage.entity.DirectMessage;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import java.util.UUID;

public interface DirectMessageCustomRepository {

  CursorPageResponse<DirectMessage> findDirectMessages(UUID currentUserId,
      DirectMessageParams params);
}