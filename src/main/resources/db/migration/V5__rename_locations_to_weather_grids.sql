-- locations를 weather_grids로 리네임하고 순수 격자(x, y) 식별 전용으로 정리
-- 위경도/행정구역명은 별도 테이블(location_blocks, 추후 locations로 리네임 예정)이 전담 (erd.md 설계 노트 15)
-- Weather 응답 조립 시 latitude/longitude/location_names는 어디서도 읽지 않는 값이 되어 제거
ALTER TABLE locations RENAME TO weather_grids;
ALTER TABLE weather_grids RENAME CONSTRAINT PK_LOCATIONS TO PK_WEATHER_GRIDS;
ALTER TABLE weather_grids RENAME CONSTRAINT UQ_locations_x_y TO UQ_weather_grids_x_y;
ALTER TABLE weather_grids
    DROP COLUMN latitude,
    DROP COLUMN longitude,
    DROP COLUMN location_names,
    DROP COLUMN updated_at;

ALTER TABLE weathers RENAME COLUMN location_id TO weather_grid_id;
ALTER TABLE weathers RENAME CONSTRAINT FK_locations_TO_weathers_1 TO FK_weather_grids_TO_weathers_1;
ALTER INDEX IDX_weathers_location_id RENAME TO IDX_weathers_weather_grid_id;