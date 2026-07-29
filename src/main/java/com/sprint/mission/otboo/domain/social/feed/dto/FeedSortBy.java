package com.sprint.mission.otboo.domain.social.feed.dto;


import com.fasterxml.jackson.annotation.JsonProperty;

public enum FeedSortBy {
  @JsonProperty("createdAt")
  CREATED_AT("createdAt"),

  @JsonProperty("likeCount")
  LIKE_COUNT("likeCount");

  private final String param;

  FeedSortBy(String param) {
    this.param = param;
  }

  public String param() {
    return param;
  }
}