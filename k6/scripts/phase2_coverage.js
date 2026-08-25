// k6/scripts/phase2_coverage.js
// Coverage Test — 핵심 흐름(Baseline/Load/Scenario 등)에 안 들어간 나머지 API들의 개별 latency를 측정한다.
// 가벼운 VU로 "정상 동작하는가 + 얼마나 걸리는가"만 본다 — 스트레스 목적 아님.
//
// 제외한 것(측정 자체가 부적절):
//   - GET /api/sse: 스트리밍이라 일반 요청형 k6 측정과 방식이 다름
//   - POST /api/auth/reset-password: 실제 Gmail SMTP로 메일이 나가서 반복 호출 부적절
//   - GET /api/clothes/extractions: 무신사/29CM 등 실제 외부 쇼핑몰 스크래핑이라 반복 호출 부적절
//
// 이 테스트는 계정을 회원가입 → 비밀번호 변경 → 세션 무효화 → 삭제까지 끝까지 써버리므로
// 시드 유저 풀을 재사용할 수 없다 — throwaway 계정만 사용.
//
// GET /api/auth/csrf-token은 helpers/auth.js의 getCsrfToken()이 iteration마다 십수 번씩
// 호출하므로 별도 태그 없이도 InfluxDB에 자동으로(URL 그대로) 쌓인다 — 따로 재지 않음.
// adminFlow에는 GET /api/users(관리자 검색), PATCH /api/users/{id}/lock(계정 잠금)도 포함한다.
import http from 'k6/http';
import {check, sleep} from 'k6';
import encoding from 'k6/encoding';
import {ENV} from './config/env.js';
import {coverageOptions} from './config/options.js';
import {signUpAndSignIn, authHeaders, getCsrfToken} from './helpers/auth.js';
import {fetchWeatherId, loginAdmin} from './helpers/data.js';

export const options = coverageOptions;

const TINY_PNG_B64 =
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=';
const IMAGE_BYTES = encoding.b64decode(TINY_PNG_B64);

export function setup() {
  const admin = loginAdmin(ENV.BASE_URL);
  return {admin};
}

function jsonHeaders(session, csrf) {
  return Object.assign({'Content-Type': 'application/json'}, authHeaders(session, {'X-XSRF-TOKEN': csrf}));
}

