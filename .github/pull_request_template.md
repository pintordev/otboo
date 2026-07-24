## 변경 사항

<!-- 무엇을 왜 바꿨는지 -->

## 관련 이슈

Closes #

## 변경 유형

- [ ] `feat` 기능 개발
- [ ] `fix` 버그 수정
- [ ] `refactor` 리팩토링
- [ ] `docs` 문서화
- [ ] `test` 테스트
- [ ] `chore` 기타 작업
- [ ] `batch` 배치
- [ ] `deploy` 배포/인프라
- [ ] `adhoc` 즉흥 작업

## 체크리스트

- [ ] `docs/conventions.md` 컨벤션 준수 (Setter 금지, 정적 팩토리 메서드, Feign Client, `@Transactional(readOnly=true)` 기본 등)
- [ ] TDD로 작업했음 (`test(red)`/`test(green)`/`refactor` 커밋)
- [ ] 테스트 통과 + 커버리지 80% 유지
- [ ] API 변경이 있다면 `docs/api-docs.json`/`docs/api-spec.md`와 일치 확인 — FE(`otboo-fe`)가 이미 이 스펙 기준으로 구현돼 있어 임의 변경 시 FE가 깨짐
- [ ] `dev` 브랜치 최신화 후 conflict 없음
- [ ] 2인 이상 리뷰 요청함

## 스크린샷/데모 (선택)

## 리뷰어에게

<!-- 특히 봐줬으면 하는 부분 -->