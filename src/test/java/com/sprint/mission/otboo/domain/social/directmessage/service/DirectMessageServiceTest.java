package com.sprint.mission.otboo.domain.social.directmessage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.domain.social.common.dto.UserSummary;
import com.sprint.mission.otboo.domain.social.common.repository.querydsl.UserSummaryQueryRepository;
import com.sprint.mission.otboo.domain.social.directmessage.dto.DirectMessageDto;
import com.sprint.mission.otboo.domain.social.directmessage.dto.DirectMessageParams;
import com.sprint.mission.otboo.domain.social.directmessage.entity.DirectMessage;
import com.sprint.mission.otboo.domain.social.directmessage.mapper.DirectMessageMapper;
import com.sprint.mission.otboo.domain.social.directmessage.repository.DirectMessageRepository;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.dto.SortDirection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DirectMessageService")
class DirectMessageServiceTest {

  @InjectMocks
  DirectMessageService directMessageService;

  @Mock
  DirectMessageRepository directMessageRepository;

  @Mock
  DirectMessageMapper directMessageMapper;

  @Mock
  UserSummaryQueryRepository userSummaryQueryRepository;

  @Nested
  @DisplayName("DM 목록 조회")
  class GetDirectMessages {

    @Test
    @DisplayName("Repository가 준 페이지를 DirectMessageDto로 변환해 반환한다")
    void Repository가_준_페이지를_DirectMessageDto로_변환해_반환한다() {
      // given
      UUID me = UUID.randomUUID();
      UUID other = UUID.randomUUID();
      DirectMessageParams params = new DirectMessageParams(other, null, null, 10);

      DirectMessage message = DirectMessage.create(me, other, "안녕하세요?");
      CursorPageResponse<DirectMessage> repoPage = new CursorPageResponse<>(
          List.of(message), null, null, false, 1L, "createdAt", SortDirection.DESCENDING);
      given(directMessageRepository.findDirectMessages(me, params)).willReturn(repoPage);

      UserSummary sender = new UserSummary(me, "나", null);
      UserSummary receiver = new UserSummary(other, "상대", null);
      given(userSummaryQueryRepository.findByUserIds(List.of(me, other)))
          .willReturn(List.of(sender, receiver));

      DirectMessageDto dto = new DirectMessageDto(
          message.getId(), null, sender, receiver, "안녕하세요?");
      given(directMessageMapper.toDto(message, sender, receiver)).willReturn(dto);

      // when
      CursorPageResponse<DirectMessageDto> result =
          directMessageService.getDirectMessages(me, params);

      // then
      assertThat(result.data()).containsExactly(dto);
      assertThat(result.totalCount()).isEqualTo(1L);
      assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("메시지가 없으면 빈 페이지를 반환하고 사용자 배치 조회를 하지 않는다")
    void 메시지가_없으면_빈_페이지를_반환하고_사용자_배치_조회를_하지_않는다() {
      // given
      UUID me = UUID.randomUUID();
      UUID other = UUID.randomUUID();
      DirectMessageParams params = new DirectMessageParams(other, null, null, 10);

      CursorPageResponse<DirectMessage> emptyPage = new CursorPageResponse<>(
          List.of(), null, null, false, 0L, "createdAt", SortDirection.DESCENDING);
      given(directMessageRepository.findDirectMessages(me, params)).willReturn(emptyPage);

      // when
      CursorPageResponse<DirectMessageDto> result =
          directMessageService.getDirectMessages(me, params);

      // then
      assertThat(result.data()).isEmpty();
      assertThat(result.totalCount()).isZero();
      verify(userSummaryQueryRepository, never()).findByUserIds(any());
    }
  }
}