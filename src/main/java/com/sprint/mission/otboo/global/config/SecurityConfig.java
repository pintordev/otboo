package com.sprint.mission.otboo.global.config;

import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import com.sprint.mission.otboo.global.exception.ErrorResponseWriter;
import com.sprint.mission.otboo.global.security.details.CustomUserDetailsService;
import com.sprint.mission.otboo.global.security.jwt.JwtProvider;
import com.sprint.mission.otboo.global.security.jwt.filter.JwtAuthenticationFilter;
import com.sprint.mission.otboo.global.usersession.UserSession;
import com.sprint.mission.otboo.global.usersession.UserSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public AuthenticationManager authenticationManager(
      CustomUserDetailsService userDetailsService,
      PasswordEncoder passwordEncoder
  ) {

    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      ObjectMapper objectMapper,
      JwtProvider jwtProvider,
      UserSessionRegistry userSessionRegistry
  ) throws Exception {

    http.formLogin(AbstractHttpConfigurer::disable);
    http.httpBasic(AbstractHttpConfigurer::disable);

    http.csrf(AbstractHttpConfigurer::disable);
    // TODO: 개발 단계에서 csrf 비활성화 / 추후 csrf 설정 추가

    http.sessionManagement(session -> session
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
    );

    http.addFilterBefore(
        new JwtAuthenticationFilter(jwtProvider, userSessionRegistry),
        UsernamePasswordAuthenticationFilter.class
    );

    http.exceptionHandling(ex -> ex
        .authenticationEntryPoint((request, response, authException) ->
            ErrorResponseWriter.write(response, objectMapper, HttpStatus.UNAUTHORIZED,
                authException, "인증이 필요합니다."))
        .accessDeniedHandler((request, response, accessDeniedException) ->
            ErrorResponseWriter.write(response, objectMapper, HttpStatus.FORBIDDEN,
                accessDeniedException, "접근 권한이 없습니다."))
    );

    http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/", "/index.html", "/favicon.ico", "/css/**", "/js/**", "/images/**",
            "/assets/**", "/logo_symbol.svg", "/vite.svg").permitAll()
        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/auth/sign-in").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/auth/reset-password").permitAll()

        .anyRequest().authenticated()
    );

    // TODO: OAuth2 설정 추가

    return http.build();
  }
}
