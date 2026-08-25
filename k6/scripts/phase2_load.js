// k6/scripts/phase2_load.js
// Load Test — VU 50(정상 부하)에서 API별 threshold를 지키는지 확인.
// 목적은 붕괴점 탐색이 아니라 "정상 부하에서도 threshold를 넘는 API가 있는가 = 병목 후보 식별".
// 한계점 탐색은 phase2_stress.js로 분리.
import http from 'k6/http';
import {check, sleep} from 'k6';
import {ENV} from './config/env.js';
import {loadOptions} from './config/options.js';
import {getCsrfToken, authHeaders} from './helpers/auth.js';
import {loginTestUserPool, firstId} from './helpers/data.js';

export const options = loadOptions;

export function setup() {
  const pool = loginTestUserPool(ENV.BASE_URL);
  const h0 = authHeaders(pool[0]);
  const weatherRes = http.get(`${ENV.BASE_URL}/api/weathers?latitude=37.5665&longitude=126.9780`, {headers: h0});
  const weatherId = JSON.parse(weatherRes.body)[0].id;
  return {pool, weatherId};
}

function jsonHeaders(session, csrf) {
  return Object.assign({'Content-Type': 'application/json'}, authHeaders(session, {'X-XSRF-TOKEN': csrf}));
}

export default function (data) {
  const idx = (__VU - 1) % data.pool.length;
  const s = data.pool[idx];
  const headers = authHeaders(s);

  check(http.get(`${ENV.BASE_URL}/api/users/${s.userId}/profiles`,
      {headers, tags: {name: 'GET /users/profiles'}}), {'profile 200': (r) => r.status === 200});
  sleep(0.3);

  const clothesRes = http.get(`${ENV.BASE_URL}/api/clothes?ownerId=${s.userId}&limit=5`,
      {headers, tags: {name: 'GET /clothes'}});
  check(clothesRes, {'clothes 200': (r) => r.status === 200});
  const clothesId = firstId(clothesRes);
  sleep(0.3);

  check(http.get(`${ENV.BASE_URL}/api/feeds?limit=20&sortBy=CREATED_AT&sortDirection=DESCENDING`,
      {headers, tags: {name: 'GET /feeds'}}), {'feeds 200': (r) => r.status === 200});
  sleep(0.3);

  if (clothesId) {
    const csrf = getCsrfToken(ENV.BASE_URL);
    const createRes = http.post(`${ENV.BASE_URL}/api/feeds`,
        JSON.stringify({authorId: s.userId, weatherId: data.weatherId, clothesIds: [clothesId], content: `k6-load-${__VU}-${__ITER}-${Date.now()}`}),
        {headers: jsonHeaders(s, csrf), tags: {name: 'POST /feeds'}});
    check(createRes, {'feed create 201': (r) => r.status === 201});

    if (createRes.status === 201) {
      const feedId = JSON.parse(createRes.body).id;
      const csrf2 = getCsrfToken(ENV.BASE_URL);
      check(http.post(`${ENV.BASE_URL}/api/feeds/${feedId}/like`, null,
          {headers: jsonHeaders(s, csrf2), tags: {name: 'POST /feeds/like'}}), {'like 204': (r) => r.status === 204});
    }
  }
  sleep(0.3);

  const target = data.pool[(idx + 1) % data.pool.length];
  const csrf3 = getCsrfToken(ENV.BASE_URL);
  check(http.post(`${ENV.BASE_URL}/api/follows`,
      JSON.stringify({followerId: s.userId, followeeId: target.userId}),
      {headers: jsonHeaders(s, csrf3), tags: {name: 'POST /follows'}}), {'follow 201/200': (r) => r.status === 201 || r.status === 200});
  sleep(0.3);

  check(http.get(`${ENV.BASE_URL}/api/notifications?limit=20`,
      {headers, tags: {name: 'GET /notifications'}}), {'notifications 200': (r) => r.status === 200});
  sleep(0.5);
}
