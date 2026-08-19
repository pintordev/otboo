package com.sprint.mission.otboo.external.purchase.dto;

public record PurchasePageResponse(
    String title,
    String imageUrl,
    String description,
    String siteName
) {

  public boolean isEmpty() {
    return (title == null || title.isBlank())
        && (imageUrl == null || imageUrl.isBlank());
  }
}
