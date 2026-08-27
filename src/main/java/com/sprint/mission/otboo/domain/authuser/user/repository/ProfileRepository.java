package com.sprint.mission.otboo.domain.authuser.user.repository;

import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

  @Query("select p from Profile p join fetch p.user where p.id = :userId")
  Optional<Profile> findByIdWithUser(@Param("userId") UUID userId);

  @Query("SELECT p FROM Profile p WHERE p.location.locationX = :x AND p.location.locationY = :y")
  List<Profile> findByLocation(@Param("x") int x, @Param("y") int y);

  @Query("SELECT p.profileImageUrl FROM Profile p WHERE p.profileImageUrl IS NOT NULL")
  Set<String> findAllProfileImageUrls();
}
