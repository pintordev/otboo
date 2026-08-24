package com.sprint.mission.otboo.domain.clothesrecommend.clothes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.entity.ClothesAttributeDef;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.exception.ClothesAttributeDefNotFoundException;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.repository.ClothesAttributeDefRepository;
import com.sprint.mission.otboo.domain.clothesrecommend.attributedef.repository.ClothesAttributeDefValueRepository;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesAttributeDto;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesCreateRequest;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesDto;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesListParams;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesType;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesUpdateRequest;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.Clothes;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.ClothesAttribute;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.exception.ClothesAttributeDuplicatedException;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.exception.ClothesNotFoundException;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.mapper.ClothesMapper;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.repository.ClothesAttributeRepository;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.repository.ClothesRepository;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.dto.SortDirection;
import com.sprint.mission.otboo.global.file.storage.FileStorageService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class ClothesServiceTest {

  @InjectMocks
  ClothesService clothesService;

  @Mock
  ClothesRepository clothesRepository;

  @Mock
  ClothesAttributeRepository clothesAttributeRepository;

  @Mock
  ClothesAttributeDefRepository clothesAttributeDefRepository;

  @Mock
  ClothesAttributeDefValueRepository clothesAttributeDefValueRepository;

  @Mock
  ClothesMapper clothesMapper;

  @Mock
  FileStorageService fileStorageService;

  @Nested
  @DisplayName("의상 등록")
  class Create {

    @Test
    @DisplayName("속성 없이 의상을 등록하면 빈 속성 목록으로 성공한다")
    void 속성_없이_의상을_등록하면_빈_속성_목록으로_성공한다() {
      // given
      UUID ownerId = UUID.randomUUID();
      ClothesCreateRequest request = new ClothesCreateRequest(
          ownerId, "테스트 상의", ClothesType.TOP, List.of());

      Clothes savedClothes = Clothes.create(ownerId, "테스트 상의", ClothesType.TOP);
      when(clothesRepository.save(any())).thenReturn(savedClothes);

      ClothesDto expectedDto = new ClothesDto(
          savedClothes.getId(), ownerId, "테스트 상의",
          null, ClothesType.TOP, List.of());
      when(clothesMapper.toDto(any(), anyList(), anyMap())).thenReturn(expectedDto);

      // when
      ClothesDto result = clothesService.create(request, null);

      // then
      assertThat(result.name()).isEqualTo("테스트 상의");
      assertThat(result.type()).isEqualTo(ClothesType.TOP);
      assertThat(result.attributes()).isEmpty();
    }

    @Test
    @DisplayName("속성과 함께 의상을 등록하면 속성이 포함된 결과를 반환한다")
    void 속성과_함께_의상을_등록하면_속성이_포함된_결과를_반환한다() {
      // given
      UUID ownerId = UUID.randomUUID();
      ClothesAttributeDef definition = ClothesAttributeDef.create("색상");

      ClothesCreateRequest request = new ClothesCreateRequest(
          ownerId, "테스트 하의", ClothesType.BOTTOM,
          List.of(new ClothesAttributeDto(definition.getId(), "블랙")));

      Clothes savedClothes = Clothes.create(ownerId, "테스트 하의", ClothesType.BOTTOM);
      when(clothesRepository.save(any())).thenReturn(savedClothes);

      when(clothesAttributeDefRepository.findAllById(anyList()))
          .thenReturn(List.of(definition));

      ClothesAttribute savedAttribute =
          ClothesAttribute.create(savedClothes.getId(), definition, "블랙");
      when(clothesAttributeRepository.saveAll(anyList()))
          .thenReturn(List.of(savedAttribute));

      when(clothesAttributeDefValueRepository.findAllByDefinitionIds(anyList()))
          .thenReturn(List.of());

      ClothesDto expectedDto = new ClothesDto(
          savedClothes.getId(), ownerId, "테스트 하의",
          null, ClothesType.BOTTOM, List.of());
      when(clothesMapper.toDto(any(), anyList(), anyMap())).thenReturn(expectedDto);

      // when
      ClothesDto result = clothesService.create(request, null);

      // then
      assertThat(result.name()).isEqualTo("테스트 하의");
      assertThat(result.type()).isEqualTo(ClothesType.BOTTOM);
    }

    @Test
    @DisplayName("이미지를 첨부해 등록하면 저장소에 저장하고 저장 키를 의상에 반영한다")
    void 이미지를_첨부해_등록하면_저장소에_저장하고_저장_키를_의상에_반영한다() {
      // given
      UUID ownerId = UUID.randomUUID();
      ClothesCreateRequest request = new ClothesCreateRequest(
          ownerId, "가디건", ClothesType.OUTER, List.of());

      Clothes savedClothes = Clothes.create(ownerId, "가디건", ClothesType.OUTER);
      when(clothesRepository.save(any())).thenReturn(savedClothes);

      MultipartFile image = new MockMultipartFile(
          "image", "cardigan.jpg", "image/jpeg", "dummy".getBytes());
      when(fileStorageService.store(image, "clothes")).thenReturn("clothes/cardigan.jpg");

      ClothesDto expectedDto = new ClothesDto(
          savedClothes.getId(), ownerId, "가디건", null, ClothesType.OUTER, List.of());
      when(clothesMapper.toDto(any(), anyList(), anyMap())).thenReturn(expectedDto);

      // when
      clothesService.create(request, image);

      // then
      verify(fileStorageService).store(image, "clothes");
      assertThat(savedClothes.getImageUrl()).isEqualTo("clothes/cardigan.jpg");
    }

    @Test
    @DisplayName("이미지를 첨부하지 않으면 저장소를 호출하지 않는다")
    void 이미지를_첨부하지_않으면_저장소를_호출하지_않는다() {
      // given
      UUID ownerId = UUID.randomUUID();
      ClothesCreateRequest request = new ClothesCreateRequest(
          ownerId, "가디건", ClothesType.OUTER, List.of());

      Clothes savedClothes = Clothes.create(ownerId, "가디건", ClothesType.OUTER);
      when(clothesRepository.save(any())).thenReturn(savedClothes);

      ClothesDto expectedDto = new ClothesDto(
          savedClothes.getId(), ownerId, "가디건", null, ClothesType.OUTER, List.of());
      when(clothesMapper.toDto(any(), anyList(), anyMap())).thenReturn(expectedDto);

      // when
      clothesService.create(request, null);

      // then
      verify(fileStorageService, never()).store(any(), any());
      assertThat(savedClothes.getImageUrl()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 속성 정의 ID를 전달하면 예외가 발생한다")
    void 존재하지_않는_속성_정의_ID를_전달하면_예외가_발생한다() {
      // given
      UUID ownerId = UUID.randomUUID();
      UUID invalidDefId = UUID.randomUUID();

      ClothesCreateRequest request = new ClothesCreateRequest(
          ownerId, "테스트 옷", ClothesType.TOP,
          List.of(new ClothesAttributeDto(invalidDefId, "빨강")));

      Clothes savedClothes = Clothes.create(ownerId, "테스트 옷", ClothesType.TOP);
      when(clothesRepository.save(any())).thenReturn(savedClothes);
      when(clothesAttributeDefRepository.findAllById(anyList())).thenReturn(List.of());

      // when & then
      assertThatThrownBy(() -> clothesService.create(request, null))
          .isInstanceOf(ClothesAttributeDefNotFoundException.class);
    }

    @Test
    @DisplayName("중복된_속성_정의_ID를_전달하면_ClothesAttributeDuplicatedException이_발생한다")
    void 중복된_속성_정의_ID를_전달하면_ClothesAttributeDuplicatedException이_발생한다() {
      // given
      UUID ownerId = UUID.randomUUID();
      UUID definitionId = UUID.randomUUID();

      ClothesCreateRequest request = new ClothesCreateRequest(
          ownerId, "테스트 옷", ClothesType.TOP,
          List.of(
              new ClothesAttributeDto(definitionId, "빨강"),
              new ClothesAttributeDto(definitionId, "파랑")));

      Clothes savedClothes = Clothes.create(ownerId, "테스트 옷", ClothesType.TOP);
      when(clothesRepository.save(any())).thenReturn(savedClothes);

      // when & then
      assertThatThrownBy(() -> clothesService.create(request, null))
          .isInstanceOf(ClothesAttributeDuplicatedException.class);
    }

    @Test
    @DisplayName("속성 검증에 실패하면 이미지를 저장하지 않는다")
    void 속성_검증에_실패하면_이미지를_저장하지_않는다() {
      // given
      UUID ownerId = UUID.randomUUID();
      UUID invalidDefId = UUID.randomUUID();
      ClothesCreateRequest request = new ClothesCreateRequest(
          ownerId, "테스트 옷", ClothesType.TOP,
          List.of(new ClothesAttributeDto(invalidDefId, "빨강")));

      Clothes savedClothes = Clothes.create(ownerId, "테스트 옷", ClothesType.TOP);
      when(clothesRepository.save(any())).thenReturn(savedClothes);
      when(clothesAttributeDefRepository.findAllById(anyList())).thenReturn(List.of());

      MultipartFile image = new MockMultipartFile(
          "image", "cardigan.jpg", "image/jpeg", "dummy".getBytes());

      // when & then
      assertThatThrownBy(() -> clothesService.create(request, image))
          .isInstanceOf(ClothesAttributeDefNotFoundException.class);
      verify(fileStorageService, never()).store(any(), any());
    }
  }

  @Nested
  @DisplayName("의상 목록 조회")
  class GetClothes {

    @Test
    @DisplayName("조회 결과가 없으면 빈 페이지를 반환한다")
    void 조회_결과가_없으면_빈_페이지를_반환한다() {
      // given
      ClothesListParams params = new ClothesListParams(
          null, null, 10, null, UUID.randomUUID());

      CursorPageResponse<Clothes> emptyPage = new CursorPageResponse<>(
          List.of(), null, null, false, 0L,
          "createdAt", SortDirection.DESCENDING);
      when(clothesRepository.findClothes(params)).thenReturn(emptyPage);

      // when
      CursorPageResponse<ClothesDto> result = clothesService.getClothes(params);

      // then
      assertThat(result.data()).isEmpty();
      assertThat(result.hasNext()).isFalse();
      assertThat(result.totalCount()).isZero();
    }

    @Test
    @DisplayName("조회 결과가 있으면 속성을 포함한 DTO 목록을 반환한다")
    void 조회_결과가_있으면_속성을_포함한_DTO_목록을_반환한다() {
      // given
      UUID ownerId = UUID.randomUUID();
      ClothesListParams params = new ClothesListParams(
          null, null, 10, null, ownerId);

      Clothes clothes1 = Clothes.create(ownerId, "상의1", ClothesType.TOP);
      Clothes clothes2 = Clothes.create(ownerId, "하의1", ClothesType.BOTTOM);

      CursorPageResponse<Clothes> page = new CursorPageResponse<>(
          List.of(clothes1, clothes2), null, null, false, 2L,
          "createdAt", SortDirection.DESCENDING);
      when(clothesRepository.findClothes(params)).thenReturn(page);

      when(clothesAttributeRepository.findAllByClothesIdsWithDefinition(anyList()))
          .thenReturn(List.of());

      ClothesDto dto1 = new ClothesDto(
          clothes1.getId(), ownerId, "상의1", null, ClothesType.TOP, List.of());
      ClothesDto dto2 = new ClothesDto(
          clothes2.getId(), ownerId, "하의1", null, ClothesType.BOTTOM, List.of());
      when(clothesMapper.toDto(any(), anyList(), anyMap()))
          .thenReturn(dto1, dto2);

      // when
      CursorPageResponse<ClothesDto> result = clothesService.getClothes(params);

      // then
      assertThat(result.data()).hasSize(2);
      assertThat(result.data().get(0).name()).isEqualTo("상의1");
      assertThat(result.data().get(1).name()).isEqualTo("하의1");
      assertThat(result.totalCount()).isEqualTo(2L);
    }
  }

  @Nested
  @DisplayName("의상 수정")
  class Update {

    @Test
    @DisplayName("정상 요청이면 이름, 타입, 속성이 수정된다")
    void 정상_요청이면_이름_타입_속성이_수정된다() {
      // given
      UUID clothesId = UUID.randomUUID();
      UUID ownerId = UUID.randomUUID();
      Clothes clothes = Clothes.create(ownerId, "반팔티", ClothesType.TOP);
      ReflectionTestUtils.setField(clothes, "id", clothesId);

      given(clothesRepository.findById(clothesId)).willReturn(Optional.of(clothes));

      ClothesAttributeDef colorDef = ClothesAttributeDef.create("색상");
      given(clothesAttributeDefRepository.findAllById(anyList()))
          .willReturn(List.of(colorDef));

      given(clothesAttributeRepository.saveAll(anyList()))
          .willAnswer(invocation -> invocation.getArgument(0));

      given(clothesAttributeDefValueRepository.findAllByDefinitionIds(anyList()))
          .willReturn(List.of());

      ClothesUpdateRequest request = new ClothesUpdateRequest(
          "긴팔티",
          ClothesType.OUTER,
          List.of(new ClothesAttributeDto(colorDef.getId(), "빨강"))
      );

      ClothesDto expectedDto = new ClothesDto(
          clothesId, ownerId, "긴팔티", null, ClothesType.OUTER, List.of());
      given(clothesMapper.toDto(any(), anyList(), anyMap())).willReturn(expectedDto);

      // when
      ClothesDto result = clothesService.update(clothesId, request, null);

      // then
      assertThat(result.name()).isEqualTo("긴팔티");
      assertThat(result.type()).isEqualTo(ClothesType.OUTER);
      verify(clothesAttributeRepository).deleteAllByClothesId(clothesId);
    }

    @Test
    @DisplayName("name만 보내면 name만 수정된다")
    void name만_보내면_name만_수정된다() {
      // given
      UUID clothesId = UUID.randomUUID();
      UUID ownerId = UUID.randomUUID();
      Clothes clothes = Clothes.create(ownerId, "반팔티", ClothesType.TOP);
      ReflectionTestUtils.setField(clothes, "id", clothesId);

      given(clothesRepository.findById(clothesId)).willReturn(Optional.of(clothes));
      given(clothesAttributeRepository.findAllByClothesIdWithDefinition(clothesId))
          .willReturn(List.of());

      ClothesUpdateRequest request = new ClothesUpdateRequest("긴팔티", null, null);

      ClothesDto expectedDto = new ClothesDto(
          clothesId, ownerId, "긴팔티", null, ClothesType.TOP, List.of());
      given(clothesMapper.toDto(any(), anyList(), anyMap())).willReturn(expectedDto);

      // when
      ClothesDto result = clothesService.update(clothesId, request, null);

      // then
      assertThat(result.name()).isEqualTo("긴팔티");
      assertThat(result.type()).isEqualTo(ClothesType.TOP);
      verify(clothesAttributeRepository, never()).deleteAllByClothesId(any());
    }

    @Test
    @DisplayName("이미지를 첨부해 수정하면 새 이미지를 저장하고 기존 이미지를 삭제한다")
    void 이미지를_첨부해_수정하면_새_이미지를_저장하고_기존_이미지를_삭제한다() {
      // given
      UUID clothesId = UUID.randomUUID();
      UUID ownerId = UUID.randomUUID();
      Clothes clothes = Clothes.create(ownerId, "반팔티", ClothesType.TOP);
      ReflectionTestUtils.setField(clothes, "id", clothesId);
      clothes.changeImageUrl("clothes/old.jpg");

      given(clothesRepository.findById(clothesId)).willReturn(Optional.of(clothes));
      given(clothesAttributeRepository.findAllByClothesIdWithDefinition(clothesId))
          .willReturn(List.of());

      MultipartFile image = new MockMultipartFile(
          "image", "new.jpg", "image/jpeg", "dummy".getBytes());
      given(fileStorageService.store(image, "clothes")).willReturn("clothes/new.jpg");

      ClothesUpdateRequest request = new ClothesUpdateRequest(null, null, null);

      ClothesDto expectedDto = new ClothesDto(
          clothesId, ownerId, "반팔티", null, ClothesType.TOP, List.of());
      given(clothesMapper.toDto(any(), anyList(), anyMap())).willReturn(expectedDto);

      // when
      clothesService.update(clothesId, request, image);

      // then
      verify(fileStorageService).store(image, "clothes");
      verify(fileStorageService).delete("clothes/old.jpg");
      assertThat(clothes.getImageUrl()).isEqualTo("clothes/new.jpg");
    }

    @Test
    @DisplayName("이미지를 첨부하지 않으면 기존 이미지를 그대로 유지한다")
    void 이미지를_첨부하지_않으면_기존_이미지를_그대로_유지한다() {
      // given
      UUID clothesId = UUID.randomUUID();
      UUID ownerId = UUID.randomUUID();
      Clothes clothes = Clothes.create(ownerId, "반팔티", ClothesType.TOP);
      ReflectionTestUtils.setField(clothes, "id", clothesId);
      clothes.changeImageUrl("clothes/old.jpg");

      given(clothesRepository.findById(clothesId)).willReturn(Optional.of(clothes));
      given(clothesAttributeRepository.findAllByClothesIdWithDefinition(clothesId))
          .willReturn(List.of());

      ClothesUpdateRequest request = new ClothesUpdateRequest("긴팔티", null, null);

      ClothesDto expectedDto = new ClothesDto(
          clothesId, ownerId, "긴팔티", null, ClothesType.TOP, List.of());
      given(clothesMapper.toDto(any(), anyList(), anyMap())).willReturn(expectedDto);

      // when
      clothesService.update(clothesId, request, null);

      // then
      verify(fileStorageService, never()).store(any(), any());
      verify(fileStorageService, never()).delete(any());
      assertThat(clothes.getImageUrl()).isEqualTo("clothes/old.jpg");
    }

    @Test
    @DisplayName("존재하지 않는 옷이면 예외가 발생한다")
    void 존재하지_않는_옷이면_예외가_발생한다() {
      // given
      UUID clothesId = UUID.randomUUID();
      given(clothesRepository.findById(clothesId)).willReturn(Optional.empty());

      ClothesUpdateRequest request = new ClothesUpdateRequest("긴팔티", null, null);

      // when & then
      assertThatThrownBy(() -> clothesService.update(clothesId, request, null))
          .isInstanceOf(ClothesNotFoundException.class);
    }

    @Test
    @DisplayName("소프트 삭제된 옷이면 예외가 발생한다")
    void 소프트_삭제된_옷이면_예외가_발생한다() {
      // given
      UUID clothesId = UUID.randomUUID();
      Clothes clothes = Clothes.create(UUID.randomUUID(), "반팔티", ClothesType.TOP);
      ReflectionTestUtils.setField(clothes, "id", clothesId);
      clothes.delete();

      given(clothesRepository.findById(clothesId)).willReturn(Optional.of(clothes));

      ClothesUpdateRequest request = new ClothesUpdateRequest("긴팔티", null, null);

      // when & then
      assertThatThrownBy(() -> clothesService.update(clothesId, request, null))
          .isInstanceOf(ClothesNotFoundException.class);
    }

    @Test
    @DisplayName("속성 검증에 실패하면 기존 이미지를 삭제하지 않고 새 이미지도 저장하지 않는다")
    void 속성_검증에_실패하면_기존_이미지를_삭제하지_않고_새_이미지도_저장하지_않는다() {
      // given
      UUID clothesId = UUID.randomUUID();
      UUID invalidDefId = UUID.randomUUID();
      Clothes clothes = Clothes.create(UUID.randomUUID(), "반팔티", ClothesType.TOP);
      ReflectionTestUtils.setField(clothes, "id", clothesId);
      clothes.changeImageUrl("clothes/old.jpg");

      given(clothesRepository.findById(clothesId)).willReturn(Optional.of(clothes));
      given(clothesAttributeDefRepository.findAllById(anyList())).willReturn(List.of());

      MultipartFile image = new MockMultipartFile(
          "image", "new.jpg", "image/jpeg", "dummy".getBytes());
      ClothesUpdateRequest request = new ClothesUpdateRequest(
          null, null, List.of(new ClothesAttributeDto(invalidDefId, "빨강")));

      // when & then
      assertThatThrownBy(() -> clothesService.update(clothesId, request, image))
          .isInstanceOf(ClothesAttributeDefNotFoundException.class);
      verify(fileStorageService, never()).store(any(), any());
      verify(fileStorageService, never()).delete(any());
      assertThat(clothes.getImageUrl()).isEqualTo("clothes/old.jpg");
    }
  }

  @Nested
  @DisplayName("의상 삭제")
  class Delete {

    @Test
    @DisplayName("정상 요청이면 소프트 삭제된다")
    void 정상_요청이면_소프트_삭제된다() {
      // given
      UUID clothesId = UUID.randomUUID();
      Clothes clothes = Clothes.create(UUID.randomUUID(), "반팔티", ClothesType.TOP);
      ReflectionTestUtils.setField(clothes, "id", clothesId);

      given(clothesRepository.findById(clothesId)).willReturn(Optional.of(clothes));

      // when
      clothesService.delete(clothesId);

      // then
      assertThat(clothes.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 옷이면 예외가 발생한다")
    void 존재하지_않는_옷이면_예외가_발생한다() {
      // given
      UUID clothesId = UUID.randomUUID();
      given(clothesRepository.findById(clothesId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> clothesService.delete(clothesId))
          .isInstanceOf(ClothesNotFoundException.class);
    }

    @Test
    @DisplayName("이미 삭제된 옷이면 예외가 발생한다")
    void 이미_삭제된_옷이면_예외가_발생한다() {
      // given
      UUID clothesId = UUID.randomUUID();
      Clothes clothes = Clothes.create(UUID.randomUUID(), "반팔티", ClothesType.TOP);
      ReflectionTestUtils.setField(clothes, "id", clothesId);
      clothes.delete();

      given(clothesRepository.findById(clothesId)).willReturn(Optional.of(clothes));

      // when & then
      assertThatThrownBy(() -> clothesService.delete(clothesId))
          .isInstanceOf(ClothesNotFoundException.class);
    }
  }
}