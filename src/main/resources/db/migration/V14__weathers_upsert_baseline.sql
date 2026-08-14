ALTER TABLE weathers DROP CONSTRAINT UQ_weathers_weather_grid_id_forecast_at_forecasted_at;
ALTER TABLE weathers ADD CONSTRAINT UQ_weathers_weather_grid_id_forecast_at
    UNIQUE (weather_grid_id, forecast_at);

ALTER TABLE weathers
    ADD COLUMN baseline_temperature_current DOUBLE PRECISION,
    ADD COLUMN baseline_precipitation_type VARCHAR(20),
    ADD COLUMN baseline_precipitation_probability DOUBLE PRECISION,
    ADD COLUMN baseline_precipitation_amount DOUBLE PRECISION;