package com.sprint.mission.otboo.domain.authuser.user.entity;

import com.sprint.mission.otboo.domain.authuser.user.entity.enums.LockReason;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "email", nullable = false, unique = true)
  private String email;

  @Column(name = "password", nullable = false)
  private String password;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false)
  private Role role;

  @Column(name = "is_locked", nullable = false)
  private boolean locked;

  @Enumerated(EnumType.STRING)
  @Column(name = "lock_reason", nullable = false)
  private LockReason lockReason;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  private User(String name, String email, String password, Role role, boolean locked,
      LockReason lockReason) {
    this.name = name;
    this.email = email;
    this.password = password;
    this.role = role;
    this.locked = locked;
    this.lockReason = lockReason;
  }

  public static User create(String name, String email, String encodedPassword) {
    return new User(name, email, encodedPassword, Role.USER, false, LockReason.NONE);
  }

  public static User createAdmin(String name, String email, String encodedPassword) {
    return new User(name, email, encodedPassword, Role.ADMIN, false, LockReason.NONE);
  }

  public void lock(LockReason lockReason) {
    this.locked = true;
    this.lockReason = lockReason;
  }

  public void unlock() {
    this.locked = false;
    this.lockReason = LockReason.NONE;
  }
}
