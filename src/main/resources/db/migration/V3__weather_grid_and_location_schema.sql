-- 기상청 격자(WeatherGrid, 5km, Weather FK 전용)와 위치명 캐시(Location, ~50m 좌표 블록)를
-- 완전히 분리 (erd.md 설계 노트 15) — 하나의 5km 격자에 여러 행정동(최대 28개)이 걸쳐
-- 격자 기준으로 캐싱한 행정구역명을 응답에 쓰면 잘못된 위치정보가 내려가는 문제를 해결

-- V1에서 만든 locations를 weather_grids로 정리: 순수 격자(x, y) 식별 전용
ALTER TABLE locations RENAME TO weather_grids;
ALTER TABLE weather_grids RENAME CONSTRAINT PK_LOCATIONS TO PK_WEATHER_GRIDS;
DROP INDEX IDX_locations_x_y;
ALTER TABLE weather_grids
    ADD CONSTRAINT UQ_weather_grids_x_y UNIQUE (x, y),
    DROP COLUMN latitude,
    DROP COLUMN longitude,
    DROP COLUMN location_names,
    DROP COLUMN updated_at;

ALTER TABLE weathers RENAME COLUMN location_id TO weather_grid_id;
ALTER TABLE weathers RENAME CONSTRAINT FK_locations_TO_weathers_1 TO FK_weather_grids_TO_weathers_1;
ALTER INDEX IDX_weathers_location_id RENAME TO IDX_weathers_weather_grid_id;

-- 응답용 행정구역명 캐시 (weather_grids와 분리, ~50m 단위 좌표 블록 기준)
CREATE TABLE locations
(
    id             UUID                     NOT NULL,
    lat_block      INTEGER                  NOT NULL,
    lon_block      INTEGER                  NOT NULL,
    location_names JSONB,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT PK_LOCATIONS PRIMARY KEY (id),
    CONSTRAINT UQ_locations_lat_block_lon_block UNIQUE (lat_block, lon_block)
);