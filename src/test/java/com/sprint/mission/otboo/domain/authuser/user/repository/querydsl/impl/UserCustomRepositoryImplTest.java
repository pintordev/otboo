package com.sprint.mission.otboo.domain.authuser.user.repository.querydsl.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserListParams;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.LockReason;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.global.config.JpaConfig;
import com.sprint.mission.otboo.global.config.QuerydslConfig;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.dto.SortDirection;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// UserRepository가 UserCustomRepository(QueryDSL)를 상속하므로 search()는 같은 빈을 통해 검증된다.
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, QuerydslConfig.class})
@DisplayName("UserCustomRepositoryImpl")
class UserCustomRepositoryImplTest {

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  private void setCreatedAt(UUID userId, Instant createdAt) {
    testEntityManager.getEntityManager()
        .createNativeQuery("update users set created_at = :createdAt where id = :id")
        .setParameter("createdAt", createdAt)
        .setParameter("id", userId)
        .executeUpdate();
  }

  @Nested
  @DisplayName("search")
  class Search {

    @Test
    @DisplayName("커서 없이 호출하면 limit개까지만 반환하고 남은 데이터가 있으면 hasNext는 true다")
    void 커서_없이_호출하면_limit개까지만_반환하고_남은_데이터가_있으면_hasNext는_true다() {
      // given
      for (int i = 0; i < 3; i++) {
        userRepository.save(User.create("사용자" + i, "user" + i + "@test.com", "encoded-password"));
      }
      testEntityManager.flush();
      testEntityManager.clear();
      UserListParams condition =
          new UserListParams(null, null, 2, "email", SortDirection.ASCENDING, null, null, null);

      // when
      CursorPageResponse<UserDto> result = userRepository.search(condition);

      // then
      assertThat(result.data()).hasSize(2);
      assertThat(result.hasNext()).isTrue();
      assertThat(result.totalCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("emailLike가 주어지면 이메일에 해당 문자열을 포함한 사용자만 조회한다")
    void emailLike가_주어지면_이메일에_해당_문자열을_포함한_사용자만_조회한다() {
      // given
      userRepository.save(User.create("홍길동", "hong@test.com", "encoded-password"));
      userRepository.save(User.create("김철수", "kim@test.com", "encoded-password"));
      testEntityManager.flush();
      testEntityManager.clear();
      UserListParams condition = new UserListParams(
          null, null, 10, "email", SortDirection.ASCENDING, "hong", null, null);

      // when
      CursorPageResponse<UserDto> result = userRepository.search(condition);

      // then
      assertThat(result.data()).extracting(UserDto::email).containsExactly("hong@test.com");
    }

    @Test
    @DisplayName("roleEqual이 주어지면 해당 권한을 가진 사용자만 조회한다")
    void roleEqual이_주어지면_해당_권한을_가진_사용자만_조회한다() {
      // given
      userRepository.save(User.create("일반유저", "user@test.com", "encoded-password"));
      userRepository.save(User.createAdmin("관리자", "admin@test.com", "encoded-password"));
      testEntityManager.flush();
      testEntityManager.clear();
      UserListParams condition = new UserListParams(
          null, null, 10, "email", SortDirection.ASCENDING, null, Role.ADMIN, null);

      // when
      CursorPageResponse<UserDto> result = userRepository.search(condition);

      // then
      assertThat(result.data()).extracting(UserDto::email).containsExactly("admin@test.com");
    }

    @Test
    @DisplayName("locked가 주어지면 잠금 상태가 일치하는 사용자만 조회한다")
    void locked가_주어지면_잠금_상태가_일치하는_사용자만_조회한다() {
      // given
      userRepository.save(User.create("정상유저", "active@test.com", "encoded-password"));
      User lockedUser = User.create("잠긴유저", "locked@test.com", "encoded-password");
      lockedUser.lock(LockReason.ADMIN_ACTION);
      userRepository.save(lockedUser);
      testEntityManager.flush();
      testEntityManager.clear();
      UserListParams condition = new UserListParams(
          null, null, 10, "email", SortDirection.ASCENDING, null, null, true);

      // when
      CursorPageResponse<UserDto> result = userRepository.search(condition);

      // then
      assertThat(result.data()).extracting(UserDto::email).containsExactly("locked@test.com");
      assertThat(result.data()).extracting(UserDto::locked).containsExactly(true);
    }

    @Test
    @DisplayName("createdAt이 같으면 id 오름차순으로 tie-break하여 조회한다")
    void createdAt이_같으면_id_오름차순으로_tie_break하여_조회한다() {
      // given
      Instant sameTime = Instant.parse("2026-07-28T00:00:00Z");
      User a = userRepository.save(User.create("A", "a@test.com", "encoded-password"));
      User b = userRepository.save(User.create("B", "b@test.com", "encoded-password"));
      testEntityManager.flush();
      setCreatedAt(a.getId(), sameTime);
      setCreatedAt(b.getId(), sameTime);
      testEntityManager.clear();
      UserListParams condition = new UserListParams(
          null, null, 10, "createdAt", SortDirection.ASCENDING, null, null, null);

      // when
      CursorPageResponse<UserDto> result = userRepository.search(condition);

      // then
      List<UUID> ids = result.data().stream().map(UserDto::id).toList();
      assertThat(ids).containsExactlyInAnyOrder(a.getId(), b.getId());
      assertThat(ids.get(0).toString()).isLessThan(ids.get(1).toString());
    }

    @Test
    @DisplayName("createdAt 정렬에서 cursor가 Instant로 파싱할 수 없으면 예외가 전파된다")
    void createdAt_정렬에서_cursor가_Instant로_파싱할_수_없으면_예외가_전파된다() {
      // given: 정상 흐름에서는 UserListParams의 @AssertTrue가 먼저 막아준다
      UserListParams condition = new UserListParams(
          "not-an-instant", UUID.randomUUID(), 10, "createdAt", SortDirection.ASCENDING, null, null,
          null);

      // when & then
      assertThatThrownBy(() -> userRepository.search(condition))
          .isInstanceOf(DateTimeParseException.class);
    }
  }

  @Nested
  @DisplayName("search - 커서 페이지네이션 (email 정렬)")
  class SearchCursorByEmail {

    @Test
    @DisplayName("오름차순 정렬에서 커서로 다음 페이지를 조회하면 나머지가 중복·누락 없이 조회된다")
    void 오름차순_정렬에서_커서로_다음_페이지를_조회하면_나머지가_중복_누락_없이_조회된다() {
      // given
      userRepository.save(User.create("가", "a@test.com", "encoded-password"));
      userRepository.save(User.create("나", "b@test.com", "encoded-password"));
      userRepository.save(User.create("다", "c@test.com", "encoded-password"));
      testEntityManager.flush();
      testEntityManager.clear();
      UserListParams firstPage =
          new UserListParams(null, null, 1, "email", SortDirection.ASCENDING, null, null, null);

      // when
      CursorPageResponse<UserDto> first = userRepository.search(firstPage);

      // then
      assertThat(first.data()).extracting(UserDto::email).containsExactly("a@test.com");
      assertThat(first.hasNext()).isTrue();
      assertThat(first.nextCursor()).isEqualTo("a@test.com");

      // when: 커서로 다음 페이지 조회
      UserListParams secondPage = new UserListParams(
          first.nextCursor(), first.nextIdAfter(), 2, "email", SortDirection.ASCENDING, null, null,
          null);
      CursorPageResponse<UserDto> second = userRepository.search(secondPage);

      // then
      assertThat(second.data()).extracting(UserDto::email)
          .containsExactly("b@test.com", "c@test.com");
      assertThat(second.hasNext()).isFalse();
      assertThat(second.nextCursor()).isNull();
      assertThat(second.nextIdAfter()).isNull();
    }

    @Test
    @DisplayName("내림차순 정렬에서 커서로 다음 페이지를 조회하면 나머지가 중복·누락 없이 조회된다")
    void 내림차순_정렬에서_커서로_다음_페이지를_조회하면_나머지가_중복_누락_없이_조회된다() {
      // given
      userRepository.save(User.create("가", "a@test.com", "encoded-password"));
      userRepository.save(User.create("나", "b@test.com", "encoded-password"));
      userRepository.save(User.create("다", "c@test.com", "encoded-password"));
      testEntityManager.flush();
      testEntityManager.clear();
      UserListParams firstPage =
          new UserListParams(null, null, 1, "email", SortDirection.DESCENDING, null, null, null);

      // when
      CursorPageResponse<UserDto> first = userRepository.search(firstPage);

      // then
      assertThat(first.data()).extracting(UserDto::email).containsExactly("c@test.com");
      assertThat(first.hasNext()).isTrue();
      assertThat(first.nextCursor()).isEqualTo("c@test.com");

      // when: 커서로 다음 페이지 조회
      UserListParams secondPage = new UserListParams(
          first.nextCursor(), first.nextIdAfter(), 2, "email", SortDirection.DESCENDING, null, null,
          null);
      CursorPageResponse<UserDto> second = userRepository.search(secondPage);

      // then
      assertThat(second.data()).extracting(UserDto::email)
          .containsExactly("b@test.com", "a@test.com");
      assertThat(second.hasNext()).isFalse();
    }
  }

  @Nested
  @DisplayName("search - 커서 페이지네이션 (createdAt 정렬)")
  class SearchCursorByCreatedAt {

    @Test
    @DisplayName("오름차순 정렬에서 커서로 다음 페이지를 조회하면 나머지가 중복·누락 없이 조회된다")
    void 오름차순_정렬에서_커서로_다음_페이지를_조회하면_나머지가_중복_누락_없이_조회된다() {
      // given
      User a = userRepository.save(User.create("A", "a@test.com", "encoded-password"));
      User b = userRepository.save(User.create("B", "b@test.com", "encoded-password"));
      User c = userRepository.save(User.create("C", "c@test.com", "encoded-password"));
      testEntityManager.flush();
      setCreatedAt(a.getId(), Instant.parse("2026-07-28T00:00:01Z"));
      setCreatedAt(b.getId(), Instant.parse("2026-07-28T00:00:02Z"));
      setCreatedAt(c.getId(), Instant.parse("2026-07-28T00:00:03Z"));
      testEntityManager.clear();
      UserListParams firstPage = new UserListParams(
          null, null, 1, "createdAt", SortDirection.ASCENDING, null, null, null);

      // when
      CursorPageResponse<UserDto> first = userRepository.search(firstPage);

      // then
      assertThat(first.data()).extracting(UserDto::email).containsExactly("a@test.com");
      assertThat(first.hasNext()).isTrue();
      assertThat(first.nextCursor()).isEqualTo(Instant.parse("2026-07-28T00:00:01Z").toString());

      // when: 커서로 다음 페이지 조회
      UserListParams secondPage = new UserListParams(
          first.nextCursor(), first.nextIdAfter(), 2, "createdAt", SortDirection.ASCENDING, null,
          null, null);
      CursorPageResponse<UserDto> second = userRepository.search(secondPage);

      // then
      assertThat(second.data()).extracting(UserDto::email)
          .containsExactly("b@test.com", "c@test.com");
      assertThat(second.hasNext()).isFalse();
    }

    @Test
    @DisplayName("내림차순 정렬에서 커서로 다음 페이지를 조회하면 나머지가 중복·누락 없이 조회된다")
    void 내림차순_정렬에서_커서로_다음_페이지를_조회하면_나머지가_중복_누락_없이_조회된다() {
      // given
      User a = userRepository.save(User.create("A", "a@test.com", "encoded-password"));
      User b = userRepository.save(User.create("B", "b@test.com", "encoded-password"));
      User c = userRepository.save(User.create("C", "c@test.com", "encoded-password"));
      testEntityManager.flush();
      setCreatedAt(a.getId(), Instant.parse("2026-07-28T00:00:01Z"));
      setCreatedAt(b.getId(), Instant.parse("2026-07-28T00:00:02Z"));
      setCreatedAt(c.getId(), Instant.parse("2026-07-28T00:00:03Z"));
      testEntityManager.clear();
      UserListParams firstPage = new UserListParams(
          null, null, 1, "createdAt", SortDirection.DESCENDING, null, null, null);

      // when
      CursorPageResponse<UserDto> first = userRepository.search(firstPage);

      // then
      assertThat(first.data()).extracting(UserDto::email).containsExactly("c@test.com");
      assertThat(first.hasNext()).isTrue();
      assertThat(first.nextCursor()).isEqualTo(Instant.parse("2026-07-28T00:00:03Z").toString());

      // when: 커서로 다음 페이지 조회
      UserListParams secondPage = new UserListParams(
          first.nextCursor(), first.nextIdAfter(), 2, "createdAt", SortDirection.DESCENDING, null,
          null, null);
      CursorPageResponse<UserDto> second = userRepository.search(secondPage);

      // then
      assertThat(second.data()).extracting(UserDto::email)
          .containsExactly("b@test.com", "a@test.com");
      assertThat(second.hasNext()).isFalse();
    }

    @Test
    @DisplayName("동률에서 커서로 다음 페이지를 조회하면 나머지가 중복·누락 없이 조회된다")
    void 동률에서_커서로_다음_페이지를_조회하면_나머지가_중복_누락_없이_조회된다() {
      // given
      Instant sameTime = Instant.parse("2026-07-28T00:00:00Z");
      User a = userRepository.save(User.create("A", "a@test.com", "encoded-password"));
      User b = userRepository.save(User.create("B", "b@test.com", "encoded-password"));
      testEntityManager.flush();
      setCreatedAt(a.getId(), sameTime);
      setCreatedAt(b.getId(), sameTime);
      testEntityManager.clear();
      UserListParams firstPage = new UserListParams(
          null, null, 1, "createdAt", SortDirection.ASCENDING, null, null, null);

      // when: 첫 페이지 조회
      CursorPageResponse<UserDto> first = userRepository.search(firstPage);

      // then
      assertThat(first.data()).hasSize(1);
      assertThat(first.hasNext()).isTrue();
      UUID firstId = first.data().get(0).id();

      // when: 커서로 다음 페이지 조회
      UserListParams secondPage = new UserListParams(
          first.nextCursor(), first.nextIdAfter(), 1, "createdAt", SortDirection.ASCENDING, null,
          null, null);
      CursorPageResponse<UserDto> second = userRepository.search(secondPage);

      // then
      assertThat(second.data()).hasSize(1);
      UUID secondId = second.data().get(0).id();
      assertThat(secondId).isNotEqualTo(firstId);
      assertThat(List.of(firstId, secondId)).containsExactlyInAnyOrder(a.getId(), b.getId());
    }
  }
}
