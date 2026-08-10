package com.sprint.mission.otboo.domain.social.directmessage.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.FieldReflectionArbitraryIntrospector;
import com.sprint.mission.otboo.domain.social.common.dto.UserSummary;
import com.sprint.mission.otboo.domain.social.directmessage.dto.DirectMessageDto;
import com.sprint.mission.otboo.domain.social.directmessage.entity.DirectMessage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DirectMessageMapper")
class DirectMessageMapperTest {

  static final FixtureMonkey fm = FixtureMonkey.builder()
      .objectIntrospector(FieldReflectionArbitraryIntrospector.INSTANCE)
      .build();

  DirectMessageMapper directMessageMapper = new DirectMessageMapper();

  @Nested
  @DisplayName("toDto 변환")
  class ToDto {

    @Test
    @DisplayName("DirectMessage와 sender, receiver로 DirectMessageDto를 반환한다")
    void DirectMessage와_sender_receiver로_DirectMessageDto를_반환한다() {
      // given
      UUID messageId = UUID.randomUUID();
      Instant createdAt = Instant.parse("2026-08-07T08:00:00Z");
      UUID senderId = UUID.randomUUID();
      UUID receiverId = UUID.randomUUID();
      DirectMessage message = fm.giveMeBuilder(DirectMessage.class)
          .set("id", messageId)
          .set("createdAt", createdAt)
          .set("senderId", senderId)
          .set("receiverId", receiverId)
          .set("content", "안녕하세요?")
          .sample();

      UserSummary sender = new UserSummary(senderId, "보낸사람", null);
      UserSummary receiver = new UserSummary(receiverId, "받는사람", null);

      // when
      DirectMessageDto dto = directMessageMapper.toDto(message, sender, receiver);

      // then
      assertThat(dto.id()).isEqualTo(messageId);
      assertThat(dto.createdAt()).isEqualTo(createdAt);
      assertThat(dto.sender()).isEqualTo(sender);
      assertThat(dto.receiver()).isEqualTo(receiver);
      assertThat(dto.content()).isEqualTo("안녕하세요?");
    }
  }
}