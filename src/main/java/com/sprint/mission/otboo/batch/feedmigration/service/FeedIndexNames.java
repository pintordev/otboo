package com.sprint.mission.otboo.batch.feedmigration.service;

import com.sprint.mission.otboo.batch.feedmigration.exception.FeedIndexNameException;
import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 피드 검색 인덱스의 세대별 이름을 다룬다.
 *
 * <p>인덱스는 {@code feeds_v{n}}으로 만들고 {@code feeds}를 alias로 붙인다. 버전을 이름에 실어
 * 현재 alias가 가리키는 인덱스에서 다음 이름을 계산한다.
 */
public final class FeedIndexNames {

  private static final String PREFIX = FeedDocument.INDEX_NAME + "_v";
  private static final Pattern VERSIONED = Pattern.compile("^" + PREFIX + "(\\d+)$");

  // 전환 직후 한 세대를 남겨, 문제가 생기면 alias만 되돌려 복구할 수 있게 한다.
  private static final int GENERATIONS_TO_KEEP = 1;

  private FeedIndexNames() {
  }

  public static String nextVersionOf(String currentIndexName) {
    return PREFIX + (versionOf(currentIndexName) + 1);
  }

  /**
   * 전환 후 지워도 되는 인덱스 이름을 반환한다. 남길 세대가 없으면 비어 있다.
   */
  public static Optional<String> indexToDelete(String newIndexName) {
    int obsolete = versionOf(newIndexName) - GENERATIONS_TO_KEEP - 1;
    if (obsolete < 1) {
      return Optional.empty();
    }
    return Optional.of(PREFIX + obsolete);
  }

  // 이름 규칙이 깨지면 엉뚱한 인덱스를 만들거나 지우게 되므로 예외로 중단한다.
  private static int versionOf(String indexName) {
    Matcher matcher = VERSIONED.matcher(indexName == null ? "" : indexName);
    if (!matcher.matches()) {
      throw FeedIndexNameException.of(indexName, PREFIX + "{n}");
    }
    return Integer.parseInt(matcher.group(1));
  }
}
