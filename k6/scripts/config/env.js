// k6/scripts/config/env.js
// nginx + app-1 + app-2(기존 docker-compose.yml 토폴로지)를 대상으로 한다.
// 시딩은 기존 데이터셋을 그대로 재사용한다(재시딩하지 않음) — 없다면 k6/seed/ 참고.
export const ENV = {
  BASE_URL: __ENV.BASE_URL || 'http://nginx',
};

// 기존 시드 유저(seed-user-1..N@test.com, 비밀번호 perf1234)를 그대로 재사용한다 —
// 매번 회원가입시키지 않고, 이미 있는 "진짜 유저처럼 보이는" 계정으로 로그인해서 현실적인 트래픽을 만든다.
export const TEST_USER_EMAILS = Array.from(
    {length: 20}, (_, i) => `seed-user-${i + 1}@test.com`);
export const TEST_USER_PASSWORD = 'perf1234';

// 관리자 role 변경 등 관리자 권한이 필요한 측정(phase2_coverage.js)에서 사용.
// k6/seed/snapshot.sql.gz가 담고 있는 고정 관리자 계정 — DB에 role=ADMIN으로 이미 존재함(재시딩 불필요).
export const ADMIN_EMAIL = 'admin@email.com';
export const ADMIN_PASSWORD = 'admin123!';
