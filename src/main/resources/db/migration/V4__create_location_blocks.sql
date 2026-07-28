-- 응답용 행정구역명 캐시 (locations의 5km 격자와 분리, ~50m 단위 좌표 블록 기준)
-- 근거: docs/erd.md 설계 노트 15 — 하나의 격자에 여러 행정동이 걸쳐 locations.location_names를
-- 응답에 그대로 쓰면 잘못된 행정구역명이 내려가는 문제를 해결하기 위해 분리
CREATE TABLE location_blocks
(
    id             UUID                     NOT NULL,
    lat_block      INTEGER                  NOT NULL,
    lon_block      INTEGER                  NOT NULL,
    location_names JSONB,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT PK_LOCATION_BLOCKS PRIMARY KEY (id),
    CONSTRAINT UQ_location_blocks_lat_block_lon_block UNIQUE (lat_block, lon_block)
);