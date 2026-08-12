package com.sprint.mission.otboo.domain.authuser.user.service;

import com.sprint.mission.otboo.domain.authuser.user.dto.request.ChangePasswordRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.ProfileUpdateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserCreateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.ProfileDto;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.Location;
import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.exception.AccessDeniedException;
import com.sprint.mission.otboo.domain.authuser.user.exception.DuplicateEmailException;
import com.sprint.mission.otboo.domain.authuser.user.exception.UserNotFoundException;
import com.sprint.mission.otboo.domain.authuser.user.mapper.UserMapper;
import com.sprint.mission.otboo.domain.authuser.user.repository.ProfileRepository;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.global.file.storage.FileStorageService;
import com.sprint.mission.otboo.global.temppassword.registry.TempPasswordRegistry;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final ProfileRepository profileRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserMapper userMapper;
  private final UserSessionRegistry userSessionRegistry;
  private final TempPasswordRegistry tempPasswordRegistry;
  private final FileStorageService fileStorageService;

  @Transactional
  public UserDto signUp(UserCreateRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw DuplicateEmailException.withEmail(request.email());
    }

    User newUser = User.create(request.name(), request.email(),
        passwordEncoder.encode(request.password()));
    User savedUser = null;
    try {
      savedUser = userRepository.saveAndFlush(newUser);
    } catch (DataIntegrityViolationException e) {
      throw DuplicateEmailException.withEmail(
          request.email()); // TODO: 특정 DB에 의존하게 되는데 어떻게 해야할까? 고민중
    }

    Profile newProfile = Profile.create(savedUser);
    profileRepository.save(newProfile);

    return userMapper.userDtoFrom(savedUser);
  }

  public ProfileDto getProfile(UUID userId) {
    Profile foundProfile = profileRepository.findByIdWithUser(userId)
        .orElseThrow(UserNotFoundException::withNone);
    return userMapper.profileDtoFrom(foundProfile);
  }

  @Transactional
  public ProfileDto changeProfile(UUID userId, ProfileUpdateRequest request,
      MultipartFile image, UUID requestUserId) {
    checkSelf(userId, requestUserId);

    Profile foundProfile = profileRepository.findByIdWithUser(userId)
        .orElseThrow(UserNotFoundException::withNone);

    // 프로필 정보 수정
    foundProfile.getUser().changeName(request.name());

    foundProfile.changeProfile(
        request.gender(),
        request.birthDate(),
        userMapper.locationFrom(request.location()),
        request.temperatureSensitivity()
    );

    String oldProfileImageUrl = foundProfile.getProfileImageUrl();
    String newProfileImageUrl = oldProfileImageUrl;
    if (image != null && !image.isEmpty()) {
      newProfileImageUrl = fileStorageService.store(image, "profile");
      fileStorageService.delete(oldProfileImageUrl);
    }

    foundProfile.changeProfileImageUrl(newProfileImageUrl);

    return userMapper.profileDtoFrom(foundProfile);
  }

  @Transactional
  public void changePassword(UUID userId, ChangePasswordRequest request, UUID requestUserId) {
    checkSelf(userId, requestUserId);

    User foundUser = userRepository.findById(userId)
        .orElseThrow(UserNotFoundException::withNone);

    foundUser.changePassword(
        passwordEncoder.encode(request.password())
    );

    userSessionRegistry.revokeAll(userId);
    tempPasswordRegistry.revoke(userId);
  }

  private void checkSelf(UUID userId, UUID requestUserId) {
    if (!userId.equals(requestUserId)) {
      throw AccessDeniedException.withNone();
    }
  }
}
