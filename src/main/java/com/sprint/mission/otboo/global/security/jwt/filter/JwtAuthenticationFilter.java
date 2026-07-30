package com.sprint.mission.otboo.global.security.jwt.filter;

import com.sprint.mission.otboo.global.security.jwt.JwtProvider;
import com.sprint.mission.otboo.global.security.jwt.exception.ExpiredTokenException;
import com.sprint.mission.otboo.global.security.jwt.exception.JwtException;
import com.sprint.mission.otboo.global.usersession.UserSession;
import com.sprint.mission.otboo.global.usersession.UserSessionRegistry;
import com.sprint.mission.otboo.global.usersession.exception.UserSessionException;
import com.sprint.mission.otboo.global.usersession.exception.UserSessionExpiredException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String TOKEN_EXPIRED_HEADER = "X-Token-Expired";

  private final JwtProvider jwtProvider;
  private final UserSessionRegistry userSessionRegistry;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    String token = resolveToken(request);
    if (token == null) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      authenticationAccessToken(token);
    } catch (ExpiredTokenException e) {
      response.setHeader(TOKEN_EXPIRED_HEADER, "true");
      log.warn("만료된 토큰, Refresh 필요");
    } catch (UserSessionExpiredException e) {
      log.warn("사용자 세션이 만료되었습니다. 재로그인 필요");
    } catch (JwtException | UserSessionException e) {
      log.warn("인증 실패: {}", e.getMessage());
    } catch (Exception e) {
      log.error("토큰 처리 중 예상하지 못한 예외 발생", e);
    }

    filterChain.doFilter(request, response);
  }

  private String resolveToken(HttpServletRequest request) {

    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

    if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
      return authorization.substring(BEARER_PREFIX.length());
    }

    return null;
  }

  private void authenticationAccessToken(String token) {

    Claims claims = jwtProvider.parseAccessTokenClaims(token);

    UUID userId = UUID.fromString(claims.getSubject());

    UUID sid = UUID.fromString(claims.get("sid", String.class));
    String role = claims.get("role", String.class);
    userSessionRegistry.verifyLoginSession(userId, sid);

    UserPrincipal userPrincipal = new UserPrincipal(userId, role);
    List<SimpleGrantedAuthority> authorities = Collections.singletonList(
        new SimpleGrantedAuthority(userPrincipal.role()));

    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
        userPrincipal, null, authorities);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authToken);
    SecurityContextHolder.setContext(context);
  }
}
