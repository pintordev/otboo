UPDATE weathers SET
    baseline_temperature_current = COALESCE(baseline_temperature_current, temperature_current),
    baseline_precipitation_type = COALESCE(baseline_precipitation_type, precipitation_type),
    baseline_precipitation_probability = COALESCE(baseline_precipitation_probability, precipitation_probability),
    baseline_precipitation_amount = COALESCE(baseline_precipitation_amount, precipitation_amount)
WHERE baseline_temperature_current IS NULL
   OR baseline_precipitation_type IS NULL
   OR baseline_precipitation_probability IS NULL
   OR baseline_precipitation_amount IS NULL;

ALTER TABLE weathers
    ALTER COLUMN baseline_temperature_current SET NOT NULL,
    ALTER COLUMN baseline_precipitation_type SET NOT NULL,
    ALTER COLUMN baseline_precipitation_probability SET NOT NULL,
    ALTER COLUMN baseline_precipitation_amount SET NOT NULL;