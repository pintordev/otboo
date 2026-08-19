-- KEYS[1] = auth:user-session-index:{userId}
-- KEYS[2] = auth:user-session:{userId}:{new sessionId} (새로 발급할 세션의 키)
-- ARGV[1] = auth:user-session:{userId}: (세션 키 접두사, 인덱스에서 읽은 sessionId와 이어붙여 기존 세션 키를 복원하는 데 씀)
-- ARGV[2] = maxDevices
-- ARGV[3] = new sessionId
-- ARGV[4] = new refreshJti
-- ARGV[5] = new issuedAt (Instant.toString())
-- ARGV[6] = new expireAt epoch millis
--
-- 이 유저의 세션 수가 maxDevices 이상이면 issuedAt이 가장 오래된 세션부터 회수해 정원을
-- 맞춘 뒤, 새 세션 하나를 발급한다. 조회(몇 개인지, 뭐가 가장 오래됐는지) -> 회수 -> 발급을
-- Redis 스크립트 하나로 묶어서, 기기 수 제한 근처에서 동시 로그인이 들어와도 각 요청이 서로
-- 다른 시점의 스냅샷을 보고 판단해 정원을 초과하거나 방금 발급한 세션을 지워버리는 레이스를 없앤다.
--
-- Instant.toString()(ISO-8601)은 문자열 사전식 비교가 시간 순서와 일치하므로 issuedAt을
-- 그대로 문자열로 비교해 정렬한다.

local indexKey = KEYS[1]
local newSessionKey = KEYS[2]
local sessionPrefix = ARGV[1]
local maxDevices = tonumber(ARGV[2])
local newSessionId = ARGV[3]
local newRefreshJti = ARGV[4]
local newIssuedAt = ARGV[5]
local newExpiresAtMillis = ARGV[6]

local existingIds = redis.call('ZRANGE', indexKey, 0, -1)
local sessions = {}
for _, sid in ipairs(existingIds) do
  local issuedAt = redis.call('HGET', sessionPrefix .. sid, 'issuedAt')
  if issuedAt then
    table.insert(sessions, {id = sid, issuedAt = issuedAt})
  else
    -- 세션 키는 TTL로 이미 사라졌는데 인덱스 엔트리만 남은 좀비 -> 이 김에 같이 청소한다.
    redis.call('ZREM', indexKey, sid)
  end
end

if #sessions >= maxDevices then
  table.sort(sessions, function(a, b) return a.issuedAt < b.issuedAt end)
  local evictCount = #sessions - maxDevices + 1
  for i = 1, evictCount do
    local victim = sessions[i]
    redis.call('DEL', sessionPrefix .. victim.id)
    redis.call('ZREM', indexKey, victim.id)
  end
end

redis.call('HSET', newSessionKey, 'refreshJti', newRefreshJti, 'issuedAt', newIssuedAt)
redis.call('PEXPIREAT', newSessionKey, newExpiresAtMillis)
redis.call('ZADD', indexKey, newExpiresAtMillis, newSessionId)

-- 인덱스 TTL은 이 유저가 가진 세션들 중 가장 늦게 끝나는 시각에 맞춘다 (save()의 refreshIndexExpiry와 동일한 정책).
local highest = redis.call('ZREVRANGE', indexKey, 0, 0, 'WITHSCORES')
local maxExpiresAtMillis = newExpiresAtMillis
if highest[2] and tonumber(highest[2]) > tonumber(maxExpiresAtMillis) then
  maxExpiresAtMillis = highest[2]
end
redis.call('PEXPIREAT', indexKey, maxExpiresAtMillis)

return 1
