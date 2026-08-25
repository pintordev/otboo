// k6/scripts/helpers/data.js
import http from 'k6/http';
import {signIn, signUpAndSignIn} from './auth.js';
import {TEST_USER_EMAILS, TEST_USER_PASSWORD, ADMIN_EMAIL, ADMIN_PASSWORD} from '../config/env.js';

// 관리자 권한이 필요한 측정(role 변경 등)에서 setup()에서 한 번만 호출.
export function loginAdmin(baseUrl) {
  const session = signIn(baseUrl, ADMIN_EMAIL, ADMIN_PASSWORD);
  if (!session.ok) {
    throw new Error(`관리자 로그인 실패: ${session.response.status} ${session.response.body}`);
  }
  return {userId: session.userId, accessToken: session.accessToken, csrfToken: session.csrfToken};
}

// setup()에서 한 번만 호출 — 기존 시드 유저 풀 전체를 로그인시켜 세션 목록을 만든다.
// "정상 사용자처럼 보이는" 흐름(Baseline/Load/Scenario/Search/Pagination)에서 쓴다.
export function loginTestUserPool(baseUrl) {
  return TEST_USER_EMAILS.map((email) => {
    const session = signIn(baseUrl, email, TEST_USER_PASSWORD);
    if (!session.ok) {
      throw new Error(`시드 유저 로그인 실패: ${email} status=${session.response.status}`);
    }
    return {userId: session.userId, accessToken: session.accessToken, csrfToken: session.csrfToken};
  });
}

// count명을 그 자리에서 회원가입시켜 세션 풀을 만든다. 20명뿐인 시드 풀보다 많은 동시 식별자가
// 필요한 대량 동시성 테스트(Stress/Spike/Concurrency)에서 쓴다.
export function createPool(baseUrl, prefix, count) {
  const sessions = [];
  for (let i = 0; i < count; i++) {
    const email = `${prefix}-${i}-${Date.now()}@test.com`;
    const session = signUpAndSignIn(baseUrl, email, 'perf1234', `${prefix}${i}`);
    if (!session.ok) {
      throw new Error(`유저 생성 실패(${prefix}-${i}): ${session.response.status} ${session.response.body}`);
    }
    sessions.push({userId: session.userId, accessToken: session.accessToken, csrfToken: session.csrfToken});
  }
  return sessions;
}

export function fetchWeatherId(baseUrl, headers) {
  const res = http.get(`${baseUrl}/api/weathers?latitude=37.5665&longitude=126.9780`, {headers});
  if (res.status !== 200) {
    throw new Error(`날씨 조회 실패: ${res.status} ${res.body}`);
  }
  const list = JSON.parse(res.body);
  if (!list || list.length === 0) {
    throw new Error('날씨 데이터가 비어있음 — 외부 API 응답 확인 필요');
  }
  return list[0].id;
}

// 회원가입 직후 계정은 옷이 없어 POST /feeds를 못 만든다 — Stress 등 throwaway pool용으로
// setup()에서 미리 속성 정의 하나 + 풀 멤버당 옷 하나씩 만들어준다.
export function createAttributeDef(baseUrl, headers, name) {
  const res = http.post(
      `${baseUrl}/api/clothes/attribute-defs`,
      JSON.stringify({name, selectableValues: ['A', 'B', 'C']}),
      {headers: Object.assign({'Content-Type': 'application/json'}, headers)}
  );
  if (res.status !== 201) {
    throw new Error(`속성 정의 생성 실패: ${res.status} ${res.body}`);
  }
  return JSON.parse(res.body).id;
}

export function createClothes(baseUrl, headers, ownerId, name) {
  const res = http.post(
      `${baseUrl}/api/clothes`,
      {request: http.file(JSON.stringify({ownerId, name, type: 'TOP', attributes: []}), '', 'application/json')},
      {headers}
  );
  if (res.status !== 201) {
    throw new Error(`옷 생성 실패: ${res.status} ${res.body}`);
  }
  return JSON.parse(res.body).id;
}

// 응답 body의 커서 목록에서 첫 항목의 id를 안전하게 꺼낸다.
export function firstId(res) {
  try {
    const body = res.json();
    return (body && body.data && body.data.length > 0) ? body.data[0].id : null;
  } catch (e) {
    return null;
  }
}
