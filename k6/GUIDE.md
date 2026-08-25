# otboo 성능 테스트 가이드

**정상 부하에서 otboo 프로젝트의 어디가 병목인가를 찾는 것**이 목적이고, 그 결과는 [`results/REPORT.md`](results/REPORT.md)에 있다. 이
문서는 "왜 이렇게 짜여 있는지"와 "어떻게 실행/재측정하는지"를 정리한다.

## 1. 설계 철학

1. **찾는 것이 목표, 무너뜨리는 게 목표가 아니다.** Stress/Spike도 있지만 붕괴점 자체를 노리는 게 아니라 "어디서부터, 왜 무너지는가"를 관찰하는 용도다 —
   대부분은 Smoke → Baseline (VU10) → Load (VU50) → Scenario (흐름 분리)로 "정상~약간 여유있는 부하"까지만 올린다.
2. **모든 threshold는 실측 기반이다.** [`config/options.js`](./scripts/config/options.js)의 숫자들은 임의로 정한 게 아니라
   초기 캘리브레이션 측정치를 "이 정도면 정상"의 기준선으로 삼아 잡았다. 새로 이 기준을 잡아야 한다면 4번 항목을 참고.
3. **병목 후보는 threshold 미달로 찾고, 원인은 트레이스로 판다.** k6는 "느리다"까지만 알려준다. "왜 느린지"는 Jaeger (분산 트레이스)로, 필요하면
   Postgres `EXPLAIN ANALYZE`로 내려가서 확인한다. `results/REPORT.md` §16이 이 과정을 보여주는 예시다 (알림 N+1 INSERT 발견
   과정), §15는 이 방식으로 프로젝트 전체를 훑은 코드 레벨 감사다.
4. **시딩은 재사용, 재시딩하지 않는다.** 기존 데이터셋 (유저 수천 명, 팔로우, 피드 수만 건, 인플루언서 계정 포함)을 그대로 쓴다. 매번 새로 회원가입시키지 않고
   `seed-user-1`~`20@test.com`(비밀번호 `perf1234`)로 로그인해서 재사용한다 — 실제 서비스에 가까운 데이터 분포 (팔로워 수 치우침 등) 위에서
   측정해야 의미가 있기 때문이다. 데이터셋이 없다면 §2-1 (시드 데이터 복원) 참고.
5. **단일 인스턴스 비교는 범위 밖.** nginx + app-1 + app-2 구성 자체의 병목만 본다.
6. **원인만 찾고 끝내도 된다.** 코드를 고치고 재측정까지 하면 더 좋지만 (이상적인 사이클은 §6 참고), 1차 목표는 "문제를 찾고 개선 방향을 제시하는 것"이다. 실제
   수정 여부는 팀 논의 후 별도로 진행한다.

## 2. 어떤 API를 왜 골랐는가 — 테스트별 선정 기준

k6/ 안의 10종 테스트는 "otboo의 모든 API를 다 잰다"가 아니라, **각 테스트마다 다른 질문에 답하기 위해 의도적으로 API 집합을 다르게 골랐다.** 왜 이렇게
나눴는지 정리해둔다 — 새 테스트를 추가할 때도 이 기준을 따르면 된다.

### 2-1. Baseline·Load·Stress가 같은 7개 API를 고정해서 쓰는 이유

`GET /users/profiles`, `GET /clothes`, `GET/POST /feeds`, `POST /feeds/like`, `POST /follows`,
`GET /notifications` — 이 7개는 세 테스트 (`phase1_baseline.js`, `phase2_load.js`, `phase2_stress.js`)에
**전부 똑같이** 들어간다. 우연이 아니라, 이 세 테스트의 목적 자체가 "**같은 흐름을 부하만 다르게 걸어서 비교하는 것**"이기 때문이다 — VU10 (Baseline) →
VU50 (Load) → VU10→300 계단식 (Stress)으로 부하만 올려가면서 "이 API가 부하가 늘수록 얼마나 느려지는가"를 본다. 세 단계가 다른 API를 재면 애초에
비교할 대상이 없어진다.

이 7개를 고른 기준은 "실제 세션에서 반복적으로 호출되는 것"이다 — 프로필/옷장/피드/좋아요/팔로우/알림은 유저가 앱을 켜놓고 있는 동안 계속 부르는 API들이다. 반대로
비밀번호 변경, 이미지 업로드, 속성정의 CRUD, 관리자 role 변경 같은 "세션당 한두 번 있을까 말까 한" 일회성 액션은 지속 부하 (VU50을 4분 유지 등)를 걸어서
재는 의미가 적다 — 이런 건 Coverage Test (§2-3)로 따로 뺐다.

