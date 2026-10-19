# 로드맵 2~7 정비 설계 (2026-10-19)

Firebase 동기화(로드맵 1) 이후 남은 우선순위 과제 6건을 기존 구조 안에서 정비한다.
각 Task는 기존 인증/동기화 흐름(Firestore 실시간, StateFlow 구독, TamperGuard, 알림 라우팅)을 침해하지 않는다.

## 범위 밖 (명시)

- FCM 백그라운드 푸시, App Check, 규칙 정밀 강화(인증 도입 전 의미상 한계), 지오펜싱 이탈 푸시, 다자녀, 웹 제어
- Kakao REST 키 실사용 (local.properties 키 있으면 우선, 없으면 Geocoder/랜드마크 폴백 — 중간 점 유지)

## T1: 위치추적 (로드맵 2)

**목표**: 자녀 폰에서 체류 지점이 데모 시드가 아닌 실 GPS로 기록된다.

- `DomainModels.DeviceHealth.currentPlaceName` 기본값을 데모 장소명("역삼초 앞 GS25 편의점")에서 `""`(중립)로 교체 — 룰링 ①의 근본 해소. 데모 장소명은 `DemoScenarioSeed`만 가진다.
- `ChildMonitoringForegroundService`: 15초 루프마다 위치 수집.
  - `LocationManager.getLastKnownLocation(GPS_PROVIDER/NETWORK_PROVIDER/PASSIVE_PROVIDER)` (FusedLocation 의존성 추가 없음)
  - 권한 미허용이면 건너뛰고 `deviceHealth.locationPermissionEnabled=false` 유지
  - 장소명 해석 순서: Kakao 키 → `Geocoder`(기기 지원 시) → 250m 내 알려진 랜드마크 → "위치 미확인"
  - `deviceHealth`에 실 좌표/장소명 반영 (웹 대시보드·부모 앱 즉시 표시)
- 체류 세션: `ChildMonitorSessionTicker` (core-data 순수 로직) — 장소 기준 250m 이상 변화 시 이전 체류를 `departedAtMillis` 갱신 완료 세션으로 `recordStayPointSession`, 새 체류는 진행 중 세션(`departedAtMillis=0`) 기록.
- `ChildMainActivity`: 위치 권한 런타임 요청 (기존 알림 권한 요청 패턴과 동일한 1회 흐름) + 대시보드 카드 2개 사이에 위치 카드 추가.

## T2: PIN 보안 (로드맵 3)

- 부모 앱 `PolicyControlTab`에 "부모 PIN 변경" 섹션 추가: 4~6자리 검증, 기본값 0000 사용 중 경고 배지.
- `repository.updateParentPin()` 사용 (인터페이스 이미 존재) — Firestore 동기화로 자녀 `LockOverlayScreen` PIN이 즉시 교체된다.
- 자녀 화면 쪽 추가 작업 없음 (이미 `policy.parentPin` 사용).

## T3: 영구저장 (로드맵 4)

- `LocalFamilySyncRepository`(데모/오프라인 경로)가 `storageFile`에 **전체 상태**(policy/deviceHealth/staySessions/webLogs/alerts/safeZones)를 JSON으로 저장·복원하도록 확장 — 현재는 policy만 영구화.
- `FamilyRepositoryProvider`의 filesDir 기반 파일을 그대로 사용.
- 대상은 로컬 저장소만 (Firestore 경로는 클라우드 영구, 저장 중복 불필요).

## T4: 삼성 설정 대응 (로드맵 5)

- `TamperGuardDetector` 키워드 보강: "디바이스 관리자"(삼성 표기), "설치된 앱"(삼성 설정의 앱 목록 화면) — 기존 `mentionsProtectedApp` 게이트 유지로 과잉 차단 방지.
- `scripts/setup-child-device.sh` 유지 (이미 ADB 원클릭 지원).
- 자녀 대시보드의 활성화 안내에 갤럭시 절차 위치(설정 > 생체 및 보안 > 기기 관리자) 문구 추가는 기존 카드 문서로 충분 — 별도 UI 없음.

## T5: 알림 권한 보강 (로드맵 6)

- 부모 앱: POST_NOTIFICATIONS 권한이 거부된 상태면 요약 화면에 재요청 카드 표시 (기존 런타임 요청 + 알림 라우팅은 Task 5로 완료됨 — 이어서 거부 상태 UX만 추가).

## T6: 접근성 튜닝 (로드맵 7)

- `ChildAccessibilityService`: 화면 꺼짐(`PowerManager.isInteractive=false`) 중 이벤트 스킵 (배터리 절약), 동일 창 반복 이벤트 스래딩(2초 스로틀) 유지.
- 차단 키워드는 T4에서 관리 — 이 태스크는 서비스 라이프사이클 튜닝만.

## 배포 (완료 후)

- 전체 테스트 + `assembleDebug` 통과 → `feature/firebase-sync` → `main` 병합, 푸시.
- 앱 배포는 APK 산출물 전달 (내부 배포) — Play 스토어 게시는 범위 밖.
- 웹 대시보드는 이미 배포 완료 (newace77/familylink-dashboard Pages).
