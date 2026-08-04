---
name: "✨ 기능 개발"
about: API 구현을 포함한 신규 기능 개발
title: "[FEAT] "
labels: feat
---

## 작업 내용

<!-- 무엇을 만드는지 한두 줄로 -->

## 관련 API (해당 시)

- Method / Path:
- `docs/api-docs.json` 기준 요청·응답 스키마 확인함

## Notion 카드 (해당 시)

<!-- 계획서 단계에서 이미 만들어둔 작업 카드(작업 트래커 DB)가 있으면 그 카드 URL을 적어주세요.
     제목만으로는 매칭이 잘 안 돼서(예: 카드 1개가 이슈 여러 개로 쪼개지는 경우) 여기 기재된 URL로 자동 연결합니다. -->

## 체크리스트

- [ ] TDD로 진행 (`test(red)` → `test(green)` → `refactor` 커밋 순서)
- [ ] Swagger 어노테이션은 `controller/api/*Api` 인터페이스에만 작성
- [ ] Setter 대신 정적 팩토리 메서드 / 의도가 드러나는 메서드명 사용
- [ ] 외부 API 호출이 있다면 Feign Client 사용 (RestTemplate 금지)
- [ ] 커버리지 확인 (`docs/conventions.md` §9)
- [ ] 도메인 라벨 1개 + `feat` 라벨 부착

## 참고

<!-- 관련 ADR Discussion 링크, 디자인 시안 등 -->