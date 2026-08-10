package com.sprint.mission.otboo.security.interceptor;

import com.sprint.mission.otboo.security.details.UserPrincipal;
import com.sprint.mission.otboo.security.token.dto.AccessTokenClaims;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

  private static final String BEARER_PREFIX = "Bearer ";

  private final TokenProvider tokenProvider;
  private final UserSessionRegistry userSessionRegistry;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
      authenticate(accessor);
    }

    return message;
  }

  private void authenticate(StompHeaderAccessor accessor) {
    String token = resolveToken(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION));
    if (token == null) {
      throw new BadCredentialsException("STOMP CONNECT에 유효한 인증 토큰이 없습니다.");
    }

    AccessTokenClaims claims = tokenProvider.parseAccessToken(token);
    userSessionRegistry.verifyUserSession(claims.userId(), claims.sessionId());

    UserPrincipal userPrincipal = new UserPrincipal(claims.userId(), claims.role());
    accessor.setUser(new UsernamePasswordAuthenticationToken(
        userPrincipal, null,
        List.of(new SimpleGrantedAuthority(userPrincipal.role()))));
  }

  private String resolveToken(String authorization) {
    if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
      return null;
    }
    String token = authorization.substring(BEARER_PREFIX.length());
    return StringUtils.hasText(token) ? token : null;
  }
}