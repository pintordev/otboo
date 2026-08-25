UPDATE weathers SET
    baseline_temperature_current = temperature_current,
    baseline_precipitation_type = precipitation_type,
    baseline_precipitation_probability = precipitation_probability,
    baseline_precipitation_amount = precipitation_amount
WHERE baseline_temperature_current IS NULL;

ALTER TABLE weathers
    ALTER COLUMN baseline_temperature_current SET NOT NULL,
    ALTER COLUMN baseline_precipitation_type SET NOT NULL,
    ALTER COLUMN baseline_precipitation_probability SET NOT NULL,
    ALTER COLUMN baseline_precipitation_amount SET NOT NULL;