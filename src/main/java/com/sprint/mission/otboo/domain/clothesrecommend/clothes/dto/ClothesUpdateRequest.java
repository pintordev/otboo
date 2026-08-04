package com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ClothesUpdateRequest(
    @Size(max = 50) String name,
    ClothesType type,
    @Valid List<ClothesAttributeDto> attributes
) {}