// k6/scripts/phase2_stress.js
// Stress Test — VU 10->300 계단식으로 올려서 "어디서부터 무너지는가"를 관찰한다.
// Load Test(VU50 고정)와 달리 SLA 준수가 목적이 아니라 붕괴점 자체가 결과물이므로 엄격한 threshold는 없다.
// throwaway 계정 풀을 쓴다 — 시드 유저 20명만으로는 VU300 동시 접속을 대표하기 어렵고,
// 이 테스트 자체가 "얼마나 많은 동시 사용자를 버티는가"를 보는 것이라 숫자 자체가 많아야 한다.
import http from 'k6/http';
import {check, sleep} from 'k6';
import {ENV} from './config/env.js';
import {stressOptions} from './config/options.js';
import {getCsrfToken, authHeaders} from './helpers/auth.js';
import {createPool, fetchWeatherId, createAttributeDef, createClothes} from './helpers/data.js';

export const options = stressOptions;
const POOL_SIZE = 60;

export function setup() {
  const pool = createPool(ENV.BASE_URL, 'stress', POOL_SIZE);
  const h0 = authHeaders(pool[0]);
  const weatherId = fetchWeatherId(ENV.BASE_URL, h0);
  createAttributeDef(ENV.BASE_URL, h0, `스트레스속성-${Date.now()}`);
  const clothesIds = pool.map((s, i) => createClothes(ENV.BASE_URL, authHeaders(s), s.userId, `스트레스옷-${i}`));
  return {pool, weatherId, clothesIds};
}

function jsonHeaders(session, csrf) {
  return Object.assign({'Content-Type': 'application/json'}, authHeaders(session, {'X-XSRF-TOKEN': csrf}));
}

export default function (data) {
  const idx = (__VU - 1) % data.pool.length;
  const s = data.pool[idx];
  const clothesId = data.clothesIds[idx];
  const headers = authHeaders(s);

  // 붕괴 구간(VU130~140 이상)에서는 getCsrfToken()이 쿠키를 못 받아 예외를 던질 수 있다 —
  // 이건 이 테스트가 관찰하려는 대상 자체이므로, try/catch 없이 그대로 두면 매 iteration이
  // 스크립트 예외로 죽어서 (a) sleep 없이 폭주하고 (b) 콘솔이 같은 에러로 도배된다.
  // phase2_auth.js가 이미 겪은 문제라 같은 패턴(try/finally로 항상 sleep 보장)을 적용한다.
  try {
    check(http.get(`${ENV.BASE_URL}/api/users/${s.userId}/profiles`,
        {headers, tags: {name: 'GET /users/profiles'}}), {'profile 200': (r) => r.status === 200});

    check(http.get(`${ENV.BASE_URL}/api/clothes?ownerId=${s.userId}&limit=20`,
        {headers, tags: {name: 'GET /clothes'}}), {'clothes 200': (r) => r.status === 200});

    check(http.get(`${ENV.BASE_URL}/api/feeds?limit=20&sortBy=CREATED_AT&sortDirection=DESCENDING`,
        {headers, tags: {name: 'GET /feeds'}}), {'feeds 200': (r) => r.status === 200});

    const csrf = getCsrfToken(ENV.BASE_URL);
    const createRes = http.post(`${ENV.BASE_URL}/api/feeds`,
        JSON.stringify({authorId: s.userId, weatherId: data.weatherId, clothesIds: [clothesId], content: `k6-stress-${__VU}-${__ITER}-${Date.now()}`}),
        {headers: jsonHeaders(s, csrf), tags: {name: 'POST /feeds'}});
    check(createRes, {'feed create 201': (r) => r.status === 201});

    const target = data.pool[(idx + 1) % data.pool.length];
    const csrf2 = getCsrfToken(ENV.BASE_URL);
    check(http.post(`${ENV.BASE_URL}/api/follows`,
        JSON.stringify({followerId: s.userId, followeeId: target.userId}),
        {headers: jsonHeaders(s, csrf2), tags: {name: 'POST /follows'}}), {'follow 201/200': (r) => r.status === 201 || r.status === 200});

    check(http.get(`${ENV.BASE_URL}/api/notifications?limit=20`,
        {headers, tags: {name: 'GET /notifications'}}), {'notifications 200': (r) => r.status === 200});
  } catch (e) {
    check(null, {'예외 없이 완료': () => false});
  } finally {
    sleep(0.3);
  }
}
