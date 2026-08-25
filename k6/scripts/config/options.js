// k6/scripts/config/options.js
// 중앙화된 options.js + API별 명시적 threshold. threshold 값은 초기 캘리브레이션 측정에서
// 실측한 nginx+2인스턴스 기준 수치를 근거로 잡았다(임의로 정한 숫자가 아니라, 실측치를
// "이 정도면 정상"의 기준선으로 삼은 것 — 다시 잡는 방법은 ../../GUIDE.md §4 참고).

// Phase 1: 스모크 테스트
export const smokeOptions = {
  vus: 1,
  iterations: 5,
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

// Phase 1: 베이스라인 — VU 10, 실측치를 기준선으로 살짝 여유를 둔 threshold
export const baselineOptions = {
  stages: [
    {duration: '30s', target: 10},
    {duration: '2m', target: 10},
    {duration: '20s', target: 0},
  ],
  thresholds: {
    http_req_duration: ['p(95)<600'],
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:GET /users/profiles}': ['p(95)<300'],
    'http_req_duration{name:GET /clothes}': ['p(95)<300'],
    'http_req_duration{name:GET /feeds}': ['p(95)<800'],       // 피드는 원래 무거움
    'http_req_duration{name:POST /feeds}': ['p(95)<900'],
    'http_req_duration{name:POST /feeds/like}': ['p(95)<900'],
    'http_req_duration{name:POST /follows}': ['p(95)<300'],
    'http_req_duration{name:GET /notifications}': ['p(95)<300'],
  },
};

// Phase 2: Load Test — VU 50, "정상 부하가 SLA를 지키는가"만 딱 떼서 검증
export const loadOptions = {
  stages: [
    {duration: '1m', target: 50},
    {duration: '4m', target: 50},
    {duration: '1m', target: 0},
  ],
  thresholds: {
    http_req_duration: ['p(95)<1500'],
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:GET /users/profiles}': ['p(95)<1000'],
    'http_req_duration{name:GET /clothes}': ['p(95)<1000'],
    'http_req_duration{name:GET /feeds}': ['p(95)<1800'],
    'http_req_duration{name:POST /feeds}': ['p(95)<1800'],
    'http_req_duration{name:POST /feeds/like}': ['p(95)<1800'],
    'http_req_duration{name:POST /follows}': ['p(95)<1000'],
    'http_req_duration{name:GET /notifications}': ['p(95)<1000'],
  },
};

// Phase 2: Stress Test — VU 10->300 계단식. 목적은 SLA 준수가 아니라 "어디서부터 무너지는가" 관찰이므로
// 엄격한 threshold는 걸지 않고 http_req_failed만 관찰용으로 느슨하게 둔다(붕괴 자체가 정상적인 결과).
export const stressOptions = {
  setupTimeout: '3m',
  stages: [
    {duration: '30s', target: 10},
    {duration: '40s', target: 50},
    {duration: '40s', target: 100},
    {duration: '40s', target: 200},
    {duration: '40s', target: 300},
    {duration: '20s', target: 0},
  ],
  thresholds: {
    http_req_failed: [{threshold: 'rate<0.5', abortOnFail: false}],
  },
};

// Phase 2: Spike Test — 평상시(VU10) -> 순간 급증(VU300) -> 급감 -> 회복 관찰.
// nginx worker_connections 한계를 재현·검증하는 스크립트(REPORT.md §16 참고).
export const spikeOptions = {
  setupTimeout: '3m',
  stages: [
    {duration: '30s', target: 10},
    {duration: '10s', target: 300},
    {duration: '1m', target: 300},
    {duration: '10s', target: 10},
    {duration: '2m', target: 10},
  ],
};

// Phase 2: Auth Test — 회원가입+로그인만 계단식. BCrypt 특성상 500까지 안 올리고 200에서 멈춘다.
export const authOptions = {
  stages: [
    {duration: '20s', target: 10},
    {duration: '40s', target: 50},
    {duration: '40s', target: 100},
    {duration: '40s', target: 200},
    {duration: '20s', target: 0},
  ],
};

