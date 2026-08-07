-- KEYS[1] = auth:user-session-index:{userId}
-- KEYS[2] = auth:user-session:{userId}:{new sessionId} (새로 발급할 세션의 키)
-- ARGV[1] = auth:user-session:{userId}: (세션 키 접두사, 인덱스에서 읽은 sessionId와 이어붙여 기존 세션 키를 복원하는 데 씀)
-- ARGV[2] = new sessionId
-- ARGV[3] = new refreshJti
-- ARGV[4] = new issuedAt (Instant.toString())
-- ARGV[5] = new expireAt epoch millis
--
-- 이 유저의 기존 세션을 전부 지우고 새 세션 하나만 남긴다 (단일 기기 로그인).
-- Redis는 스크립트 실행 도중 다른 명령이 끼어들 수 없으므로, 동시에 여러 로그인이
-- 들어와도 마지막에 실행된 스크립트가 만든 세션 하나만 최종적으로 남는다.

local indexKey = KEYS[1]
local newSessionKey = KEYS[2]
local sessionPrefix = ARGV[1]
local newSessionId = ARGV[2]
local newRefreshJti = ARGV[3]
local newIssuedAt = ARGV[4]
local newExpiresAtMillis = ARGV[5]

local existing = redis.call('ZRANGE', indexKey, 0, -1)
for _, sid in ipairs(existing) do
  redis.call('DEL', sessionPrefix .. sid)
end
redis.call('DEL', indexKey)

redis.call('HSET', newSessionKey, 'refreshJti', newRefreshJti, 'issuedAt', newIssuedAt)
redis.call('PEXPIREAT', newSessionKey, newExpiresAtMillis)
redis.call('ZADD', indexKey, newExpiresAtMillis, newSessionId)
redis.call('PEXPIREAT', indexKey, newExpiresAtMillis)

return 1
