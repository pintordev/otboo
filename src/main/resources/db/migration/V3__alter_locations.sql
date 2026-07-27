-- locations 테이블 (x, y) 유니크 제약 추가 — 동시 요청 시 중복 Location 생성 방지
-- UNIQUE 제약이 자체적으로 유니크 인덱스를 생성하므로, V1의 일반 인덱스(IDX_locations_x_y)는 중복이라 제거

DROP INDEX IDX_locations_x_y;

ALTER TABLE locations
    ADD CONSTRAINT UQ_locations_x_y UNIQUE (x, y);