// k6/scripts/phase2_concurrency.js
// Concurrency Test — 완전히 동시에(http.batch) 같은 자원을 건드렸을 때 정합성이 지켜지는지 확인.
// 세 가지를 각각 검증한다:
//   1. followRace  — FollowService.findOrCreateFollow(유니크 제약 위반 catch 후 같은 트랜잭션에서
//      폴백 SELECT를 하는 코드) — 이미 확인된 버그, 재확인용
//   2. signupRace  — UserService.signUp(유니크 제약 위반을 catch해서 바로 예외만 던짐, 폴백 쿼리 없음)
//      — 코드 리뷰로는 안전해 보이는 패턴이 실제로도 안전한지 검증
//   3. likeRace    — FeedService.like()/saveFeedLike(유니크 제약 위반을 catch해서 false만 반환, 폴백
//      쿼리 없음) — 마찬가지로 안전해 보이는 패턴을 실측 검증
// 세 시나리오 모두 매번 새 계정/자원이 필요해서 시드 유저 풀을 재사용하지 않는다.
import http from 'k6/http';
import {check} from 'k6';
import {ENV} from './config/env.js';
import {concurrencyOptions} from './config/options.js';
import {signUpAndSignIn, authHeaders, getCsrfToken} from './helpers/auth.js';
import {loginTestUserPool, firstId} from './helpers/data.js';

export const options = concurrencyOptions;

export function setup() {
  const pool = loginTestUserPool(ENV.BASE_URL);
  const headers = authHeaders(pool[0]);
  const feedsRes = http.get(`${ENV.BASE_URL}/api/feeds?limit=1&sortBy=CREATED_AT&sortDirection=DESCENDING`, {headers});
  const feedId = firstId(feedsRes);
  if (!feedId) {
    throw new Error('좋아요 동시성 테스트용 기존 피드를 못 찾음 — 시드 데이터 확인 필요');
  }
  return {feedId};
}

function fire(requests) {
  const responses = http.batch(requests);
  return {
    successCount: responses.filter((r) => r.status === 201 || r.status === 200 || r.status === 204).length,
    serverErrorCount: responses.filter((r) => r.status >= 500).length,
  };
}

// 1. 팔로우 동시 생성 — 이미 확인된 버그(같은 트랜잭션 안 폴백 SELECT로 인한 abort 연쇄) 재확인용
export function followRace() {
  const followerEmail = `k6-concur-follower-${__VU}-${__ITER}-${Date.now()}@test.com`;
  const followeeEmail = `k6-concur-followee-${__VU}-${__ITER}-${Date.now()}@test.com`;

  const followerSession = signUpAndSignIn(ENV.BASE_URL, followerEmail, 'perf1234', '팔로워');
  const followeeSession = signUpAndSignIn(ENV.BASE_URL, followeeEmail, 'perf1234', '팔로위');

  const headers = Object.assign({'Content-Type': 'application/json'}, authHeaders(followerSession));
  const body = JSON.stringify({followerId: followerSession.userId, followeeId: followeeSession.userId});

  const requests = Array.from({length: 10}, () => ({
    method: 'POST',
    url: `${ENV.BASE_URL}/api/follows`,
    body,
    params: {headers, tags: {name: 'POST /follows(concurrent)'}},
  }));

  const {successCount, serverErrorCount} = fire(requests);
  check(null, {
    'follow 500 에러 없음': () => serverErrorCount === 0,
    'follow 전부 성공 응답': () => successCount === 10,
  });
  console.log(`[follow] follower=${followerSession.userId} followee=${followeeSession.userId} success=${successCount} 5xx=${serverErrorCount}`);
}

// 2. 회원가입 이메일 동시 중복 — UserService.signUp이 폴백 쿼리 없이 즉시 예외만 던지는 패턴이 실제로 안전한지 검증
export function signupRace() {
  const email = `k6-concur-signup-${__VU}-${__ITER}-${Date.now()}@test.com`;
  const csrfToken = getCsrfToken(ENV.BASE_URL);
  const headers = {'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfToken};
  const body = JSON.stringify({email, password: 'perf1234', name: '동시가입테스트'});

  const requests = Array.from({length: 10}, () => ({
    method: 'POST',
    url: `${ENV.BASE_URL}/api/users`,
    body,
    params: {headers, tags: {name: 'POST /users(concurrent)'}},
  }));

  const responses = http.batch(requests);
  const successCount = responses.filter((r) => r.status === 201).length;
  const conflictCount = responses.filter((r) => r.status === 409).length;
  const serverErrorCount = responses.filter((r) => r.status >= 500).length;

  check(null, {
    'signup 500 에러 없음': () => serverErrorCount === 0,
    'signup 정확히 1개만 성공': () => successCount === 1,
  });
  console.log(`[signup] email=${email} success=${successCount} conflict409=${conflictCount} 5xx=${serverErrorCount}`);
}

// 3. 같은 피드에 같은 유저가 동시 좋아요 — FeedService.saveFeedLike가 폴백 쿼리 없이 false만 반환하는
// 패턴이 실제로 안전한지 검증
export function likeRace(data) {
  const email = `k6-concur-like-${__VU}-${__ITER}-${Date.now()}@test.com`;
  const session = signUpAndSignIn(ENV.BASE_URL, email, 'perf1234', '좋아요동시테스트');
  const csrfToken = getCsrfToken(ENV.BASE_URL);
  const headers = Object.assign({'X-XSRF-TOKEN': csrfToken}, authHeaders(session));

  const requests = Array.from({length: 10}, () => ({
    method: 'POST',
    url: `${ENV.BASE_URL}/api/feeds/${data.feedId}/like`,
    body: null,
    params: {headers, tags: {name: 'POST /feeds/like(concurrent)'}},
  }));

  const {successCount, serverErrorCount} = fire(requests);
  check(null, {
    'like 500 에러 없음': () => serverErrorCount === 0,
  });
  console.log(`[like] feedId=${data.feedId} user=${session.userId} success=${successCount} 5xx=${serverErrorCount}`);
}
