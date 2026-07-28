-- location_blocks를 locations로 리네임 — WeatherGrid(날씨 캐싱용 격자)와 분리된
-- "실제 위치"(좌표 블록 기준 행정구역명 캐시) 개념이 이 이름을 갖는 게 맞음 (erd.md 설계 노트 15)
ALTER TABLE location_blocks RENAME TO locations;
ALTER TABLE locations RENAME CONSTRAINT PK_LOCATION_BLOCKS TO PK_LOCATIONS;
ALTER TABLE locations
    RENAME CONSTRAINT UQ_location_blocks_lat_block_lon_block TO UQ_locations_lat_block_lon_block;