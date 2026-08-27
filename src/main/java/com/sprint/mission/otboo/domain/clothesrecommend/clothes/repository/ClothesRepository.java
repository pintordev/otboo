package com.sprint.mission.otboo.domain.clothesrecommend.clothes.repository;

import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesType;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.Clothes;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.repository.querydsl.ClothesCustomRepository;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClothesRepository extends JpaRepository<Clothes, UUID>,
    ClothesCustomRepository {

  @Query("SELECT c FROM Clothes c "
      + "WHERE c.ownerId = :ownerId "
      + "AND c.softDeletable.deletedAt IS NULL")
  List<Clothes> findActiveByOwnerId(@Param("ownerId") UUID ownerId);

  @Query("SELECT c FROM Clothes c "
      + "WHERE c.ownerId = :ownerId "
      + "AND c.type IN :types "
      + "AND c.softDeletable.deletedAt IS NULL")
  List<Clothes> findActiveByOwnerIdAndTypeIn(
      @Param("ownerId") UUID ownerId,
      @Param("types") Collection<ClothesType> types);

  @Query("SELECT c.imageUrl FROM Clothes c WHERE c.imageUrl IS NOT NULL")
  Set<String> findAllImageUrls();
}