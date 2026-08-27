ALTER TABLE notifications
    ADD COLUMN sse_delivered_at TIMESTAMP WITH TIME ZONE;

-- follows(V5)/direct_messages(V9)와 동일 패턴 — receiver_id 등치 + created_at DESC, id DESC
-- 커서 정렬을 인덱스 하나로 커버한다. 기존 단일 컬럼 인덱스는 이 인덱스의 접두사라 중복이므로 제거.
DROP INDEX IF EXISTS IDX_notifications_receiver_id;

CREATE INDEX IDX_notifications_receiver_created_id
    ON notifications (receiver_id, created_at DESC, id DESC);