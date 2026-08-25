// k6/scripts/phase2_auth.js
// Auth Test — 회원가입/로그인만 별도로 계단식 VU. BCrypt 특성상 200까지만 올린다.
// 이 테스트는 정의상 "새 계정을 만드는 것" 자체가 측정 대상이라 시드 유저 풀을 재사용할 수 없다.
import {check, sleep} from 'k6';
import {ENV} from './config/env.js';
import {authOptions} from './config/options.js';
import {signUp, signIn} from './helpers/auth.js';

export const options = authOptions;

export default function () {
  const email = `k6-auth-${__VU}-${__ITER}-${Date.now()}@test.com`;

  try {
    const signUpRes = signUp(ENV.BASE_URL, email, 'perf1234', '인증계단테스트', {name: 'POST /users(signup)'});
    check(signUpRes, {'회원가입 201': (r) => r.status === 201});

    const session = signIn(ENV.BASE_URL, email, 'perf1234', {name: 'POST /auth/sign-in'});
    check(session, {'로그인 성공': (s) => s.ok === true});
  } catch (e) {
    check(null, {'예외 없이 완료': () => false});
  } finally {
    sleep(0.3);
  }
}
