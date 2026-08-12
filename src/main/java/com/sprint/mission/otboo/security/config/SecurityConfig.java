package com.sprint.mission.otboo.security.config;

import com.sprint.mission.otboo.domain.authuser.auth.authentication.TempPasswordAuthenticationProvider;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import com.sprint.mission.otboo.domain.authuser.user.mapper.UserMapper;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.global.temppassword.registry.TempPasswordRegistry;
import com.sprint.mission.otboo.security.details.CustomUserDetailsService;
import com.sprint.mission.otboo.security.exception.ErrorResponseWriter;
import com.sprint.mission.otboo.security.filter.TokenAuthenticationFilter;
import com.sprint.mission.otboo.security.oauth2.handler.OAuth2LoginFailureHandler;
import com.sprint.mission.otboo.security.oauth2.handler.OAuth2LoginSuccessHandler;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(proxyTargetClass = true)   // 변경: 인터페이스(JDK) 프록시 대신 CGLIB로 고정
public class SecurityConfig {

  @Bean
  public AuthenticationManager authenticationManager(
      CustomUserDetailsService userDetailsService,
      PasswordEncoder passwordEncoder,
      UserRepository userRepository,
      UserMapper userMapper,
      TempPasswordRegistry tempPasswordRegistry
  ) {

    DaoAuthenticationProvider daoProvider = new DaoAuthenticationProvider(userDetailsService);
    daoProvider.setPasswordEncoder(passwordEncoder);

    TempPasswordAuthenticationProvider tempPasswordAuthenticationProvider = new TempPasswordAuthenticationProvider(
        userRepository, userMapper, tempPasswordRegistry);

    return new ProviderManager(daoProvider, tempPasswordAuthenticationProvider);
  }

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      JsonMapper jsonMapper,
      TokenProvider tokenProvider,
      UserSessionRegistry userSessionRegistry,
      OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
      OAuth2LoginFailureHandler oAuth2LoginFailureHandler
  ) throws Exception {

    http.formLogin(AbstractHttpConfigurer::disable);
    http.httpBasic(AbstractHttpConfigurer::disable);
    http.logout(AbstractHttpConfigurer::disable);

    http.csrf(csrf -> csrf
        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
        .ignoringRequestMatchers("/ws/**")
        // STATELESS라도 SessionManagementFilter는 모든 인증된 요청을 "새 로그인"으로 보고
        // 세션 인증 전략을 매번 다시 태운다. 기본 전략은 세션을 건드리려다 CSRF 토큰까지
        // 갈아치워서 로그인 성공 직후 요청이 다시 CSRF 실패로 튕기는 루프가 생겼다.
        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())
    );

    http.sessionManagement(session -> session
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
    );

    http.addFilterBefore(
        new TokenAuthenticationFilter(tokenProvider, userSessionRegistry),
        UsernamePasswordAuthenticationFilter.class
    );

    http.exceptionHandling(ex -> ex
        .defaultAuthenticationEntryPointFor((request, response, authException) ->
            ErrorResponseWriter.write(response, jsonMapper, HttpStatus.UNAUTHORIZED,
                authException, "인증이 필요합니다."), AnyRequestMatcher.INSTANCE)

        .accessDeniedHandler((request, response, accessDeniedException) ->
            ErrorResponseWriter.write(response, jsonMapper, HttpStatus.FORBIDDEN,
                accessDeniedException, "접근 권한이 없습니다."))
    );

    http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/", "/index.html", "/favicon.ico", "/css/**", "/js/**", "/images/**",
            "/assets/**", "/logo_symbol.svg", "/vite.svg").permitAll()
        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
        .requestMatchers("/ws/**").permitAll()

        .requestMatchers(HttpMethod.POST, "/api/auth/sign-out").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/auth/sign-in").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/auth/reset-password").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/auth/csrf-token").permitAll()

        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()

        .requestMatchers(HttpMethod.PATCH, "/api/users/{userId}/role")
        .hasAuthority(Role.ADMIN.name())
        .requestMatchers(HttpMethod.PATCH, "/api/users/{userId}/lock")
        .hasAuthority(Role.ADMIN.name())

        .requestMatchers("/uploads/**").permitAll()

        .anyRequest().authenticated()
    );

    http.oauth2Login(oauth2 -> oauth2
        .successHandler(oAuth2LoginSuccessHandler)
        .failureHandler(oAuth2LoginFailureHandler)
    );

    return http.build();
  }
}
