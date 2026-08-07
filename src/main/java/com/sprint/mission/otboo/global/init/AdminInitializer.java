package com.sprint.mission.otboo.global.init;

import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.repository.ProfileRepository;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

  private final UserRepository userRepository;
  private final ProfileRepository profileRepository;
  private final PasswordEncoder passwordEncoder;
  private final AdminProperties adminProperties;

  @Override
  @Transactional
  public void run(ApplicationArguments args) throws Exception {
    if (!StringUtils.hasText(adminProperties.password())) {
      log.warn("ADMIN_PASSWORD가 설정되지 않아 관리자 계정 초기화를 건너뜁니다.");
      return;
    }

    if (userRepository.existsByEmail(adminProperties.email())) {
      log.info("관리자 계정이 이미 존재합니다: {}", adminProperties.email());
      return;
    }

    User admin = User.createAdmin(
        adminProperties.name(),
        adminProperties.email(),
        passwordEncoder.encode(adminProperties.password())
    );
    try {
      userRepository.saveAndFlush(admin);
    } catch (DataIntegrityViolationException e) {
      log.info("관리자 계정이 이미 존재합니다: {}", adminProperties.email());
      return;
    }

    Profile adminProfile = Profile.create(admin);
    profileRepository.save(adminProfile);

    log.info("관리자 계정이 생성되었습니다: {}", adminProperties.email());
  }
}
