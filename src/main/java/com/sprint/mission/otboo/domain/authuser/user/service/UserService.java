package com.sprint.mission.otboo.domain.authuser.user.service;

import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserCreateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.exception.DuplicateEmailException;
import com.sprint.mission.otboo.domain.authuser.user.mapper.UserMapper;
import com.sprint.mission.otboo.domain.authuser.user.repository.ProfileRepository;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final ProfileRepository profileRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserMapper userMapper;

  @Transactional
  public UserDto signUp(UserCreateRequest request) {

    if (userRepository.existsByEmail(request.email())) {
      throw DuplicateEmailException.withEmail(request.email());
    }

    User newUser = User.create(request.name(), request.email(),
        passwordEncoder.encode(request.password()));
    User savedUser;
    try {
      savedUser = userRepository.saveAndFlush(newUser);
    } catch (DataIntegrityViolationException e) {
      throw DuplicateEmailException.withEmail(request.email());
    }

    Profile newDefaultProfile = Profile.createDefault(savedUser);
    profileRepository.save(newDefaultProfile);

    return userMapper.userDtoFrom(savedUser);
  }
}
