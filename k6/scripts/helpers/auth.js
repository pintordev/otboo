// k6/scripts/helpers/auth.js
// CSRF 토큰 발급/회원가입/로그인 등 인증 관련 공용 헬퍼.
import http from 'k6/http';

export function getCsrfToken(baseUrl) {
  http.get(`${baseUrl}/api/auth/csrf-token`);
  const jar = http.cookieJar();
  const cookies = jar.cookiesForURL(baseUrl);
  if (!cookies['XSRF-TOKEN'] || cookies['XSRF-TOKEN'].length === 0) {
    throw new Error('XSRF-TOKEN 쿠키를 못 받았음 — 서버가 떠있는지, BASE_URL이 맞는지 확인');
  }
  return cookies['XSRF-TOKEN'][0];
}

export function signUp(baseUrl, email, password, name, tags = {}) {
  const csrfToken = getCsrfToken(baseUrl);
  return http.post(
      `${baseUrl}/api/users`,
      JSON.stringify({email, password, name}),
      {headers: {'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfToken}, tags}
  );
}

export function signIn(baseUrl, username, password, tags = {}) {
  const csrfToken = getCsrfToken(baseUrl);
  const res = http.post(
      `${baseUrl}/api/auth/sign-in`,
      `username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}`,
      {headers: {'Content-Type': 'application/x-www-form-urlencoded', 'X-XSRF-TOKEN': csrfToken}, tags}
  );

  if (res.status !== 200) {
    return {ok: false, response: res};
  }

  const body = JSON.parse(res.body);
  return {
    ok: true,
    accessToken: body.accessToken,
    userId: body.userDto.id,
    csrfToken,
    response: res,
  };
}

export function authHeaders(session, extra) {
  return Object.assign(
      {Authorization: `Bearer ${session.accessToken}`, 'X-XSRF-TOKEN': session.csrfToken},
      extra || {}
  );
}

export function signUpAndSignIn(baseUrl, email, password, name) {
  const signUpRes = signUp(baseUrl, email, password, name);
  if (signUpRes.status !== 201 && signUpRes.status !== 409) {
    throw new Error(`회원가입 실패: ${signUpRes.status} ${signUpRes.body}`);
  }
  return signIn(baseUrl, email, password);
}
