-- KEYS[1] = auth:user-session-index:{userId}
-- ARGV[1] = auth:user-session:{userId}: (세션 키 접두사, 인덱스에서 읽은 sessionId와 이어붙여 세션 키를 복원하는 데 씀)
--
-- 이 유저의 세션을 전부 삭제한다. 인덱스 조회부터 각 세션 키 삭제, 인덱스 키 자체 삭제까지
-- 하나의 스크립트로 원자적으로 처리되므로, 실행 도중에는 다른 요청이 끼어들어 인덱스에 새
-- 세션을 추가할 수 없다. 따라서 (기존의 부분 삭제 방식과 달리) 인덱스 키 자체를 통째로
-- 지워도 방금 추가된 세션의 인덱스 엔트리까지 같이 날아가는 좀비 세션 문제가 생기지 않는다.

local indexKey = KEYS[1]
local sessionPrefix = ARGV[1]

local sessionIds = redis.call('ZRANGE', indexKey, 0, -1)
for _, sid in ipairs(sessionIds) do
  redis.call('DEL', sessionPrefix .. sid)
end
redis.call('DEL', indexKey)

return #sessionIds
