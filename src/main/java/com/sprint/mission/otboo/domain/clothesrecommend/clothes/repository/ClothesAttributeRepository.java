package com.sprint.mission.otboo.domain.clothesrecommend.clothes.repository;

import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.ClothesAttribute;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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

  void deleteAllByClothesId(UUID clothesId);
}