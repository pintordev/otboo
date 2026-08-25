-- event_id는 NULL 허용 — 기존에 이미 쌓인 notifications 행은 event_id가 없다.
-- PostgreSQL은 UNIQUE 제약에서 NULL끼리는 서로 다른 값으로 취급하므로(NULL <> NULL),
-- 기존 행이 전부 event_id=NULL이어도 제약 위반 없이 그대로 남는다. 이후 저장되는 행만
-- event_id가 채워지고 (event_id, receiver_id) 조합으로 실제 중복 방지가 걸린다.
-- 실제 기존 로컬 DB(notifications 2건, event_id=NULL)에 이 마이그레이션을 적용해 확인함.
CREATE TABLE notification_outboxes
(
    id           UUID                     NOT NULL,
    topic        VARCHAR(255)             NOT NULL,
    payload      TEXT                     NOT NULL,
    status       VARCHAR(20)              NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT PK_notification_outboxes PRIMARY KEY (id)
);

CREATE INDEX IDX_notification_outboxes_status_created_at
    ON notification_outboxes (status, created_at);

ALTER TABLE notifications
    ADD COLUMN event_id UUID;

ALTER TABLE notifications
    ADD CONSTRAINT UQ_notifications_event_id_receiver_id UNIQUE (event_id, receiver_id);