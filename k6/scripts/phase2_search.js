// k6/scripts/phase2_search.js
// Search Performance Test — 키워드 검색(ES match 쿼리) vs 전체 조회(matchAll) 오버헤드 비교.
// 시딩된 피드가 전부 "더미 피드 내용 N" 형태라 "더미"로 검색하면 전량이 매칭됨 —
// 결과 건수가 아니라 "match 절이 붙었을 때 자체의 오버헤드"를 비교하는 게 목적.
// VU20이라 시드 유저 풀(20명)을 그대로 재사용한다.
import http from 'k6/http';
import {check} from 'k6';
import {ENV} from './config/env.js';
import {searchOptions} from './config/options.js';
import {authHeaders} from './helpers/auth.js';
import {loginTestUserPool} from './helpers/data.js';

export const options = searchOptions;

export function setup() {
  const pool = loginTestUserPool(ENV.BASE_URL);
  return {pool};
}

function pick(data) {
  return data.pool[(__VU - 1) % data.pool.length];
}

export function withKeyword(data) {
  const s = pick(data);
  const headers = authHeaders(s);
  const res = http.get(
      `${ENV.BASE_URL}/api/feeds?limit=20&sortBy=CREATED_AT&sortDirection=DESCENDING&keywordLike=${encodeURIComponent('더미')}`,
      {headers, tags: {name: 'search_with_keyword'}}
  );
  check(res, {'200': (r) => r.status === 200});
}

export function withoutKeyword(data) {
  const s = pick(data);
  const headers = authHeaders(s);
  const res = http.get(
      `${ENV.BASE_URL}/api/feeds?limit=20&sortBy=CREATED_AT&sortDirection=DESCENDING`,
      {headers, tags: {name: 'search_without_keyword'}}
  );
  check(res, {'200': (r) => r.status === 200});
}
