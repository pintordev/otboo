-- 재색인 배치 쿼리용 인덱스.
-- 두 쿼리 모두 deleted_at IS NULL이 붙으므로 partial index로 좁힌다.
-- 활성 피드만 대상이라 소프트 삭제가 쌓여도 인덱스 크기가 늘지 않는다.

-- 전체 재색인: WHERE deleted_at IS NULL AND (created_at, id) > (?, ?) ORDER BY created_at, id
CREATE INDEX idx_feeds_active_created_at_id
    ON feeds (created_at, id)
    WHERE deleted_at IS NULL;

-- 증분 재색인: WHERE deleted_at IS NULL AND updated_at >= ? AND (updated_at, id) > (?, ?)
--             ORDER BY updated_at, id
CREATE INDEX idx_feeds_active_updated_at_id
    ON feeds (updated_at, id)
    WHERE deleted_at IS NULL;
