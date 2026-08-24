package com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("의상")
class ClothesTest {

  @Nested
  @DisplayName("생성")
  class Create {

    @Test
    @DisplayName("정적 팩토리 메서드로 생성하면 전달한 필드가 채워지고 삭제되지 않은 상태이다")
    void 정적_팩토리_메서드로_생성하면_전달한_필드가_채워지고_삭제되지_않은_상태이다() {
      // given
      UUID ownerId = UUID.randomUUID();

      // when
      Clothes clothes = Clothes.create(ownerId, "가디건", ClothesType.OUTER);

      // then
      assertThat(clothes.getId()).isNotNull();
      assertThat(clothes.getOwnerId()).isEqualTo(ownerId);
      assertThat(clothes.getName()).isEqualTo("가디건");
      assertThat(clothes.getType()).isEqualTo(ClothesType.OUTER);
      assertThat(clothes.getImageUrl()).isNull();
      assertThat(clothes.isDeleted()).isFalse();
    }
  }

  @Nested
  @DisplayName("이름 변경")
  class ChangeName {

    @Test
    @DisplayName("이름을 변경하면 반영된다")
    void 이름을_변경하면_반영된다() {
      // given
      Clothes clothes = Clothes.create(UUID.randomUUID(), "가디건", ClothesType.OUTER);

      // when
      clothes.changeName("긴팔 가디건");

      // then
      assertThat(clothes.getName()).isEqualTo("긴팔 가디건");
    }
  }

  @Nested
  @DisplayName("타입 변경")
  class ChangeType {

    @Test
    @DisplayName("타입을 변경하면 반영된다")
    void 타입을_변경하면_반영된다() {
      // given
      Clothes clothes = Clothes.create(UUID.randomUUID(), "원피스", ClothesType.DRESS);

      // when
      clothes.changeType(ClothesType.TOP);

      // then
      assertThat(clothes.getType()).isEqualTo(ClothesType.TOP);
    }
  }

  @Nested
  @DisplayName("이미지 URL 변경")
  class ChangeImageUrl {

    @Test
    @DisplayName("이미지 URL을 변경하면 반영된다")
    void 이미지_URL을_변경하면_반영된다() {
      // given
      Clothes clothes = Clothes.create(UUID.randomUUID(), "가디건", ClothesType.OUTER);

      // when
      clothes.changeImageUrl("clothes/key.jpg");

      // then
      assertThat(clothes.getImageUrl()).isEqualTo("clothes/key.jpg");
    }
  }

  @Nested
  @DisplayName("삭제")
  class Delete {

    @Test
    @DisplayName("삭제하면 isDeleted가 true를 반환한다")
    void 삭제하면_isDeleted가_true를_반환한다() {
      // given
      Clothes clothes = Clothes.create(UUID.randomUUID(), "가디건", ClothesType.OUTER);

      // when
      clothes.delete();

      // then
      assertThat(clothes.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("삭제하기 전에는 isDeleted가 false를 반환한다")
    void 삭제하기_전에는_isDeleted가_false를_반환한다() {
      // given
      Clothes clothes = Clothes.create(UUID.randomUUID(), "가디건", ClothesType.OUTER);

      // when & then
      assertThat(clothes.isDeleted()).isFalse();
    }
  }
}
