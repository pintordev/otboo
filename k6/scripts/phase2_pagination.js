// k6/scripts/phase2_pagination.js
// Pagination Test — 커서 기반 페이지네이션이 뒷페이지로 갈수록 느려지는지 확인.
// feeds가 시딩으로 이미 수만 건 쌓여있어 30페이지(600건)까지 파봐도 충분히 깊다.
// VU5라 시드 유저 풀 앞 5명만 재사용한다.
import http from 'k6/http';
import {check} from 'k6';
import {ENV} from './config/env.js';
import {paginationOptions} from './config/options.js';
import {authHeaders} from './helpers/auth.js';
import {loginTestUserPool} from './helpers/data.js';

export const options = paginationOptions;
const PAGES_PER_ITERATION = 30;

export function setup() {
  const pool = loginTestUserPool(ENV.BASE_URL);
  return {pool};
}

export default function (data) {
  const s = data.pool[(__VU - 1) % data.pool.length];
  const headers = authHeaders(s);

  let cursor = null;
  let idAfter = null;
  const pageTimings = [];

  for (let page = 0; page < PAGES_PER_ITERATION; page++) {
    let url = `${ENV.BASE_URL}/api/feeds?limit=20&sortBy=CREATED_AT&sortDirection=DESCENDING`;
    if (cursor) {
      url += `&cursor=${encodeURIComponent(cursor)}&idAfter=${idAfter}`;
    }

    const res = http.get(url, {headers, tags: {name: 'GET /feeds(page)', page: String(page)}});
    check(res, {[`page ${page} 200`]: (r) => r.status === 200});
    pageTimings.push(res.timings.duration);

    if (res.status !== 200) break;
    const body = JSON.parse(res.body);
    if (!body.hasNext) break;
    cursor = body.nextCursor;
    idAfter = body.nextIdAfter;
  }

  console.log(`iter=${__ITER} vu=${__VU} pages=${pageTimings.length} first=${pageTimings[0]?.toFixed(0)}ms last=${pageTimings[pageTimings.length - 1]?.toFixed(0)}ms`);
}