export function userFlow() {
  const uid = `${__VU}-${__ITER}-${Date.now()}`;
  const me = signUpAndSignIn(ENV.BASE_URL, `cover-me-${uid}@test.com`, 'perf1234', `커버리지me${uid}`);
  const partner = signUpAndSignIn(ENV.BASE_URL, `cover-partner-${uid}@test.com`, 'perf1234', `커버리지partner${uid}`);
  if (!me.ok || !partner.ok) return;

  // --- 속성 정의 CRUD ---
  let csrf = getCsrfToken(ENV.BASE_URL);
  const attrDefName = `커버리지속성-${uid}`;
  let res = http.post(`${ENV.BASE_URL}/api/clothes/attribute-defs`,
      JSON.stringify({name: attrDefName, selectableValues: ['A', 'B']}),
      {headers: jsonHeaders(me, csrf), tags: {name: 'POST /clothes/attribute-defs'}});
  check(res, {'attrdef post 201': (r) => r.status === 201});
  const attrDefId = res.status === 201 ? JSON.parse(res.body).id : null;

  if (attrDefId) {
    csrf = getCsrfToken(ENV.BASE_URL);
    res = http.patch(`${ENV.BASE_URL}/api/clothes/attribute-defs/${attrDefId}`,
        JSON.stringify({name: attrDefName, selectableValues: ['A', 'B', 'C']}),
        {headers: jsonHeaders(me, csrf), tags: {name: 'PATCH /clothes/attribute-defs'}});
    check(res, {'attrdef patch 200': (r) => r.status === 200});
  }

  // --- 옷 이미지 업로드 CRUD ---
  csrf = getCsrfToken(ENV.BASE_URL);
  res = http.post(`${ENV.BASE_URL}/api/clothes`, {
    request: http.file(JSON.stringify({ownerId: me.userId, name: `커버리지옷${uid}`, type: 'TOP', attributes: []}), '', 'application/json'),
    image: http.file(IMAGE_BYTES, 'test.png', 'image/png'),
  }, {headers: authHeaders(me, {'X-XSRF-TOKEN': csrf}), tags: {name: 'POST /clothes(image)'}});
  check(res, {'clothes post(image) 201': (r) => r.status === 201});
  const clothesId = res.status === 201 ? JSON.parse(res.body).id : null;

  if (clothesId) {
    csrf = getCsrfToken(ENV.BASE_URL);
    res = http.patch(`${ENV.BASE_URL}/api/clothes/${clothesId}`, {
      request: http.file(JSON.stringify({name: `커버리지옷수정${uid}`, type: 'TOP', attributes: []}), '', 'application/json'),
      image: http.file(IMAGE_BYTES, 'test2.png', 'image/png'),
    }, {headers: authHeaders(me, {'X-XSRF-TOKEN': csrf}), tags: {name: 'PATCH /clothes(image)'}});
    check(res, {'clothes patch(image) 200': (r) => r.status === 200});
  }

  // --- 피드 + 댓글 + 좋아요 취소 + 수정 ---
  const weatherId = fetchWeatherId(ENV.BASE_URL, authHeaders(me));
  csrf = getCsrfToken(ENV.BASE_URL);
  res = http.post(`${ENV.BASE_URL}/api/feeds`,
      JSON.stringify({authorId: me.userId, weatherId, clothesIds: clothesId ? [clothesId] : [], content: `커버리지피드${uid}`}),
      {headers: jsonHeaders(me, csrf), tags: {name: 'POST /feeds(coverage)'}});
  const feedId = res.status === 201 ? JSON.parse(res.body).id : null;

  if (feedId) {
    csrf = getCsrfToken(ENV.BASE_URL);
    res = http.post(`${ENV.BASE_URL}/api/feeds/${feedId}/comments`,
        JSON.stringify({feedId, authorId: partner.userId, content: `커버리지댓글${uid}`}),
        {headers: jsonHeaders(partner, csrf), tags: {name: 'POST /feeds/comments'}});
    check(res, {'comment post 201': (r) => r.status === 201});

    res = http.get(`${ENV.BASE_URL}/api/feeds/${feedId}/comments?limit=20`,
        {headers: authHeaders(me), tags: {name: 'GET /feeds/comments(coverage)'}});
    check(res, {'comments get 200': (r) => r.status === 200});

    csrf = getCsrfToken(ENV.BASE_URL);
    res = http.post(`${ENV.BASE_URL}/api/feeds/${feedId}/like`, null,
        {headers: jsonHeaders(me, csrf), tags: {name: 'POST /feeds/like(coverage)'}});
    check(res, {'feed like 204': (r) => r.status === 204});

    csrf = getCsrfToken(ENV.BASE_URL);
    res = http.del(`${ENV.BASE_URL}/api/feeds/${feedId}/like`, null,
        {headers: jsonHeaders(me, csrf), tags: {name: 'DELETE /feeds/like'}});
    check(res, {'feed unlike 204': (r) => r.status === 204});

    csrf = getCsrfToken(ENV.BASE_URL);
    res = http.patch(`${ENV.BASE_URL}/api/feeds/${feedId}`,
        JSON.stringify({content: `커버리지피드수정${uid}`}),
        {headers: jsonHeaders(me, csrf), tags: {name: 'PATCH /feeds'}});
    check(res, {'feed patch 200': (r) => r.status === 200});
  }

  // --- 팔로우: partner -> me, 요약/목록/취소, 알림 삭제 ---
  csrf = getCsrfToken(ENV.BASE_URL);
  res = http.post(`${ENV.BASE_URL}/api/follows`,
      JSON.stringify({followerId: partner.userId, followeeId: me.userId}),
      {headers: jsonHeaders(partner, csrf), tags: {name: 'POST /follows(coverage)'}});
  const followId = res.status === 201 ? JSON.parse(res.body).id : null;

  res = http.get(`${ENV.BASE_URL}/api/follows/summary?userId=${me.userId}`,
      {headers: authHeaders(me), tags: {name: 'GET /follows/summary'}});
  check(res, {'follow summary 200': (r) => r.status === 200});

  res = http.get(`${ENV.BASE_URL}/api/follows/followings?followerId=${partner.userId}&limit=20`,
      {headers: authHeaders(partner), tags: {name: 'GET /follows/followings'}});
  check(res, {'followings 200': (r) => r.status === 200});

  res = http.get(`${ENV.BASE_URL}/api/follows/followers?followeeId=${me.userId}&limit=20`,
      {headers: authHeaders(me), tags: {name: 'GET /follows/followers'}});
  check(res, {'followers 200': (r) => r.status === 200});

  // 팔로우 알림이 비동기로 들어오는 걸 기다렸다가 삭제
  let notificationId = null;
  for (let i = 0; i < 5 && !notificationId; i++) {
    sleep(0.3);
    const nres = http.get(`${ENV.BASE_URL}/api/notifications?limit=20`, {headers: authHeaders(me)});
    if (nres.status === 200) {
      const found = JSON.parse(nres.body).data.find((n) => n.title === '팔로우');
      if (found) notificationId = found.id;
    }
  }
  if (notificationId) {
    csrf = getCsrfToken(ENV.BASE_URL);
    res = http.del(`${ENV.BASE_URL}/api/notifications/${notificationId}`, null,
        {headers: jsonHeaders(me, csrf), tags: {name: 'DELETE /notifications'}});
    check(res, {'notification delete 204': (r) => r.status === 204});
  }

  if (followId) {
    csrf = getCsrfToken(ENV.BASE_URL);
    res = http.del(`${ENV.BASE_URL}/api/follows/${followId}`, null,
        {headers: jsonHeaders(partner, csrf), tags: {name: 'DELETE /follows'}});
    check(res, {'follow delete 204': (r) => r.status === 204});
  }

  // --- DM 목록 조회 ---
  res = http.get(`${ENV.BASE_URL}/api/direct-messages?userId=${partner.userId}&limit=20`,
      {headers: authHeaders(me), tags: {name: 'GET /direct-messages'}});
  check(res, {'dm list 200': (r) => r.status === 200});

  // --- 날씨 위치 조회 ---
  res = http.get(`${ENV.BASE_URL}/api/weathers/location?latitude=37.5665&longitude=126.9780`,
      {headers: authHeaders(me), tags: {name: 'GET /weathers/location'}});
  check(res, {'weather location 200': (r) => r.status === 200});

  // --- 프로필 수정 ---
  csrf = getCsrfToken(ENV.BASE_URL);
  res = http.patch(`${ENV.BASE_URL}/api/users/${me.userId}/profiles`, {
    request: http.file(JSON.stringify({name: `수정됨${uid}`, temperatureSensitivity: 3}), '', 'application/json'),
  }, {headers: authHeaders(me, {'X-XSRF-TOKEN': csrf}), tags: {name: 'PATCH /users/profiles'}});
  check(res, {'profile patch 200': (r) => r.status === 200});

  // --- 정리(삭제) ---
  if (clothesId) {
    csrf = getCsrfToken(ENV.BASE_URL);
    res = http.del(`${ENV.BASE_URL}/api/clothes/${clothesId}`, null,
        {headers: jsonHeaders(me, csrf), tags: {name: 'DELETE /clothes'}});
    check(res, {'clothes delete 204': (r) => r.status === 204});
  }
  if (attrDefId) {
    csrf = getCsrfToken(ENV.BASE_URL);
    res = http.del(`${ENV.BASE_URL}/api/clothes/attribute-defs/${attrDefId}`, null,
        {headers: jsonHeaders(me, csrf), tags: {name: 'DELETE /clothes/attribute-defs'}});
    check(res, {'attrdef delete 204': (r) => r.status === 204});
  }
  if (feedId) {
    csrf = getCsrfToken(ENV.BASE_URL);
    res = http.del(`${ENV.BASE_URL}/api/feeds/${feedId}`, null,
        {headers: jsonHeaders(me, csrf), tags: {name: 'DELETE /feeds'}});
    check(res, {'feed delete 204': (r) => r.status === 204});
  }

  // --- 토큰 갱신 + 로그아웃(세션 라이프사이클 확인 — 비밀번호 변경 전에 해야 함) ---
  csrf = getCsrfToken(ENV.BASE_URL);
  res = http.post(`${ENV.BASE_URL}/api/auth/refresh`, null,
      {headers: {'X-XSRF-TOKEN': csrf}, tags: {name: 'POST /auth/refresh'}});
  check(res, {'refresh 200': (r) => r.status === 200});

  csrf = getCsrfToken(ENV.BASE_URL);
  res = http.post(`${ENV.BASE_URL}/api/auth/sign-out`, null,
      {headers: {'X-XSRF-TOKEN': csrf}, tags: {name: 'POST /auth/sign-out'}});
  check(res, {'sign-out 204': (r) => r.status === 204});

  // --- 비밀번호 변경(세션 전체 무효화되므로 반드시 맨 마지막) ---
  csrf = getCsrfToken(ENV.BASE_URL);
  res = http.patch(`${ENV.BASE_URL}/api/users/${me.userId}/password`,
      JSON.stringify({password: 'perf5678'}),
      {headers: jsonHeaders(me, csrf), tags: {name: 'PATCH /users/password'}});
  check(res, {'password patch 204': (r) => r.status === 204});
}

