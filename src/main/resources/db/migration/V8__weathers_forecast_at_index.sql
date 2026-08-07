-- retention 배치의 커서 페이지네이션(forecast_at, id)이 매 회차 전체 weathers를 스캔하지 않도록 인덱스 추가.
-- V7의 유니크 제약(weather_grid_id, forecast_at, forecasted_at)은 weather_grid_id로 시작해
-- 격자를 가로지르는 forecast_at 단독 조회엔 쓸 수 없다.

CREATE INDEX IDX_weathers_forecast_at_id ON weathers (forecast_at, id);