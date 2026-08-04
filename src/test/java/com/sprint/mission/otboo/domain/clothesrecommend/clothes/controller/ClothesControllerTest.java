package com.sprint.mission.otboo.domain.clothesrecommend.clothes.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesDto;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesType;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesUpdateRequest;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.exception.ClothesNotFoundException;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.service.ClothesService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClothesController.class)
@WithMockUser
@DisplayName("ClothesController")
class ClothesControllerTest {

  @Autowired
  MockMvc mockMvc;

  @MockitoBean
  ClothesService clothesService;

  @Nested
  @DisplayName("옷 수정 - PATCH /api/clothes/{clothesId}")
  class UpdateClothes {

    @Test
    @DisplayName("정상 요청이면 200과 ClothesDto를 반환한다")
    void 정상_요청이면_200과_ClothesDto를_반환한다() throws Exception {
      // given
      UUID clothesId = UUID.randomUUID();
      UUID ownerId = UUID.randomUUID();

      ClothesDto expectedDto = new ClothesDto(
          clothesId, ownerId, "긴팔티", null, ClothesType.OUTER, List.of());
      given(clothesService.update(eq(clothesId), any(ClothesUpdateRequest.class), any()))
          .willReturn(expectedDto);

      String requestJson = """
          {
            "name": "긴팔티",
            "type": "OUTER",
            "attributes": []
          }
          """;

      MockMultipartFile requestPart = new MockMultipartFile(
          "request", "request.json", MediaType.APPLICATION_JSON_VALUE,
          requestJson.getBytes(StandardCharsets.UTF_8));

      // when & then
      mockMvc.perform(multipart(HttpMethod.PATCH, "/api/clothes/{clothesId}", clothesId)
              .file(requestPart)
              .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(clothesId.toString()))
          .andExpect(jsonPath("$.name").value("긴팔티"))
          .andExpect(jsonPath("$.type").value("OUTER"));
    }

    @Test
    @DisplayName("존재하지 않는 옷이면 404를 반환한다")
    void 존재하지_않는_옷이면_404를_반환한다() throws Exception {
      // given
      UUID clothesId = UUID.randomUUID();

      given(clothesService.update(eq(clothesId), any(ClothesUpdateRequest.class), any()))
          .willThrow(ClothesNotFoundException.withId(clothesId));

      String requestJson = """
          {
            "name": "긴팔티"
          }
          """;

      MockMultipartFile requestPart = new MockMultipartFile(
          "request", "request.json", MediaType.APPLICATION_JSON_VALUE,
          requestJson.getBytes(StandardCharsets.UTF_8));

      // when & then
      mockMvc.perform(multipart(HttpMethod.PATCH, "/api/clothes/{clothesId}", clothesId)
              .file(requestPart)
              .with(csrf()))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("옷 삭제 - DELETE /api/clothes/{clothesId}")
  class DeleteClothes {

    @Test
    @DisplayName("정상 요청이면 204를 반환한다")
    void 정상_요청이면_204를_반환한다() throws Exception {
      // given
      UUID clothesId = UUID.randomUUID();
      doNothing().when(clothesService).delete(clothesId);

      // when & then
      mockMvc.perform(delete("/api/clothes/{clothesId}", clothesId)
              .with(csrf()))
          .andExpect(status().isNoContent());

      verify(clothesService).delete(clothesId);
    }

    @Test
    @DisplayName("존재하지 않는 옷이면 404를 반환한다")
    void 존재하지_않는_옷이면_404를_반환한다() throws Exception {
      // given
      UUID clothesId = UUID.randomUUID();
      doThrow(ClothesNotFoundException.withId(clothesId))
          .when(clothesService).delete(clothesId);

      // when & then
      mockMvc.perform(delete("/api/clothes/{clothesId}", clothesId)
              .with(csrf()))
          .andExpect(status().isNotFound());
    }
  }
}