// k6/scripts/phase2_spike.js
// Spike Test — 평상시(VU10) -> 순간 급증(VU300) -> 급증 유지 -> 급감 -> 회복 관찰.
// nginx worker_connections 부족을 재현·확정하는 스크립트(REPORT.md §16 참고).
import http from 'k6/http';
import {check, sleep} from 'k6';
import {Trend, Rate} from 'k6/metrics';
import {ENV} from './config/env.js';
import {spikeOptions} from './config/options.js';
import {authHeaders, getCsrfToken} from './helpers/auth.js';
import {createPool} from './helpers/data.js';

export const options = spikeOptions;
const POOL_SIZE = 60;

const PHASES = ['A_평상시', 'B_급증중', 'C_급증유지', 'D_급감중', 'E_회복관찰'];
const PHASE_METRIC_KEY = {
  'A_평상시': 'A_normal',
  'B_급증중': 'B_spiking',
  'C_급증유지': 'C_sustained',
  'D_급감중': 'D_dropping',
  'E_회복관찰': 'E_recovery',
};
const phaseDuration = {};
const phaseFailRate = {};
PHASES.forEach((p) => {
  const key = PHASE_METRIC_KEY[p];
  phaseDuration[p] = new Trend(`phase_${key}_duration`);
  phaseFailRate[p] = new Rate(`phase_${key}_fail_rate`);
});

export function setup() {
  const pool = createPool(ENV.BASE_URL, 'spike', POOL_SIZE);
  return {pool, startedAt: Date.now()};
}

export default function (data) {
  const idx = (__VU - 1) % data.pool.length;
  const s = data.pool[idx];

  const elapsedS = Math.floor((Date.now() - data.startedAt) / 1000);
  const phase =
      elapsedS < 30 ? 'A_평상시' :
      elapsedS < 40 ? 'B_급증중' :
      elapsedS < 100 ? 'C_급증유지' :
      elapsedS < 110 ? 'D_급감중' : 'E_회복관찰';

  // 급증 구간에서는 getCsrfToken()이 쿠키를 못 받아 예외를 던질 수 있다(nginx worker_connections
  // 소진 — 이 테스트가 관찰하려는 대상 자체). try/catch 없이 두면 (a) 해당 iteration이 phase
  // 실패율 집계에 전혀 안 잡히고(과소 집계) (b) sleep 없이 폭주하고 (c) 콘솔이 도배된다.
  try {
    const csrfToken = getCsrfToken(ENV.BASE_URL);
    const headers = authHeaders(s, {'X-XSRF-TOKEN': csrfToken});

    const r1 = http.get(`${ENV.BASE_URL}/api/clothes?ownerId=${s.userId}&limit=20`,
        {headers, tags: {name: 'GET clothes', phase}});
    const ok1 = check(r1, {'200': (r) => r.status === 200});
    phaseDuration[phase].add(r1.timings.duration);
    phaseFailRate[phase].add(!ok1);

    const r2 = http.get(`${ENV.BASE_URL}/api/feeds?limit=20&sortBy=CREATED_AT&sortDirection=DESCENDING`,
        {headers, tags: {name: 'GET feeds', phase}});
    const ok2 = check(r2, {'200': (r) => r.status === 200});
    phaseDuration[phase].add(r2.timings.duration);
    phaseFailRate[phase].add(!ok2);
  } catch (e) {
    // CSRF 단계에서부터 막힌 것 — r1/r2 둘 다 시도조차 못 했으므로 정상 흐름과 같은 비중(2건)으로
    // 실패를 기록해야 phase별 실패율이 과소 집계되지 않는다.
    check(null, {'예외 없이 완료': () => false});
    phaseFailRate[phase].add(true);
    phaseFailRate[phase].add(true);
  } finally {
    sleep(0.3);
  }
}
