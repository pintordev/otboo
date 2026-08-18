package com.sprint.mission.otboo.domain.authuser.user.repository;

import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.repository.querydsl.UserCustomRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID>, UserCustomRepository {

  boolean existsByEmail(String email);

  Optional<User> findByEmail(String email);

  @Query("SELECT u.id FROM User u")
  List<UUID> findAllIds();
}
