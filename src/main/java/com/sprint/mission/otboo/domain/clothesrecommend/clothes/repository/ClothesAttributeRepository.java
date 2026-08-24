package com.sprint.mission.otboo.domain.clothesrecommend.clothes.repository;

import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.ClothesAttribute;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClothesAttributeRepository extends JpaRepository<ClothesAttribute, UUID> {

  @Query("SELECT ca FROM ClothesAttribute ca "
      + "JOIN FETCH ca.definition "
      + "WHERE ca.clothesId = :clothesId")
  List<ClothesAttribute> findAllByClothesIdWithDefinition(@Param("clothesId") UUID clothesId);

  @Query("SELECT ca FROM ClothesAttribute ca "
      + "JOIN FETCH ca.definition "
      + "WHERE ca.clothesId IN :clothesIds")
  List<ClothesAttribute> findAllByClothesIdsWithDefinition(
      @Param("clothesIds") List<UUID> clothesIds);

  // 파생 삭제 쿼리는 플러시 시점까지 DELETE를 미뤄 재등록 INSERT가 먼저 나가므로
  // 유니크 제약을 위반한다. 즉시 실행되는 벌크 DELETE를 사용한다.
  @Modifying
  @Query("delete from ClothesAttribute ca where ca.clothesId = :clothesId")
  void deleteAllByClothesId(@Param("clothesId") UUID clothesId);
}