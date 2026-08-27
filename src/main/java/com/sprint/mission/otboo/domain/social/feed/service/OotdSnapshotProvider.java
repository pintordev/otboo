package com.sprint.mission.otboo.domain.social.feed.service;

import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesDto;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.service.ClothesService;
import com.sprint.mission.otboo.domain.social.feed.dto.OotdSnapshot;
import com.sprint.mission.otboo.domain.social.feed.exception.ClothesOwnershipException;
import com.sprint.mission.otboo.domain.social.feed.exception.OotdNotFoundException;
import com.sprint.mission.otboo.global.file.util.FileUrlResolver;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Component
@Transactional(readOnly = true)
public class OotdSnapshotProvider {

  private final ClothesService clothesService;
  private final FileUrlResolver fileUrlResolver;

  public List<OotdSnapshot> readOotds(List<UUID> clothesIds, UUID currentUserId) {
    if (clothesIds == null || clothesIds.isEmpty()) {
      return List.of();
    }
    List<ClothesDto> clothesList = clothesService.getClothesByIds(clothesIds);

    if (clothesList.size() != clothesIds.size()) {
      log.warn("착장 일부를 조회할 수 없어 피드 등록 실패: 요청={}, 조회={}",
          clothesIds.size(), clothesList.size());
      throw OotdNotFoundException.withNone();
    }

    boolean hasOthersClothes = clothesList.stream()
        .anyMatch(clothes -> !clothes.ownerId().equals(currentUserId));
    if (hasOthersClothes) {
      log.warn("착장 소유권 불일치로 피드 등록 실패");
      throw ClothesOwnershipException.withNone();
    }

    return clothesList.stream()
        .map(this::toOotdSnapshot)
        .toList();
  }

  // 스냅샷은 저장 시점의 값을 박제하므로, publicBaseUrl이 바뀌어도 이미지를 찾을 수 있도록
  // resolve된 URL이 아닌 원본 키를 보관한다. 응답 시 FeedMapper가 resolve한다.
  private OotdSnapshot toOotdSnapshot(ClothesDto clothes) {
    return new OotdSnapshot(
        clothes.id(),
        clothes.name(),
        fileUrlResolver.extractKey(clothes.imageUrl()),
        clothes.type(),
        clothes.attributes()
    );
  }
}
