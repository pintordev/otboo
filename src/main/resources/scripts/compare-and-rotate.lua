-- KEYS[1] = auth:user-session:{userId}:{sessionId} (rotate 대상 세션)
-- KEYS[2] = auth:user-session-index:{userId}
-- ARGV[1] = auth:user-session:{userId}: (세션 키 접두사, 재사용 감지 시 전체 세션 삭제에 사용)
-- ARGV[2] = sessionId
-- ARGV[3] = expected refreshJti (요청에 실려온 refresh token의 jti)
-- ARGV[4] = new refreshJti (rotate 성공 시 심을 새 jti)
-- ARGV[5] = new expireAt epoch millis (Java에서 정책 계산까지 마친 값)
--
-- return  1 : rotate 성공 (jti 교체 + TTL 갱신까지 원자적으로 완료)
-- return  0 : 세션 없음 (TTL 만료 / 이미 회수됨) -> UserSessionExpiredException
-- return -1 : refreshJti 불일치 (재사용 의심) -> 이 유저의 모든 세션 삭제 후 RefreshTokenReusedException

local sessionKey = KEYS[1]
local indexKey = KEYS[2]
local sessionPrefix = ARGV[1]
local sessionId = ARGV[2]
local expectedRefreshJti = ARGV[3]
local newRefreshJti = ARGV[4]
local newExpiresAtMillis = ARGV[5]

local storedJti = redis.call('HGET', sessionKey, 'refreshJti')
if not storedJti then
  return 0
end

if storedJti ~= expectedRefreshJti then
  local existing = redis.call('ZRANGE', indexKey, 0, -1)
  for _, sid in ipairs(existing) do
    redis.call('DEL', sessionPrefix .. sid)
  end
  redis.call('DEL', indexKey)
  return -1
end

redis.call('HSET', sessionKey, 'refreshJti', newRefreshJti)
redis.call('PEXPIREAT', sessionKey, newExpiresAtMillis)
redis.call('ZADD', indexKey, newExpiresAtMillis, sessionId)

-- 인덱스 TTL은 이 유저가 가진 세션들 중 가장 늦게 끝나는 시각에 맞춘다 (save()의 refreshIndexExpiry와 동일한 정책).
local highest = redis.call('ZREVRANGE', indexKey, 0, 0, 'WITHSCORES')
local maxExpiresAtMillis = newExpiresAtMillis
if highest[2] and tonumber(highest[2]) > tonumber(maxExpiresAtMillis) then
  maxExpiresAtMillis = highest[2]
end
redis.call('PEXPIREAT', indexKey, maxExpiresAtMillis)

return 1
