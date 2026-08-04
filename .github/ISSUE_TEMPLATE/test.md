---
name: "✅ 테스트"
about: 테스트 보강, 커버리지 개선, 검증
title: "[TEST] "
labels: test
---

## 테스트 대상

<!-- 클래스/기능 -->

## 목적

- [ ] 커버리지 보강
- [ ] 엣지 케이스 보강
- [ ] 외부 API 실제 호출 기반 검증 (기상청/Kakao/LLM)
- [ ] 기타:

## Notion 카드 (해당 시)

<!-- 계획서 단계에서 이미 만들어둔 작업 카드(작업 트래커 DB)가 있으면 그 카드 URL을 적어주세요.
     제목만으로는 매칭이 잘 안 돼서(예: 카드 1개가 이슈 여러 개로 쪼개지는 경우) 여기 기재된 URL로 자동 연결합니다. -->

## 체크리스트

- [ ] `given / when / then` 구조 준수, `@DisplayName` 한글
- [ ] 테스트 픽스처는 EasyRandom/FixtureMonkey 사용(수동 빌더 지양)
- [ ] Repository 테스트는 Testcontainers 실제 PostgreSQL 사용
- [ ] 도메인 라벨 1개 + `test` 라벨 부착