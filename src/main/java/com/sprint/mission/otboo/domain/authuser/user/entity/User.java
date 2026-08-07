package com.sprint.mission.otboo.domain.authuser.user.entity;

import com.sprint.mission.otboo.domain.authuser.user.entity.enums.LockReason;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false)
  private String password;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @Column(nullable = false)
  private boolean locked;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private LockReason lockReason;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(nullable = false)
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

  public void changePassword(String newEncodedPassword) {
    this.password = newEncodedPassword;
  }

  public void changeName(String newName) {
    this.name = newName;
  }

  public void changeRole(Role newRole) {
    this.role = newRole;
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
