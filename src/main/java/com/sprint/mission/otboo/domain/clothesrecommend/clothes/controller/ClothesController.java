package com.sprint.mission.otboo.domain.clothesrecommend.clothes.controller;

import com.sprint.mission.otboo.domain.clothesrecommend.clothes.controller.api.ClothesApi;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesCreateRequest;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesDto;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesListParams;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesUpdateRequest;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.service.ClothesService;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.service.ClothesExtractionService;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesExtractionParams;

@RequiredArgsConstructor
@RequestMapping("/api/clothes")
@RestController
public class ClothesController implements ClothesApi {

  private final ClothesService clothesService;
  private final ClothesExtractionService clothesExtractionService;

  @Override
  @GetMapping
  public ResponseEntity<CursorPageResponse<ClothesDto>> getClothes(
      @Valid ClothesListParams params) {
    return ResponseEntity.ok(clothesService.getClothes(params));
  }

  @Override
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ClothesDto> create(
      @RequestPart @Valid ClothesCreateRequest request,
      @RequestPart(required = false) MultipartFile image) {
    ClothesDto created = clothesService.create(request, image);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @Override
  @PatchMapping(value = "/{clothesId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ClothesDto> update(
      @PathVariable UUID clothesId,
      @RequestPart @Valid ClothesUpdateRequest request,
      @RequestPart(required = false) MultipartFile image) {
    ClothesDto updated = clothesService.update(clothesId, request, image);
    return ResponseEntity.ok(updated);
  }

  @Override
  @DeleteMapping("/{clothesId}")
  public ResponseEntity<Void> delete(@PathVariable UUID clothesId) {
    clothesService.delete(clothesId);
    return ResponseEntity.noContent().build();
  }

  @Override
  @GetMapping("/extractions")
  public ResponseEntity<ClothesDto> extractByUrl(@Valid ClothesExtractionParams params) {
    ClothesDto extracted = clothesExtractionService.extractByUrl(params.url());
    return ResponseEntity.ok(extracted);
  }
}
