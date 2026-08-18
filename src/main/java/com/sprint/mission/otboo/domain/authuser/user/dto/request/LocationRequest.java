package com.sprint.mission.otboo.domain.authuser.user.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record LocationRequest(
    @NotNull(message = "latitude는 필수입니다.")
    @DecimalMin(value = "33.0", message = "latitude는 33.0 이상이어야 합니다.")
    @DecimalMax(value = "38.5", message = "latitude는 38.5 이하여야 합니다.")
    Double latitude,

    @NotNull(message = "longitude는 필수입니다.")
    @DecimalMin(value = "124.5", message = "longitude는 124.5 이상이어야 합니다.")
    @DecimalMax(value = "131.9", message = "longitude는 131.9 이하여야 합니다.")
    Double longitude,

    @NotNull(message = "x는 필수입니다.")
    Integer x,

    @NotNull(message = "y는 필수입니다.")
    Integer y,

    @NotNull(message = "locationNames는 필수입니다.")
    @Size(min = 4, max = 4, message = "locationNames는 4개여야 합니다.")
    List<@NotNull(message = "지역 이름에 빈 값이 포함될 수 없습니다.") String> locationNames
) {

}
