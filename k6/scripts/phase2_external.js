// k6/scripts/phase2_external.js
// External API Test — 외부 의존(기상청/카카오 날씨, OpenRouter LLM 추천) 구간의 성능을 분리 측정.
// 외부 서비스를 대량 VU로 때리지 않는다 — 날씨는 VU1 소수 반복, 추천은 VU5·30초로 제한.
//
// 주의: 추천 풀 유저에게 반드시 옷을 미리 등록해야 한다 — 옷이 없으면 RecommendationService의
// 얼리 리턴 경로를 타서 LLM 호출 자체가 스킵되고 무효하게 빠른 결과가 나온다(처음엔 이 함정에
// 걸려 p95 수백ms로 잘못 측정됐다가, 풀 유저마다 옷을 3개씩 미리 등록하도록 고쳐서 바로잡음).
import http from 'k6/http';
import {check, sleep} from 'k6';
import {ENV} from './config/env.js';
import {externalOptions} from './config/options.js';
import {authHeaders, signUpAndSignIn} from './helpers/auth.js';
import {createPool, fetchWeatherId, createClothes} from './helpers/data.js';

export const options = externalOptions;

export function setup() {
  const weatherSession = signUpAndSignIn(ENV.BASE_URL, `k6-ext-${Date.now()}@test.com`, 'perf1234', '외부API체크');
  const recoPool = createPool(ENV.BASE_URL, 'k6-reco', 5);
  const weatherId = fetchWeatherId(ENV.BASE_URL, authHeaders(recoPool[0]));
  recoPool.forEach((s, i) => {
    for (let j = 0; j < 3; j++) {
      createClothes(ENV.BASE_URL, authHeaders(s), s.userId, `추천용옷${i}-${j}`);
    }
  });
  return {weatherHeaders: authHeaders(weatherSession), recoPool, weatherId};
}

export function weatherScenario(data) {
  const weatherRes = http.get(
      `${ENV.BASE_URL}/api/weathers?latitude=37.5665&longitude=126.9780`,
      {headers: data.weatherHeaders, tags: {name: 'GET /weathers'}}
  );
  check(weatherRes, {'날씨 조회 200': (r) => r.status === 200});

  const locationRes = http.get(
      `${ENV.BASE_URL}/api/weathers/location?latitude=37.5665&longitude=126.9780`,
      {headers: data.weatherHeaders, tags: {name: 'GET /weathers/location(external)'}}
  );
  check(locationRes, {'위치 조회 200': (r) => r.status === 200});

  sleep(3);
}

export function recommendationScenario(data) {
  const s = data.recoPool[(__VU - 1) % data.recoPool.length];
  const headers = authHeaders(s);

  check(http.get(`${ENV.BASE_URL}/api/recommendations?weatherId=${data.weatherId}`,
      {headers, tags: {name: 'GET /recommendations(with clothes)'}}), {'200': (r) => r.status === 200});
}
