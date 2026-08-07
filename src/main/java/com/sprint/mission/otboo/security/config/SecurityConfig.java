package com.sprint.mission.otboo.security.config;

import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import com.sprint.mission.otboo.domain.authuser.user.mapper.UserMapper;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.security.exception.ErrorResponseWriter;
import com.sprint.mission.otboo.security.details.CustomUserDetailsService;
import com.sprint.mission.otboo.security.filter.TokenAuthenticationFilter;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import com.sprint.mission.otboo.domain.authuser.auth.authentication.TempPasswordAuthenticationProvider;
import com.sprint.mission.otboo.global.temppassword.registry.TempPasswordRegistry;
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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
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
      UserSessionRegistry userSessionRegistry
  ) throws Exception {

    http.formLogin(AbstractHttpConfigurer::disable);
    http.httpBasic(AbstractHttpConfigurer::disable);
    http.logout(AbstractHttpConfigurer::disable);

    http.csrf(csrf -> csrf
        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
    );

    http.sessionManagement(session -> session
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
    );

    http.addFilterBefore(
        new TokenAuthenticationFilter(tokenProvider, userSessionRegistry),
        UsernamePasswordAuthenticationFilter.class
    );

    http.exceptionHandling(ex -> ex
        .authenticationEntryPoint((request, response, authException) ->
            ErrorResponseWriter.write(response, jsonMapper, HttpStatus.UNAUTHORIZED,
                authException, "인증이 필요합니다."))
        .accessDeniedHandler((request, response, accessDeniedException) ->
            ErrorResponseWriter.write(response, jsonMapper, HttpStatus.FORBIDDEN,
                accessDeniedException, "접근 권한이 없습니다."))
    );

    http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/", "/index.html", "/favicon.ico", "/css/**", "/js/**", "/images/**",
            "/assets/**", "/logo_symbol.svg", "/vite.svg").permitAll()
        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

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

        .anyRequest().authenticated()
    );

    // TODO: OAuth2 설정 추가

    return http.build();
  }
}
