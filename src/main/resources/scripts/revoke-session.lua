-- KEYS[1] = auth:user-session:{userId}:{sessionId} (회수 대상 세션)
-- KEYS[2] = auth:user-session-index:{userId}
-- ARGV[1] = sessionId
--
-- 세션 키 삭제와 인덱스에서의 제거를 하나의 원자적 연산으로 묶는다.

local sessionKey = KEYS[1]
local indexKey = KEYS[2]
local sessionId = ARGV[1]

redis.call('DEL', sessionKey)
redis.call('ZREM', indexKey, sessionId)

return 1
