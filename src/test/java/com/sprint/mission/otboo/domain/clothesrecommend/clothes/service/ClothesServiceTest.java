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
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.exception.ClothesNotFoundException;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.mapper.ClothesMapper;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.repository.ClothesAttributeRepository;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.repository.ClothesRepository;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.dto.SortDirection;
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
import org.springframework.test.util.ReflectionTestUtils;

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