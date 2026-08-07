package com.sprint.mission.otboo.domain.authuser.user.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.util.List;

public record LocationRequest(
    @DecimalMin(value = "-90.0", message = "latitude는 -90 이상이어야 합니다.")
    @DecimalMax(value = "90.0", message = "latitude는 90 이하여야 합니다.")
    Double latitude,

    @DecimalMin(value = "-180.0", message = "longitude는 -180 이상이어야 합니다.")
    @DecimalMax(value = "180.0", message = "longitude는 180 이하여야 합니다.")
    Double longitude,

    Integer x,

    Integer y,

    @Size(max = 10, message = "locationNames는 10개를 넘을 수 없습니다.")
    List<String> locationNames
) {

}
