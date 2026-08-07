package com.sprint.mission.otboo.domain.authuser.user.mapper;

import com.sprint.mission.otboo.domain.authuser.user.dto.request.LocationRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.LocationDto;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.ProfileDto;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.Location;
import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

  public UserDto userDtoFrom(User user) {
    return new UserDto(
        user.getId(),
        user.getCreatedAt(),
        user.getEmail(),
        user.getName(),
        user.getRole(),
        user.isLocked()
    );
  }

  public ProfileDto profileDtoFrom(Profile profile) {
    return new ProfileDto(
        profile.getId(),
        profile.getUser().getName(),
        profile.getGender(),
        profile.getBirthDate(),
        locationDtoFrom(profile.getLocation()),
        profile.getTemperatureSensitivity(),
        profile.getProfileImageUrl()
    );
  }

  public LocationDto locationDtoFrom(Location location) {
    return location == null ? null
        : new LocationDto(
            location.getLatitude(),
            location.getLongitude(),
            location.getLocationX(),
            location.getLocationY(),
            location.getLocationNames() == null ? null
                : List.copyOf(location.getLocationNames())
        );
  }

  public Location locationFrom(LocationRequest request) {
    return request == null ? null
        : Location.create(request.latitude(), request.longitude(), request.x(), request.y(),
            request.locationNames());
  }
}
