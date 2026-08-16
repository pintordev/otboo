DROP TABLE weather_change_notification_logs;

CREATE TABLE weather_d1_baselines
(
    id                UUID                     NOT NULL DEFAULT gen_random_uuid(),
    weather_grid_id   UUID                     NOT NULL,
    target_date       DATE                     NOT NULL,
    hourly_snapshot   JSONB                    NOT NULL,
    captured_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT PK_weather_d1_baselines PRIMARY KEY (id),
    CONSTRAINT UQ_weather_d1_baselines_weather_grid_id_target_date
        UNIQUE (weather_grid_id, target_date),
    CONSTRAINT FK_weather_grids_TO_weather_d1_baselines_1
        FOREIGN KEY (weather_grid_id) REFERENCES weather_grids (id)
);