package com.sprint.mission.otboo.global.security.details;

import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
public class CustomUserDetails implements UserDetails, CredentialsContainer {

  private final UUID userId;
  private final String email;
  private final String name;
  private final Role role;
  private final boolean locked;
  private final Instant createdAt;
  private String password;

  public CustomUserDetails(User user) {
    this.userId = user.getId();
    this.email = user.getEmail();
    this.name = user.getName();
    this.role = user.getRole();
    this.locked = user.isLocked();
    this.createdAt = user.getCreatedAt();
    this.password = user.getPassword();
  }

  @Override
  public void eraseCredentials() {
    this.password = null;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority(role.name()));
  }

  @Override
  public @Nullable String getPassword() {
    return password;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonLocked() {
    return !locked;
  }
}