// Phase 2: Scenario Test — 실사용 흐름별(조회 위주 vs 쓰기 위주)로 나눠서 어느 흐름이 병목인지 구분.
export const scenarioOptions = {
  scenarios: {
    browse_flow: {
      executor: 'ramping-vus',
      exec: 'browseFlow',
      startVUs: 0,
      stages: [
        {duration: '30s', target: 20},
        {duration: '2m', target: 20},
        {duration: '20s', target: 0},
      ],
      gracefulRampDown: '20s',
    },
    write_flow: {
      executor: 'ramping-vus',
      exec: 'writeFlow',
      startVUs: 0,
      stages: [
        {duration: '30s', target: 10},
        {duration: '2m', target: 10},
        {duration: '20s', target: 0},
      ],
      gracefulRampDown: '20s',
      startTime: '3m',  // browse_flow와 안 겹치게(각 흐름의 병목을 분리해서 보기 위함)
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<1500'],
    http_req_failed: ['rate<0.02'],
    'http_req_duration{flow:browse}': ['p(95)<1000'],
    'http_req_duration{flow:write}': ['p(95)<1800'],
  },
};

// Phase 2: Concurrency Test — 동일 자원에 대한 요청 10개를 동시에 쏴서 정합성(유니크 제약 처리)을 검증.
// 세 시나리오를 순서대로(겹치지 않게) 실행 — followRace(이미 확인된 버그), signupRace/likeRace(안전 검증).
export const concurrencyOptions = {
  scenarios: {
    follow_race: {
      executor: 'shared-iterations', vus: 5, iterations: 30, maxDuration: '2m', exec: 'followRace',
    },
    signup_race: {
      executor: 'shared-iterations', vus: 5, iterations: 30, maxDuration: '2m', exec: 'signupRace',
      startTime: '30s',
    },
    like_race: {
      executor: 'shared-iterations', vus: 5, iterations: 30, maxDuration: '2m', exec: 'likeRace',
      startTime: '1m',
    },
  },
};

// Phase 2: Search Performance Test — 키워드 검색(ES match) vs 전체 조회(matchAll) 오버헤드 비교.
export const searchOptions = {
  scenarios: {
    with_keyword: {
      executor: 'constant-vus',
      vus: 20,
      duration: '1m',
      exec: 'withKeyword',
    },
    without_keyword: {
      executor: 'constant-vus',
      vus: 20,
      duration: '1m',
      exec: 'withoutKeyword',
      startTime: '1m10s',
    },
  },
  thresholds: {
    'http_req_duration{name:search_with_keyword}': ['p(95)<1000'],
    'http_req_duration{name:search_without_keyword}': ['p(95)<1000'],
  },
};

// Phase 2: Pagination Test — 커서 페이지네이션이 뒷페이지로 갈수록 느려지는지 확인.
export const paginationOptions = {
  vus: 5,
  iterations: 20,
};

// Phase 2: Coverage Test — 핵심 흐름에 안 들어간 나머지 API들의 개별 latency 측정(가벼운 VU, 스트레스 목적 아님).
// API 전수 커버리지를 이 스위트 하나로 유지하기 위한 것.
export const coverageOptions = {
  setupTimeout: '2m',
  scenarios: {
    user_flow: {
      executor: 'shared-iterations', vus: 10, iterations: 100, maxDuration: '5m', exec: 'userFlow',
    },
    admin_flow: {
      executor: 'shared-iterations', vus: 2, iterations: 20, maxDuration: '2m', exec: 'adminFlow',
    },
  },
};

// Phase 2: External API Test — 외부 의존(날씨, LLM 추천) 구간만 분리 측정. 대량 VU로 때리지 않는다.
// 추천은 외부 LLM(OpenRouter)을 실제로 호출하므로 VU5·30초로 짧게 제한(과도한 외부 API 호출 방지).
export const externalOptions = {
  setupTimeout: '2m',
  scenarios: {
    weather: {
      executor: 'shared-iterations', vus: 1, iterations: 3, maxDuration: '1m', exec: 'weatherScenario',
    },
    recommendation: {
      executor: 'constant-vus', vus: 5, duration: '30s', exec: 'recommendationScenario', startTime: '20s',
    },
  },
};