### 2-2. Search·Pagination·Auth·Concurrency가 따로 분리된 이유

이 넷은 각각 "부하 자체"가 아니라 **다른 변수 하나**를 보는 게 목적이라, Baseline/Load 흐름에 섞으면 오히려 판단을 흐린다:

- **Search** — 키워드 유무만 다르게 비교해야 하는데, 혼합 흐름에 섞으면 "검색이라서 느린 건지 부하 때문에 느린 건지" 구분이 안 된다.
- **Pagination** — 페이지 깊이가 변수라 마찬가지로 부하 변수와 분리해야 한다.
- **Auth** — 시드 유저를 재사용할 수 없는 (회원가입 자체가 측정 대상인) 유일한 부하 테스트라 흐름 자체가 다르다.
- **Concurrency** — "부하량"이 아니라 "완전히 동시에 같은 자원을 건드렸을 때 정합성이 지켜지는가"가 변수라, VU를 올리는 방식의 테스트와는 아예 성격이
  다르다. 처음엔 팔로우 하나만 검증했지만, "다른 API에서도 같은 문제가 날 수 있지 않냐"는 지적을 받고 유니크 제약을 catch하는 곳 (회원가입, 좋아요)까지 넓혔다 —
  `results/REPORT.md` §9·§15-1 참고. 코드가 "안전해 보인다"는 것과 "실제로 안전하다"는 것은 다를 수 있다는 게 이 라운드의 핵심 교훈이었다.

### 2-3. Coverage·External이 따로 있는 이유

`phase2_coverage.js`는 나머지 열 개 스크립트에 안 들어간 API (속성정의 CRUD, 이미지 업로드, 댓글, 팔로우 목록류, 관리자 기능 등)를 전부 훑어서
**API 전수 커버리지를 이 스위트 하나로 유지**하는 역할이다. 계정을 회원가입 → 비밀번호 변경 → 탈퇴까지 끝까지 소모하는 흐름이라 시드 풀은 못 쓴다.

`phase2_external.js`는 기상청/카카오 날씨, OpenRouter LLM 추천처럼 **otboo 코드가 아니라 외부 서비스에 달린 API**만 따로 뺀 것 — 대량
VU로 외부 서비스를 때리면 안 되고 (과도한 외부 API 호출), 응답시간이 우리 코드 성능이 아니라 외부 제공자 상태를 반영하므로 다른 결과와 섞으면 안 된다
(`results/REPORT.md` §14 참고).

## 3. 폴더 구조

```
k6/
├── GUIDE.md               # 이 문서
├── REPORT.md              # 결과 보고서
├── seed/
│   └── snapshot.sql.gz      # 시드 데이터 스냅샷(users/clothes/feeds/follows/comments/weathers 등)
├── scripts/
│   ├── config/
│   │   ├── env.js            # BASE_URL, 시드 유저 풀 정의
│   │   └── options.js        # 중앙화된 k6 옵션 — 모든 phase가 여기서 threshold를 가져옴
│   ├── helpers/
│   │   ├── auth.js            # CSRF/회원가입/로그인
│   │   └── data.js            # 시드 유저 풀 로그인, throwaway 풀 생성, 응답에서 id 뽑기
│   ├── phase1_smoke.js        # 연결·기능 최소 확인
│   ├── phase1_baseline.js     # 기준선
│   ├── phase2_load.js         # 정상 부하
│   ├── phase2_stress.js       # 한계점 탐색(계단식)
│   ├── phase2_spike.js        # 순간 급증 + 회복력
│   ├── phase2_auth.js         # 회원가입/로그인만 계단식
│   ├── phase2_scenario.js     # 흐름 분리: browse_flow / write_flow
│   ├── phase2_concurrency.js  # 동시 요청 정합성(팔로우 유니크 제약)
│   ├── phase2_search.js       # 키워드 검색 vs 전체 조회
│   ├── phase2_pagination.js   # 커서 페이지네이션 깊이별 성능
│   ├── phase2_coverage.js     # 나머지 22개 API 개별 latency
│   └── phase2_external.js     # 외부 의존(날씨, LLM 추천) 구간 분리 측정
├── results/                # --summary-export 결과 JSON (git 미포함, .gitignore 처리됨)
└── grafana/provisioning/   # Grafana 데이터소스+대시보드 자동 프로비저닝 설정
```

