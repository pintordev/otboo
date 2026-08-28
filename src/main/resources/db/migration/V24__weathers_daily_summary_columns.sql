ALTER TABLE weathers
    ADD COLUMN sky_status_worst        VARCHAR(20) CHECK (sky_status_worst IN ('CLEAR', 'MOSTLY_CLOUDY', 'CLOUDY')),
    ADD COLUMN precipitation_type_mode VARCHAR(20) CHECK (precipitation_type_mode IN ('NONE', 'RAIN', 'RAIN_SNOW', 'SNOW', 'SHOWER')),
    ADD COLUMN humidity_max            DOUBLE PRECISION;