---
name: "🚀 배포/인프라"
about: CI/CD, Docker, ECS, Secrets 등 배포·인프라 관련 작업
title: "[DEPLOY] "
labels: deploy
---

## 작업 내용

<!-- 예: GitHub Actions CD 파이프라인 구성, Redis ElastiCache 전환 -->

## 영향 범위

- [ ] 로컬 개발 환경에 영향 있음
- [ ] CI/CD 파이프라인에 영향 있음
- [ ] 운영 환경에 영향 있음 (배포 중단 가능성 명시)

## 롤백 계획

## 체크리스트

- [ ] 관련 환경변수는 `docs/환경변수 체크리스트.md`에 반영
- [ ] 헬스체크 실패 시 자동 롤백 조건 확인(운영 배포 관련이면)
- [ ] AWS 비용 영향 있으면 예산($150/월) 대비 점검
- [ ] 도메인 라벨(`infra`) + `deploy` 라벨 부착