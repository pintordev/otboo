package com.sprint.mission.otboo.security.details;

import static org.assertj.core.api.Assertions.assertThat;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.navercorp.fixturemonkey.jakarta.validation.plugin.JakartaValidationPlugin;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

@DisplayName("CustomUserDetails")
class CustomUserDetailsTest {

  private static final FixtureMonkey fm = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .plugin(new JakartaValidationPlugin())
      .build();

  @Nested
  @DisplayName("권한 조회 (getAuthorities)")
  class GetAuthorities {

    @Test
    @DisplayName("role 이름을 가진 권한 하나를 반환한다")
    void role_이름을_가진_권한_하나를_반환한다() {
      // given
      UserDto userDto = fm.giveMeBuilder(UserDto.class)
          .set("role", Role.ADMIN)
          .sample();
      CustomUserDetails userDetails = new CustomUserDetails(userDto, "encoded-password");

      // when
      var authorities = userDetails.getAuthorities();

      // then
      assertThat(authorities)
          .extracting(GrantedAuthority::getAuthority)
          .containsExactly("ADMIN");
    }
  }

  @Nested
  @DisplayName("계정 정보")
  class AccountInfo {

    @Test
    @DisplayName("getUsername()은 이메일을 반환한다")
    void getUsername은_이메일을_반환한다() {
      // given
      UserDto userDto = fm.giveMeBuilder(UserDto.class)
          .set("email", "otboo@example.com")
          .sample();
      CustomUserDetails userDetails = new CustomUserDetails(userDto, "encoded-password");

      // when & then
      assertThat(userDetails.getUsername()).isEqualTo("otboo@example.com");
    }

    @Test
    @DisplayName("getPassword()는 생성 시 전달한 비밀번호를 반환한다")
    void getPassword는_생성_시_전달한_비밀번호를_반환한다() {
      // given
      UserDto userDto = fm.giveMeBuilder(UserDto.class).sample();
      CustomUserDetails userDetails = new CustomUserDetails(userDto, "encoded-password");

      // when & then
      assertThat(userDetails.getPassword()).isEqualTo("encoded-password");
    }

    @Test
    @DisplayName("getUserDto()는 생성 시 전달한 UserDto를 그대로 반환한다")
    void getUserDto는_생성_시_전달한_UserDto를_그대로_반환한다() {
      // given
      UserDto userDto = fm.giveMeBuilder(UserDto.class).sample();
      CustomUserDetails userDetails = new CustomUserDetails(userDto, "encoded-password");

      // when & then
      assertThat(userDetails.getUserDto()).isEqualTo(userDto);
    }

    @Test
    @DisplayName("잠긴 계정이면 isAccountNonLocked()는 false를 반환한다")
    void 잠긴_계정이면_isAccountNonLocked는_false를_반환한다() {
      // given
      UserDto userDto = fm.giveMeBuilder(UserDto.class)
          .set("locked", true)
          .sample();
      CustomUserDetails userDetails = new CustomUserDetails(userDto, "encoded-password");

      // when & then
      assertThat(userDetails.isAccountNonLocked()).isFalse();
    }

    @Test
    @DisplayName("잠기지 않은 계정이면 isAccountNonLocked()는 true를 반환한다")
    void 잠기지_않은_계정이면_isAccountNonLocked는_true를_반환한다() {
      // given
      UserDto userDto = fm.giveMeBuilder(UserDto.class)
          .set("locked", false)
          .sample();
      CustomUserDetails userDetails = new CustomUserDetails(userDto, "encoded-password");

      // when & then
      assertThat(userDetails.isAccountNonLocked()).isTrue();
    }
  }

  @Nested
  @DisplayName("자격 증명 제거 (eraseCredentials)")
  class EraseCredentials {

    @Test
    @DisplayName("호출 후에는 getPassword()가 null을 반환한다")
    void 호출_후에는_getPassword가_null을_반환한다() {
      // given
      UserDto userDto = fm.giveMeBuilder(UserDto.class).sample();
      CustomUserDetails userDetails = new CustomUserDetails(userDto, "encoded-password");

      // when
      userDetails.eraseCredentials();

      // then
      assertThat(userDetails.getPassword()).isNull();
    }
  }
}
