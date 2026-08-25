// k6/scripts/phase2_scenario.js
// Scenario Test — 흐름별(조회 위주 vs 쓰기 위주)로 나눠서 어느 흐름이 병목인지 구분해서 본다.
import http from 'k6/http';
import {check, sleep, group} from 'k6';
import {ENV} from './config/env.js';
import {scenarioOptions} from './config/options.js';
import {getCsrfToken, authHeaders} from './helpers/auth.js';
import {loginTestUserPool, firstId} from './helpers/data.js';

export const options = scenarioOptions;

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

function pickUser(data) {
  return data.pool[(__VU - 1) % data.pool.length];
}

// 신규 방문자처럼 조회만 하는 흐름
export function browseFlow(data) {
  const s = pickUser(data);
  const headers = authHeaders(s);

  group('browse', () => {
    check(http.get(`${ENV.BASE_URL}/api/users/${s.userId}/profiles`,
        {headers, tags: {flow: 'browse', name: 'GET /users/profiles'}}), {'profile 200': (r) => r.status === 200});
    sleep(0.5);

    check(http.get(`${ENV.BASE_URL}/api/clothes?ownerId=${s.userId}&limit=20`,
        {headers, tags: {flow: 'browse', name: 'GET /clothes'}}), {'clothes 200': (r) => r.status === 200});
    sleep(0.5);

    const feedRes = http.get(`${ENV.BASE_URL}/api/feeds?limit=20&sortBy=CREATED_AT&sortDirection=DESCENDING`,
        {headers, tags: {flow: 'browse', name: 'GET /feeds'}});
    check(feedRes, {'feeds 200': (r) => r.status === 200});
    sleep(0.5);

    const feedId = firstId(feedRes);
    if (feedId) {
      check(http.get(`${ENV.BASE_URL}/api/feeds/${feedId}/comments?limit=20`,
          {headers, tags: {flow: 'browse', name: 'GET /feeds/comments'}}), {'comments 200': (r) => r.status === 200});
      sleep(0.5);
    }

    check(http.get(`${ENV.BASE_URL}/api/notifications?limit=20`,
        {headers, tags: {flow: 'browse', name: 'GET /notifications'}}), {'notifications 200': (r) => r.status === 200});
  });

  sleep(1);
}

// 실제로 뭔가 만들고 상호작용하는 흐름
export function writeFlow(data) {
  const idx = (__VU - 1) % data.pool.length;
  const s = data.pool[idx];
  const headers = authHeaders(s);

  group('write', () => {
    const clothesRes = http.get(`${ENV.BASE_URL}/api/clothes?ownerId=${s.userId}&limit=5`,
        {headers, tags: {flow: 'write', name: 'GET /clothes'}});
    const clothesId = firstId(clothesRes);
    sleep(0.5);

    let feedId = null;
    if (clothesId) {
      const csrf = getCsrfToken(ENV.BASE_URL);
      const createRes = http.post(`${ENV.BASE_URL}/api/feeds`,
          JSON.stringify({authorId: s.userId, weatherId: data.weatherId, clothesIds: [clothesId], content: `k6-scenario-${__VU}-${__ITER}-${Date.now()}`}),
          {headers: jsonHeaders(s, csrf), tags: {flow: 'write', name: 'POST /feeds'}});
      check(createRes, {'feed create 201': (r) => r.status === 201});
      if (createRes.status === 201) feedId = JSON.parse(createRes.body).id;
      sleep(0.5);
    }

    if (feedId) {
      const csrf2 = getCsrfToken(ENV.BASE_URL);
      check(http.post(`${ENV.BASE_URL}/api/feeds/${feedId}/like`, null,
          {headers: jsonHeaders(s, csrf2), tags: {flow: 'write', name: 'POST /feeds/like'}}), {'like 204': (r) => r.status === 204});
      sleep(0.5);

      const target = data.pool[(idx + 1) % data.pool.length];
      const csrf3 = getCsrfToken(ENV.BASE_URL);
      check(http.post(`${ENV.BASE_URL}/api/feeds/${feedId}/comments`,
          JSON.stringify({feedId, authorId: target.userId, content: `k6 댓글 ${__ITER}`}),
          {headers: jsonHeaders(target, csrf3), tags: {flow: 'write', name: 'POST /feeds/comments'}}), {'comment 201': (r) => r.status === 201});
      sleep(0.5);
    }

    const target2 = data.pool[(idx + 1) % data.pool.length];
    const csrf4 = getCsrfToken(ENV.BASE_URL);
    check(http.post(`${ENV.BASE_URL}/api/follows`,
        JSON.stringify({followerId: s.userId, followeeId: target2.userId}),
        {headers: jsonHeaders(s, csrf4), tags: {flow: 'write', name: 'POST /follows'}}), {'follow 201/200': (r) => r.status === 201 || r.status === 200});
  });

  sleep(1);
}
