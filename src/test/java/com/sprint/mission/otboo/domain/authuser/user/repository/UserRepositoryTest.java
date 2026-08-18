package com.sprint.mission.otboo.domain.authuser.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.global.config.JpaConfig;
import com.sprint.mission.otboo.global.config.QuerydslConfig;
import java.util.List;
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

// application-test.yaml의 datasource가 testcontainers jdbc url(jdbc:tc:postgresql:...)이라
// 별도 @Container 선언 없이도 실제 Postgres 컨테이너에 대해 검증된다.
// QueryDSL 기반 search()는 UserCustomRepositoryImplTest에서 별도로 검증한다.
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, QuerydslConfig.class})
@DisplayName("UserRepository")
class UserRepositoryTest {

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  @Nested
  @DisplayName("저장 (save)")
  class Save {

    @Test
    @DisplayName("User를 저장하면 ID가 생성되고 저장된 값을 조회할 수 있다")
    void User를_저장하면_ID가_생성되고_저장된_값을_조회할_수_있다() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");

      // when
      User savedUser = userRepository.save(user);
      testEntityManager.flush();
      testEntityManager.clear();

      // then
      Optional<User> found = userRepository.findById(savedUser.getId());
      assertThat(found).isPresent();
      assertThat(found.get().getEmail()).isEqualTo("hong@test.com");
      assertThat(found.get().getName()).isEqualTo("홍길동");
      assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 존재하는 이메일로 저장하면 무결성 제약 예외가 발생한다")
    void 이미_존재하는_이메일로_저장하면_무결성_제약_예외가_발생한다() {
      // given
      User user1 = User.create("홍길동", "duplicate@test.com", "encoded-password-1");
      userRepository.save(user1);
      testEntityManager.flush();
      User user2 = User.create("김철수", "duplicate@test.com", "encoded-password-2");

      // when & then
      assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Nested
  @DisplayName("이메일 존재 여부 (existsByEmail)")
  class ExistsByEmail {

    @Test
    @DisplayName("존재하는 이메일이면 true를 반환한다")
    void 존재하는_이메일이면_true를_반환한다() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      userRepository.save(user);
      testEntityManager.flush();

      // when & then
      assertThat(userRepository.existsByEmail("hong@test.com")).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 이메일이면 false를 반환한다")
    void 존재하지_않는_이메일이면_false를_반환한다() {
      // when & then
      assertThat(userRepository.existsByEmail("notfound@test.com")).isFalse();
    }
  }

  @Nested
  @DisplayName("ID로 조회 (findById)")
  class FindById {

    @Test
    @DisplayName("존재하지 않는 ID로 조회하면 빈 Optional을 반환한다")
    void 존재하지_않는_ID로_조회하면_빈_Optional을_반환한다() {
      // when & then
      assertThat(userRepository.findById(UUID.randomUUID())).isEmpty();
    }
  }

  @Nested
  @DisplayName("전체 ID 조회 (findAllIds)")
  class FindAllIds {

    @Test
    @DisplayName("저장된 유저가 있으면 전체 ID를 반환한다")
    void 저장된_유저가_있으면_전체_ID를_반환한다() {
      // given
      User user1 = User.create("홍길동", "hong@test.com", "encoded-password");
      User user2 = User.create("김철수", "kim@test.com", "encoded-password");
      userRepository.save(user1);
      userRepository.save(user2);
      testEntityManager.flush();

      // when
      List<UUID> result = userRepository.findAllIds();

      // then
      assertThat(result).containsExactlyInAnyOrder(user1.getId(), user2.getId());
    }

    @Test
    @DisplayName("저장된 유저가 없으면 빈 리스트를 반환한다")
    void 저장된_유저가_없으면_빈_리스트를_반환한다() {
      // when & then
      assertThat(userRepository.findAllIds()).isEmpty();
    }
  }
}
