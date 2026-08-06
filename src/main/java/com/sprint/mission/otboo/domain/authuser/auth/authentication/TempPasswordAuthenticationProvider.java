package com.sprint.mission.otboo.domain.authuser.auth.authentication;

import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.security.details.CustomUserDetails;
import com.sprint.mission.otboo.global.temppassword.registry.TempPasswordRegistry;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

@RequiredArgsConstructor
public class TempPasswordAuthenticationProvider implements AuthenticationProvider {

  private final UserRepository userRepository;
  private final TempPasswordRegistry tempPasswordRegistry;

  @Override
  public @Nullable Authentication authenticate(Authentication authentication)
      throws AuthenticationException {
    String email = authentication.getName();
    String rawPassword = String.valueOf(authentication.getCredentials());

    User foundUser = userRepository.findByEmail(email)
        .orElseThrow(() -> new BadCredentialsException("자격 증명이 올바르지 않습니다."));

    if (foundUser.isLocked()) {
      throw new LockedException("계정이 잠겨있습니다.");
    }

    if (!tempPasswordRegistry.matches(foundUser.getId(), rawPassword)) {
      throw new BadCredentialsException("자격 증명이 올바르지 않습니다.");
    }

    CustomUserDetails principal = new CustomUserDetails(foundUser);
    return UsernamePasswordAuthenticationToken.authenticated(
        principal, null, principal.getAuthorities());
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
