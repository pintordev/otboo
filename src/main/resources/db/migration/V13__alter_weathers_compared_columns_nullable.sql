ALTER TABLE weathers
    ALTER COLUMN temperature_compared DROP NOT NULL;

ALTER TABLE weathers
    ALTER COLUMN humidity_compared DROP NOT NULL;