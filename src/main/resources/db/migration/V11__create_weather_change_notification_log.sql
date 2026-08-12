CREATE TABLE weather_change_notification_logs
(
    id                           UUID                     NOT NULL DEFAULT gen_random_uuid(),
    weather_grid_id              UUID                     NOT NULL,
    forecast_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    last_notified_forecasted_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at                   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT PK_weather_change_notification_logs PRIMARY KEY (id),
    CONSTRAINT UQ_weather_change_notification_logs_grid_forecast_at
        UNIQUE (weather_grid_id, forecast_at),
    CONSTRAINT FK_weather_grids_TO_weather_change_notification_logs_1
        FOREIGN KEY (weather_grid_id) REFERENCES weather_grids (id)
);

-- WeatherSuddenChangeNotifier.publish()가 격자마다 위치 기준으로 profiles를 조회한다
-- (CodeRabbit PR #131 리뷰) - 인덱스 없이는 매 회차 격자 수만큼 전체 스캔이 반복된다
CREATE INDEX IDX_profiles_location_x_location_y ON profiles (location_x, location_y);