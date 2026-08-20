-- KEYS[1] = sse:message-index
-- ARGV[1] = lastEventId (재생 시작 기준이 되는 마지막으로 수신한 메시지 id)
-- ARGV[2] = maxReplaySize (재생 상한 건수)
--
-- ZRANK로 lastEventId의 순위를 구하고 그 바로 뒤 구간을 ZRANGE로 읽는 두 단계를
-- 하나의 스크립트로 묶어, 그 사이 다른 인스턴스의 evictExpired()가 인덱스를 지워
-- 순위가 밀리는 상황을 막는다.
--
-- return  lastEventId 이후 메시지 id 목록 (상한 초과 시 최신 것부터 상한 개수만)
-- return  lastEventId가 인덱스에 없으면 빈 배열

local indexKey = KEYS[1]
local lastEventId = ARGV[1]
local maxReplaySize = tonumber(ARGV[2])

local rank = redis.call('ZRANK', indexKey, lastEventId)
if not rank then
  return {}
end

local from = rank + 1
local size = redis.call('ZCARD', indexKey)
if size - from > maxReplaySize then
  from = size - maxReplaySize
end

return redis.call('ZRANGE', indexKey, from, -1)