export function adminFlow(data) {
  const uid = `${__VU}-${__ITER}-${Date.now()}`;
  const email = `cover-admin-victim-${uid}@test.com`;
  const csrf0 = getCsrfToken(ENV.BASE_URL);
  const signUpRes = http.post(`${ENV.BASE_URL}/api/users`,
      JSON.stringify({email, password: 'perf1234', name: `롤변경대상${uid}`}),
      {headers: {'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf0}});
  if (signUpRes.status !== 201) return;
  const targetId = JSON.parse(signUpRes.body).id;

  const csrf = getCsrfToken(ENV.BASE_URL);
  const res = http.patch(`${ENV.BASE_URL}/api/users/${targetId}/role`,
      JSON.stringify({role: 'ADMIN'}),
      {headers: jsonHeaders(data.admin, csrf), tags: {name: 'PATCH /users/role'}});
  check(res, {'admin role change 200': (r) => r.status === 200});

  // --- 관리자 유저 검색/목록 ---
  const listRes = http.get(`${ENV.BASE_URL}/api/users?limit=20&sortBy=createdAt&sortDirection=DESCENDING`,
      {headers: authHeaders(data.admin), tags: {name: 'GET /users(admin search)'}});
  check(listRes, {'admin user search 200': (r) => r.status === 200});

  // --- 관리자 계정 잠금/해제 ---
  const csrf2 = getCsrfToken(ENV.BASE_URL);
  const lockRes = http.patch(`${ENV.BASE_URL}/api/users/${targetId}/lock`,
      JSON.stringify({locked: true}),
      {headers: jsonHeaders(data.admin, csrf2), tags: {name: 'PATCH /users/lock'}});
  check(lockRes, {'admin lock 200': (r) => r.status === 200});
}
