CREATE TABLE weather_change_notification_logs
(
    id                           UUID                     NOT NULL DEFAULT gen_random_uuid(),
    weather_grid_id              UUID                     NOT NULL,
    forecast_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    last_notified_forecasted_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT PK_weather_change_notification_logs PRIMARY KEY (id),
    CONSTRAINT UQ_weather_change_notification_logs_grid_forecast_at
        UNIQUE (weather_grid_id, forecast_at),
    CONSTRAINT FK_weather_grids_TO_weather_change_notification_logs_1
        FOREIGN KEY (weather_grid_id) REFERENCES weather_grids (id)
);