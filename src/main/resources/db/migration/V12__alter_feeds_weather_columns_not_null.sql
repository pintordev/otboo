-- FeedCreateRequest.weatherId가 @NotNull이고 Feed.create가 weatherSnapshot을 필수로 강제하므로
-- 컬럼 제약도 동일하게 맞춘다. 락 획득을 한 번으로 줄이기 위해 한 문장으로 묶는다.

ALTER TABLE feeds
    ALTER COLUMN weather_id SET NOT NULL,
    ALTER COLUMN sky_status SET NOT NULL,
    ALTER COLUMN precipitation_type SET NOT NULL,
    ALTER COLUMN precipitation_amount SET NOT NULL,
    ALTER COLUMN precipitation_probability SET NOT NULL,
    ALTER COLUMN temperature_current SET NOT NULL,
    ALTER COLUMN temperature_compared SET NOT NULL,
    ALTER COLUMN temperature_min SET NOT NULL,
    ALTER COLUMN temperature_max SET NOT NULL;
