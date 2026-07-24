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

## 체크리스트

- [ ] TDD로 진행 (`test(red)` → `test(green)` → `refactor` 커밋 순서)
- [ ] Swagger 어노테이션은 `controller/api/*Api` 인터페이스에만 작성
- [ ] Setter 대신 정적 팩토리 메서드 / 의도가 드러나는 메서드명 사용
- [ ] 외부 API 호출이 있다면 Feign Client 사용 (RestTemplate 금지)
- [ ] 커버리지 확인 (`docs/conventions.md` §9)
- [ ] 도메인 라벨 1개 + `feat` 라벨 부착

## 참고

<!-- 관련 ADR Discussion 링크, 디자인 시안 등 -->