package com.sprint.mission.otboo.security.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.security.details.UserPrincipal;
import com.sprint.mission.otboo.security.token.dto.AccessTokenClaims;
import com.sprint.mission.otboo.security.token.exception.business.ExpiredTokenException;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import com.sprint.mission.otboo.security.usersession.exception.business.UserSessionExpiredException;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

@ExtendWith(MockitoExtension.class)
@DisplayName("StompAuthChannelInterceptor")
class StompAuthChannelInterceptorTest {

  @InjectMocks
  private StompAuthChannelInterceptor interceptor;

  @Mock
  private TokenProvider tokenProvider;

  @Mock
  private UserSessionRegistry userSessionRegistry;

  private Message<byte[]> connectMessage(String authorizationHeader) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    if (authorizationHeader != null) {
      accessor.setNativeHeader("Authorization", authorizationHeader);
    }
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }

  @Nested
  @DisplayName("CONNECT 인증")
  class ConnectAuthentication {

    @Test
    @DisplayName("유효한 토큰이면 인증 사용자를 STOMP 세션에 설정한다")
    void 유효한_토큰이면_인증_사용자를_STOMP_세션에_설정한다() {
      // given
      UUID userId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();
      given(tokenProvider.parseAccessToken("valid-token"))
          .willReturn(new AccessTokenClaims(userId, sessionId, "USER"));
      Message<byte[]> message = connectMessage("Bearer valid-token");

      // when
      Message<?> result = interceptor.preSend(message, null);

      // then
      StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
      Authentication authentication = (Authentication) accessor.getUser();
      assertThat(authentication).isNotNull();
      assertThat(authentication.getPrincipal()).isEqualTo(new UserPrincipal(userId, "USER"));
      assertThat(authentication.getAuthorities())
          .extracting(GrantedAuthority::getAuthority)
          .containsExactly("USER");
      verify(userSessionRegistry).verifyUserSession(userId, sessionId);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 예외를 전파해 연결을 거부한다")
    void Authorization_헤더가_없으면_예외를_전파해_연결을_거부한다() {
      // given
      Message<byte[]> message = connectMessage(null);

      // when & then
      assertThatThrownBy(() -> interceptor.preSend(message, null))
          .isInstanceOf(BadCredentialsException.class);
      verify(tokenProvider, never()).parseAccessToken(any());
    }

    @Test
    @DisplayName("Bearer 접두사가 아니면 예외를 전파해 연결을 거부한다")
    void Bearer_접두사가_아니면_예외를_전파해_연결을_거부한다() {
      // given
      Message<byte[]> message = connectMessage("Basic abcdef");

      // when & then
      assertThatThrownBy(() -> interceptor.preSend(message, null))
          .isInstanceOf(BadCredentialsException.class);
      verify(tokenProvider, never()).parseAccessToken(any());
    }

    @Test
    @DisplayName("Bearer 뒤 토큰이 비어 있으면 예외를 전파해 연결을 거부한다")
    void Bearer_뒤_토큰이_비어_있으면_예외를_전파해_연결을_거부한다() {
      // given
      Message<byte[]> message = connectMessage("Bearer ");

      // when & then
      assertThatThrownBy(() -> interceptor.preSend(message, null))
          .isInstanceOf(BadCredentialsException.class);
      verify(tokenProvider, never()).parseAccessToken(any());
    }

    @Test
    @DisplayName("만료된 토큰이면 예외를 전파해 연결을 거부한다")
    void 만료된_토큰이면_예외를_전파해_연결을_거부한다() {
      // given
      given(tokenProvider.parseAccessToken("expired-token"))
          .willThrow(ExpiredTokenException.withNone());
      Message<byte[]> message = connectMessage("Bearer expired-token");

      // when & then
      assertThatThrownBy(() -> interceptor.preSend(message, null))
          .isInstanceOf(ExpiredTokenException.class);
    }

    @Test
    @DisplayName("세션이 만료되었으면 예외를 전파해 연결을 거부한다")
    void 세션이_만료되었으면_예외를_전파해_연결을_거부한다() {
      // given
      UUID userId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();
      given(tokenProvider.parseAccessToken("valid-token"))
          .willReturn(new AccessTokenClaims(userId, sessionId, "USER"));
      willThrow(UserSessionExpiredException.withNone())
          .given(userSessionRegistry).verifyUserSession(userId, sessionId);
      Message<byte[]> message = connectMessage("Bearer valid-token");

      // when & then
      assertThatThrownBy(() -> interceptor.preSend(message, null))
          .isInstanceOf(UserSessionExpiredException.class);
    }
  }
}