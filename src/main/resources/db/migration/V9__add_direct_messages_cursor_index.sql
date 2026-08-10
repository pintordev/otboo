-- DM 대화 내역 조회는 양방향 조건 (sender=A AND receiver=B) OR (sender=B AND receiver=A) 이며
-- createdAt DESC + id DESC 커서 정렬을 사용한다.
-- sender_id·receiver_id 모두 등등 조건이므로 복합 인덱스 하나로 양방향을 모두 탐색할 수 있고,
-- V1의 (sender_id, receiver_id) 인덱스는 이 인덱스의 접두사라 중복이므로 제거한다.

DROP INDEX IF EXISTS IDX_direct_messages_sender_id_receiver_id;

CREATE INDEX IDX_direct_messages_sender_receiver_created_id
    ON direct_messages (sender_id, receiver_id, created_at DESC, id DESC);
