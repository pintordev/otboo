ALTER TABLE weathers
    ADD CONSTRAINT UQ_weathers_weather_grid_id_forecast_at_forecasted_at
    UNIQUE (weather_grid_id, forecast_at, forecasted_at);