`docker-compose.k6.yml`(레포 루트)이 이 폴더를 마운트하고, k6 + InfluxDB + Grafana 세 서비스를 정의한다.

### 3-1. 시드 데이터 복원

Postgres 볼륨이 살아있는 한 아무것도 할 필요 없다. 볼륨을 지웠거나 (`docker compose down -v`) 처음부터 다시 세팅하는 경우:

```powershell
docker compose up -d postgres            # Flyway가 앱 기동 시 스키마를 만들므로 app도 한 번 떠야 함
docker compose up -d --build
Get-Content k6/seed/snapshot.sql.gz | & "C:\Program Files\7-Zip\7z.exe" x -si -so | `
  docker compose exec -T postgres psql -U $env:POSTGRES_USER -d otboo
```

Git Bash라면 더 간단하다:

```bash
gunzip -c k6/seed/snapshot.sql.gz | docker compose exec -T postgres psql -U "$POSTGRES_USER" -d otboo
```

스키마는 Flyway 마이그레이션 (`src/main/resources/db/migration/`)이 앱 기동 시 자동으로 만들기 때문에, 이 덤프는 **데이터만** 담고 있다
(`pg_dump --data-only`). `notifications`는 덤프에서 제외했다 — 이건 시드 데이터가 아니라 피드 작성 같은 행동의 부산물이라, 앱을 정상적으로 쓰다
보면 자연히 다시 쌓인다.

**Elasticsearch는 볼륨이 없어서 컨테이너가 재시작되면 색인이 비어있다.** feeds 검색 (`phase2_search.js` 등)을 돌리려면 재색인이 필요하다:

```powershell
docker compose exec -T postgres psql -U $env:POSTGRES_USER -d otboo -t -A -c "
SELECT '{\"index\":{\"_index\":\"feeds\",\"_id\":\"' || id || '\"}}' || $([char]10) ||
  json_build_object('id', id, 'content', content, 'authorId', author_id,
    'skyStatus', sky_status, 'precipitationType', precipitation_type,
    'createdAt', to_char(created_at at time zone 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.MS\"Z\"'),
    'likeCount', like_count)::text
FROM feeds WHERE deleted_at IS NULL;" | Out-File -Encoding utf8 feeds_bulk.ndjson
curl -X POST "http://localhost:9201/_bulk" -H "Content-Type: application/x-ndjson" --data-binary "@feeds_bulk.ndjson"
```

### 시드 유저 재사용 vs 그때그때 회원가입 — 테스트마다 다르다

"시딩 재사용"(§1-4)은 원칙이지만, 절대적인 건 아니다. 두 그룹으로 나뉜다:

- **시드 유저 풀 (20명) 재사용** — Baseline, Load, Scenario, Search, Pagination. "실제 있는 유저처럼" 보이는 게 중요하고 VU가
  20 이하라 풀로 충분하다.
- **그때그때 회원가입 (throwaway pool)** — Stress, Spike, Auth, Concurrency, Coverage, External. VU가 최대
  300까지 올라가 20명 풀로는 동시 식별자 수가 모자라거나 (Stress/Spike), 애초에 "회원가입 자체"가 측정 대상이거나 (Auth), 매 iteration마다
  유니크한 새 자원 쌍이 필요하거나 (Concurrency), 계정을 비밀번호 변경·탈퇴까지 끝까지 소모하거나 (Coverage), 외부 LLM을 호출하는 소규모 전용 풀이
  필요하기 (External) 때문이다. `helpers/data.js`의 `createPool()`이 이 역할을 한다.

## 4. 실행 방법

### 4-1. 메인 스택 + 시각화 스택 기동

```powershell
docker compose up -d --build                                          # 메인 스택 (이미 떠있다면 생략)
docker compose -f docker-compose.yml -f docker-compose.k6.yml up -d influxdb grafana
```

Grafana: http://localhost:3002 (로그인 불필요, 익명 뷰어로 바로 열림) → `k6` 폴더 → **otboo k6 실시간 부하 테스트** 대시보드.
InfluxDB: http://localhost:8086 (호스트에서 직접 쿼리하고 싶을 때만; 평소엔 Grafana로 충분)

### 4-2. 테스트 실행

```powershell
docker compose -f docker-compose.yml -f docker-compose.k6.yml run --rm k6 `
  run --summary-export=/results/phase1_baseline-summary.json /scripts/phase1_baseline.js
```

Git Bash에서는 경로 자동변환 때문에 앞에 `MSYS_NO_PATHCONV=1`을 붙인다:

```bash
MSYS_NO_PATHCONV=1 docker compose -f docker-compose.yml -f docker-compose.k6.yml run --rm k6 \
  run --summary-export=/results/phase1_baseline-summary.json /scripts/phase1_baseline.js
```

다른 스크립트도 파일명만 바꿔서 동일하게 실행한다.

| 스크립트                | 부하                                            | 소요시간 | 유저 풀                                                          |
|-------------------------|-------------------------------------------------|----------|------------------------------------------------------------------|
| `phase1_smoke.js`       | VU1, 5 iteration                                | ~20초    | 회원가입(1회성)                                                  |
| `phase1_baseline.js`    | VU10 고정                                       | ~3분     | 시드 풀 재사용                                                   |
| `phase2_load.js`        | VU50 고정                                       | ~6분     | 시드 풀 재사용                                                   |
| `phase2_stress.js`      | VU 10→300 계단식                                | ~3분50초 | throwaway(60명)                                                  |
| `phase2_spike.js`       | VU10→300 순간 급증→회복                         | ~4분10초 | throwaway(60명)                                                  |
| `phase2_auth.js`        | VU 10→200 계단식(회원가입+로그인만)             | ~2분50초 | 매 iteration 신규 회원가입                                       |
| `phase2_scenario.js`    | browse VU20 + write VU10(3분 뒤 시작)           | ~6분     | 시드 풀 재사용                                                   |
| `phase2_concurrency.js` | VU5, 30 iteration, iteration마다 동시 요청 10개 | ~20초    | 매 iteration 신규 회원가입 쌍                                    |
| `phase2_search.js`      | VU20, with/without 키워드 각 1분                | ~2분10초 | 시드 풀 재사용                                                   |
| `phase2_pagination.js`  | VU5, 20 iteration, iteration마다 최대 30페이지  | ~15초    | 시드 풀 재사용(앞 5명)                                           |
| `phase2_coverage.js`    | user_flow VU10×100 + admin_flow VU2×20          | ~2분     | 매 iteration 신규 회원가입(계정을 끝까지 소모하므로 재사용 불가) |
| `phase2_external.js`    | weather VU1×3 + recommendation VU5×30초         | ~1분     | throwaway(외부 LLM 실호출이라 소규모로 제한)                     |

Stress/Spike/Auth는 **의도적으로 nginx `worker_connections`(기본 512) 한계를 넘는 VU까지 올린다** — 실패율이 급증하는 게 버그가
아니라 이 테스트들의 정상적인 관찰 대상이다 (`results/REPORT.md` §17 참고).

`phase2_coverage.js`는 나머지 스크립트에 안 들어간 API (속성정의 CRUD, 옷 이미지 업로드, 댓글, 팔로우 요약/목록/삭제, DM, 날씨 위치,
프로필/비밀번호 수정, 인증 refresh/sign-out, 관리자 role 변경 등 22개)를 훑어서 API 전수 커버리지를 이 스위트 하나로 유지한다. 계정을 회원가입 →
비밀번호 변경 → 세션 무효화 → 탈퇴까지 끝까지 소모하는 흐름이라 시드 유저 풀은 못 쓰고 매번 새로 회원가입한다. 관리자 role 변경 테스트는 시드 데이터에 포함된 고정
관리자 계정 (`admin@email.com`)을 재사용한다 (재시딩 불필요, DB에 이미 `role=ADMIN`으로 존재).

**의도적으로 제외한 API (측정 자체가 부적절, 커버리지 누락이 아님)**:

- `GET /api/sse` — 스트리밍이라 일반 요청형 k6 측정과 방식이 다름
- `POST /api/auth/reset-password` — 실제 SMTP로 메일이 나가서 반복 호출 부적절
- `GET /api/clothes/extractions` — 실제 외부 쇼핑몰 스크래핑이라 반복 호출 부적절

단일 인스턴스 비교, 대규모 도메인별 계단식 테스트, 장시간 (Soak) 테스트, 배치 병행 테스트는 "성능 위주 병목 찾기"라는 이번 스위트의 스코프 밖이다
(`results/REPORT.md` §20 참고) — 다음 라운드 후보.

**실행 중에 Grafana 대시보드를 열어두면 VU/응답시간/에러율이 실시간 (5초 갱신)으로 움직이는 걸 볼 수 있다.** k6는
`K6_OUT=influxdb=http://influxdb:8086/k6` 환경변수 (이미 `docker-compose.k6.yml`에 설정됨) 덕분에 실행과 동시에 매 요청
단위로 InfluxDB에 쓴다.

## 5. threshold (정상 기준선)를 다시 잡아야 할 때

지금 `options.js`의 숫자는 이 프로젝트의 **초기 측정치**를 기준으로 잡은 것이라, 인프라나 데이터 규모가 크게 바뀌면 그대로 안 맞을 수 있다. 다시 잡는 순서:

1. threshold 없이 (또는 느슨하게) `phase1_baseline.js`를 1~2회 돌려서 API별 p95를 확인한다.
2. 그 값에 30~50% 정도 여유를 둬서 `options.js`의 `http_req_duration{name:...}` threshold로 넣는다 — "여기까지는 정상"이라는
   상한선 개념이지, 목표치가 아니다.
3. Load (VU50) threshold는 Baseline threshold의 약 2배 전후로 잡되, 실제로 VU50까지 돌려보고 조정한다.
4. API를 추가했다면 해당 스크립트에 `tags: {name: 'METHOD /path'}`를 반드시 붙인다 — 이 태그가 없으면 threshold도, Grafana의
   "엔드포인트별 p95" 패널도 그 API를 구분하지 못한다 (URL 그대로 태그가 잡혀서 유저 ID별로 계열이 쪼개짐).

## 6. 재측정 방법 — 코드를 고친 뒤 개선을 검증하고 싶을 때

이상적인 사이클은 "측정 → 병목 후보 식별 → 원인 분석 → (코드/설정 수정) → 재측정으로 검증"이다. `results/REPORT.md`가 찾아낸 문제 (예: 알림 N+1
INSERT)를 실제로 고친 뒤 재측정하려면:

1. 코드/설정을 수정한다 (예: `application.yaml`에 `hibernate.jdbc.batch_size` 추가).
2. `docker compose up -d --build app-1 app-2`로 앱만 재빌드/재기동한다 (Postgres 데이터는 볼륨이라 유지됨 — 재시딩 불필요).
3. **수정 전과 똑같은 스크립트**를 똑같은 조건 (VU, duration)으로 다시 돌린다. 시드 데이터를 그대로 재사용하기 때문에 "비교 조건"이 자동으로 맞는다.
4. 결과 파일명을 구분한다: `--summary-export=/results/phase1_baseline-after.json` 식으로 `-after` 접미사를 붙여서 기존 결과를
   덮어쓰지 않는다.
5. **InfluxDB/Grafana는 시간축으로 데이터가 계속 쌓인다** — 수정 전/후 실행이 같은 대시보드에 다 남아있다. Grafana 우측 상단 시간 범위를 수정 전
   실행 구간 / 수정 후 실행 구간으로 각각 좁혀서 두 스크린샷을 비교하거나, InfluxDB에 직접 시간 범위를 넣어 쿼리하면 된다:
   ```
   SELECT percentile("value", 95) FROM "http_req_duration"
   WHERE "name" = 'POST /feeds' AND time > '2026-08-24T07:00:00Z' AND time < '2026-08-24T07:10:00Z'
   ```
6. `results/REPORT.md`에 before/after 표로 반영한다.

## 7. 한계 / 주의사항

- **로컬 Docker Desktop 환경이라 절대치보다 상대적 경향을 신뢰하는 게 안전하다.** 호스트 리소스를 다른 프로세스와 공유하고, JIT 워밍업도 완전하지 않다 —
  "어떤 API가 다른 API보다 몇 배 무거운가", "threshold를 넘었는가"가 "정확히 몇 ms인가"보다 신뢰도가 높다.
- **연속으로 강한 부하를 여러 번 돌리면 Docker Desktop 자체가 불안정해질 수 있다** — `docker ps`조차 응답이 없어지는 증상으로 나타난다. Docker
  Desktop 프로세스를 강제 종료하고 재시작하면 복구된다 (§8 참고). Load 이상은 연달아 돌리기보단 한 번 돌리고 회복 시간을 두는 걸 권장.
- **Grafana 대시보드의 "엔드포인트별" 패널은 `tags:{name:...}`을 명시한 스크립트에서만 의미가 있다.** `phase1_smoke.js`처럼 태그를 안 붙인
  스크립트는 URL 그대로 태그가 찍혀서 고카디널리티 (유저 ID별로 분리)가 된다 — 문제는 아니지만 대시보드에서 지저분해 보일 수 있다.
- InfluxDB 데이터는 이 프로젝트 목적상 로컬 전용이며 만료 정책 (retention policy)을 따로 걸지 않았다. 오래 방치하면 볼륨이 계속 커진다 — 정리하고
  싶으면 `docker compose down -v`로 `influxdb_data`/`grafana_data` 볼륨을 지우면 된다 (Postgres 볼륨과는 별개라 시드 데이터는
  안전).
- **VU를 붕괴점 이상으로 올리는 스크립트 (Stress/Spike/Auth류)는 `getCsrfToken()` 등 실패할 수 있는 호출을 반드시
  `try/catch/finally`로 감싸야 한다.** 안 감싸면 서버가 무너지는 순간 매 iteration이 스크립트 예외로 죽어서 (1) 콘솔이 같은 에러로 도배되고 (2)
  `sleep()`을 못 타서 페이싱 없이 폭주하고 (3) — 커스텀 `Rate()`/`Trend()` 메트릭을 쓰는 스크립트라면 — **가장 심하게 무너지는 구간일수록 실패가
  오히려 집계에서 빠지는 역설**이 생긴다 (`phase2_spike.js`가 실제로 이 버그로 급증유지 구간 실패율을 59~62%로 과소 집계했다가, 고친 뒤 재보니
  82%였다 — `results/REPORT.md` §7 참고). `phase2_auth.js`가 최초로 이 교훈을 겪고 반영한 패턴이니, 새 스크립트를 붕괴점 이상으로 설계할
  때는 처음부터 이 패턴을 쓸 것.
- **`admin_flow`처럼 VU가 적은 (2 등) 시나리오의 지표는 표본이 20개 안팎이라 극단값 한두 개에 p95가 크게 흔들린다.** 실제로
  `PATCH /users/role`과 `GET /users`(관리자 검색)가 한 번은 p95 1초 이상으로 나왔다가, 재실행에서는 완전히 정상 범위로 나온 사례가 있었다
  (`results/REPORT.md` §13) — `user_flow`(VU10)와 동시에 도는 동안 하필 서버가 바쁜 순간에 걸린 것뿐, 코드 문제가 아니었다. 표본이 적은
  지표에서 이상치가 보이면 **바로 결론 내지 말고 최소 한 번은 재실행해서 재현되는지 확인**할 것.
- **Jaeger는 기본적으로 메모리 저장이라 컨테이너가 재시작되면 그동안의 트레이스가 전부 사라진다**(`docker-compose.yml`에 별도 영속 스토리지를 안
  붙였음). InfluxDB/Postgres 데이터는 볼륨 덕에 재시작 후에도 남지만, "그 요청이 정확히 어디서 느렸는지"를 Jaeger로 파려면 **재시작 직후에는 옛날
  트레이스를 볼 수 없다** — 재현 테스트를 다시 돌려서 새 트레이스를 만들어야 한다.

## 8. 문제 생기면

- `nginx`/`app-1` 못 찾는다는 에러 → 메인 스택이 안 떠있는 것. `docker compose up -d --build`부터.
- Git Bash에서 `/scripts/...` 파일을 못 찾는다는 에러 → `MSYS_NO_PATHCONV=1` 빠뜨림.
- Grafana 대시보드에 패널은 있는데 데이터가 안 보임 → 우측 상단 시간 범위가 테스트를 돌린 시간대를 벗어나 있는 경우가 대부분. `Last 15 minutes` 등으로
  맞추기.
- `influxdb` 컨테이너가 `unhealthy`로 안 뜸 → alpine 이미지엔 `curl`이 없어서 헬스체크가 실패할 수 있음
  (`docker-compose.k6.yml`은 이미 `wget`으로 처리돼 있음). 커스텀 이미지로 바꿨다면 이 부분부터 확인.
- 시딩된 계정으로 로그인이 500 에러 → 비밀번호 해시에 `{bcrypt}` 접두사가 빠진 경우 (Spring Security `DelegatingPasswordEncoder`
  요구사항). `k6/seed/snapshot.sql.gz`는 이미 정상 해시로 반영돼 있음.
- `docker ps`도 응답 안 함 → Docker Desktop 자체가 다운된 것 (강한 부하 테스트 직후 흔함). Docker Desktop 프로세스를 강제 종료 후
  재시작 → 엔진이 응답할 때까지 대기 → `docker compose up -d`로 메인 스택과 InfluxDB/Grafana를 다시 올린다. Postgres는 볼륨이 있어
  데이터가 유지되지만, Elasticsearch는 볼륨이 없어 색인이 날아가므로 §3-1의 재색인 절차를 다시 밟아야 한다.
