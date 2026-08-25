// k6/scripts/phase1_smoke.js
// Smoke Test — 연결·기능이 정상인지 최소한으로 확인.
import http from 'k6/http';
import {check, sleep} from 'k6';
import {ENV} from './config/env.js';
import {smokeOptions} from './config/options.js';
import {signUpAndSignIn, authHeaders} from './helpers/auth.js';

export const options = smokeOptions;

export default function () {
  const health = http.get(`${ENV.BASE_URL}/actuator/health`);
  check(health, {'health 200': (r) => r.status === 200});

  const email = `k6-smoke-${__VU}-${__ITER}-${Date.now()}@test.com`;
  const session = signUpAndSignIn(ENV.BASE_URL, email, 'perf1234', '스모크테스트');
  check(session, {'회원가입+로그인 성공': (s) => s.ok === true});
  if (!session.ok) return;

  const headers = authHeaders(session);
  check(http.get(`${ENV.BASE_URL}/api/users/${session.userId}/profiles`, {headers}),
      {'프로필 조회 200': (r) => r.status === 200});
  check(http.get(`${ENV.BASE_URL}/api/feeds?limit=20&sortBy=CREATED_AT&sortDirection=DESCENDING`, {headers}),
      {'피드 목록 200': (r) => r.status === 200});

  sleep(1);
}
