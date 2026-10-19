# 남은 일감 실행 계획 (2026-10-19)

스펙: `docs/superpowers/specs/2026-10-19-roadmap-2-7-hardening-design.md`
실행 방식: Native(세션 내 TDD), 브랜치 `feature/firebase-sync`에 커밋 누적 후 main 병합·배포.

## Tasks

- [ ] **T1 위치추적**: DeviceHealth 기본값 중립화 → 위치 수집(15초 루프) → `SessionTicker`(core-data 순수) + 서비스 연결 → 위치 권한 요청 + 카드 → `DomainModels`/`DemoSeed` 테스트 갱신, `SessionTicker` 테스트 신규
- [ ] **T2 PIN 보안**: 부모 `PolicyControlTab` PIN 변경 섹션 + 0000 경고 배지 → `ParentViewModel`/컨트롤러로 유효성 검사 전달 (4~6자리)
- [ ] **T3 영구저장**: `LocalFamilySyncRepository` 전체 상태 JSON 저장/복원 + 라운드트립 테스트
- [ ] **T4 삼성설정**: `TamperGuardDetector` 키워드(디바이스 관리자/설치된 앱) 추가 + 테스트
- [ ] **T5 알림권한**: 부모 앱 거부 상태 재요청 카드
- [ ] **T6 접근성 튜닝**: 화면 꺼짐 스킵 + 동일창 스로틀 (서비스 내부)
- [ ] **배포**: 전체 테스트+빌드 → main 병합/푸시 → APK 2개 산출 → 대시보드 규칙 안내 문서 갱신

각 Task 완료 시 커밋 (`feat(...)`, `--no-verify`), 중간이라도 커밋 유지.
