package com.sprint.mission.otboo.domain.authuser.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.global.config.JpaConfig;
import com.sprint.mission.otboo.global.config.QuerydslConfig;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, QuerydslConfig.class})
class UserRepositoryTest {

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  @Nested
  @DisplayName("save")
  class Save {

    @Test
    @DisplayName("User를 저장하면 ID가 생성되고 저장된 값을 조회할 수 있다")
    void save_and_findById() {
      User user = User.create("홍길동", "hong@test.com", "encoded-password");

      User savedUser = userRepository.save(user);
      testEntityManager.flush();
      testEntityManager.clear();

      Optional<User> found = userRepository.findById(savedUser.getId());

      assertThat(found).isPresent();
      assertThat(found.get().getEmail()).isEqualTo("hong@test.com");
      assertThat(found.get().getName()).isEqualTo("홍길동");
      assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 존재하는 이메일로 저장하면 무결성 제약 예외가 발생한다")
    void save_duplicateEmail_throwsException() {
      User user1 = User.create("홍길동", "duplicate@test.com", "encoded-password-1");
      userRepository.save(user1);
      testEntityManager.flush();

      User user2 = User.create("김철수", "duplicate@test.com", "encoded-password-2");

      assertThatThrownBy(() -> {
        userRepository.saveAndFlush(user2);
      }).isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Nested
  @DisplayName("existsByEmail")
  class ExistsByEmail {

    @Test
    @DisplayName("존재하는 이메일에 대해 existsByEmail은 true를 반환한다")
    void existsByEmail_existingEmail_returnsTrue() {
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      userRepository.save(user);
      testEntityManager.flush();

      boolean exists = userRepository.existsByEmail("hong@test.com");

      assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 이메일에 대해 existsByEmail은 false를 반환한다")
    void existsByEmail_nonExistingEmail_returnsFalse() {
      boolean exists = userRepository.existsByEmail("notfound@test.com");

      assertThat(exists).isFalse();
    }
  }

  @Nested
  @DisplayName("findById")
  class FindById {

    @Test
    @DisplayName("존재하지 않는 ID로 조회하면 빈 Optional을 반환한다")
    void findById_nonExistingId_returnsEmpty() {
      Optional<User> found = userRepository.findById(UUID.randomUUID());

      assertThat(found).isEmpty();
    }
  }
}