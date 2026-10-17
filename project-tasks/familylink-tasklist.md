# FamilyLink Plus Task List

- **Spec Reference**: `project-specs/familylink-setup.md` & `docs/superpowers/specs/2026-10-17-familylink-plus-design.md`
- **Plan Reference**: `docs/superpowers/plans/2026-10-17-familylink-plus-plan.md`

### [x] Task 1: Multi-Module Gradle Scaffolding & Policy Evaluation Engine (`:core-model`)
- Setup Gradle 9.5.1 multi-module build (`:core-model`, `:core-data`, `:app-child`, `:app-parent`).
- Implement `ControlPolicy`, `AppRule`, `AppAccessMode`, `LockDecision`, `LockReason`, and `PolicyEvaluator` (4.3 사용 시간대 오전 10시~오후 9시, 4.4 사용 가능한 앱 정의, 4.6 일일 총 사용시간 제한 및 즉시 잠금/보너스 시간).
- Validate with `PolicyEvaluatorTest` (PASS).

### [x] Task 2: Stay-Point Location+Usage Merger, 30-Min Milestone Tracker & Browser/Tamper Parsers (`:core-model`)
- Implement `StayPointClusterer` (4.2 위치 기록과 사용 기록을 지도에서 동시에 확인 — 예: 학교앞 편의점에서 유튜브 30분 사용).
- Implement `UsageMilestoneTracker` (4.5 사용 시간 30분마다 부모 알림, 주기 수정 가능).
- Implement `BrowserContentFilter` (4.1 크롬 등 브라우저 조회 페이지 및 유튜브 시청 제목 추출).
- Implement `TamperGuardDetector` & `SafeZoneEvaluator` (4.6 우회/삭제 방지 및 안심존 지오펜싱).
- Validate with `StayPointAndTrackersTest` (PASS).

### [x] Task 3: Shared Data Layer, Kakao Local Reverse Geocoding & Family Sync Repository (`:core-data`)
- Implement `PlaceNameResolver` (Kakao Local REST API 역지오코딩 + 한국 상호명 조합).
- Implement `FamilySyncRepository` & `DemoScenarioSeed` (부모-자녀 실시간 정책/로그 동기화 및 한국 학군 시뮬레이션 데이터).
- Validate with `FamilySyncRepositoryTest` (PASS).

### [x] Task 4: Child Android App (`:app-child`) — Accessibility Service, Usage/Location Daemon, Lock Overlay & TamperGuard
- Implement `ChildAccessibilityService`, `ChildMonitoringForegroundService`, `ChildDeviceAdminReceiver`.
- Implement `ChildDashboardScreen`, `LockOverlayScreen` (PIN 잠금 해제 및 보너스 시간 요청), and `scripts/setup-child-device.sh` (ADB 원클릭 권한/Device Owner 설정 스크립트).
- Validate with `ChildMonitorControllerTest` and `:app-child:assembleDebug` (PASS).

### [x] Task 5: Parent Android App (`:app-parent`) — Location+Usage Map, Web/YouTube History, Policy Controls & 30-Min Alerts
- Implement `LocationUsageMapTab` (체류지점+앱 사용시간 통합 지도 및 상세 바텀시트).
- Implement `WebHistoryTimelineTab` (크롬 웹페이지 URL 및 유튜브 시청 제목 타임라인).
- Implement `PolicyControlTab` (오전 10시~오후 9시 시간대 설정, 앱별 허용/차단/시간제한, 30분 알림 주기 수정, 즉시 잠금/+30분 보너스).
- Implement `AlertsAndSafeZonesTab` & `ParentNotificationHelper` (30분 주기 푸시 알림, 안심존 진·출입, 단말기 배터리/소리 상태 표시).
- Validate with `ParentViewModelTest` and full `./gradlew testDebugUnitTest assembleDebug` (PASS).
