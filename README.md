# FamilyLink Plus (부모-자녀 안드로이드 스마트폰 통합 제어 시스템)

기존 구글 패밀리 링크(Family Link)에서 제공하지 않는 **브라우저 방문 웹페이지(URL·검색어)/유튜브 시청 영상 제목 확인**, **위치 기록과 앱 사용 기록의 지도 위 동시 확인(예: "학교앞 편의점에서 유튜브 30분 사용")**, **30분(수정 가능) 주기 부모 알림**, **사용 시간대(오전 10시~오후 9시) 및 앱별 제어**, **우회/삭제 방지(`TamperGuard`)**를 구현한 안드로이드 멀티 모듈 프로젝트입니다.

---

## 📦 모듈 구성 및 빌드 산출물

| 모듈 | 역할 | 빌드 산출물 |
| :--- | :--- | :--- |
| `:core-model` | 정책 평가(`PolicyEvaluator`), 체류지점+앱 사용 병합(`StayPointClusterer`), 30분 주기 알림(`UsageMilestoneTracker`), 브라우저/유튜브 파서(`BrowserContentFilter`), 우회 감지(`TamperGuardDetector`), 안심존(`SafeZoneEvaluator`) | JVM 라이브러리 |
| `:core-data` | 카카오 로컬 API 한국 상호명 역지오코딩(`PlaceNameResolver`), 부모-자녀 실시간 정책/로그 동기화(`FamilySyncRepository`), 한국 학군 시뮬레이션 시드(`DemoScenarioSeed`) | Android 라이브러리 |
| `:app-child` | 자녀 폰 설치용 앱 — 접근성 서비스(`ChildAccessibilityService`), 포그라운드 모니터링 데몬(`ChildMonitoringForegroundService`), 기기 관리자(`ChildDeviceAdminReceiver`), 차단 오버레이(`LockOverlayScreen`) | `app-child/build/outputs/apk/debug/app-child-debug.apk` |
| `:app-parent` | 부모 폰 설치용 앱 — 위치+앱 사용 통합 지도 탭, 웹/유튜브 히스토리 탭, 시간대/앱/30분 알림 제어 탭, 알림/안심존/단말기 상태 대시보드 | `app-parent/build/outputs/apk/debug/app-parent-debug.apk` |

---

## 🚀 빌드 및 설치 방법

### 1. 전체 유닛 테스트 및 APK 빌드
```bash
./gradlew testDebugUnitTest assembleDebug
```

### 2. 자녀 스마트폰 원클릭 보호 권한 & 삭제 방지 활성화 (ADB)
자녀 폰에 `app-child-debug.apk`를 설치한 뒤 아래 스크립트를 실행하면 접근성 서비스, 사용정보 접근, 오버레이, 기기 관리자/Device Owner 권한이 한 번에 설정됩니다.
```bash
./scripts/setup-child-device.sh
```
