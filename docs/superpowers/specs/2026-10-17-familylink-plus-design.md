# FamilyLink Plus (아이 스마트폰 통합 제어 및 위치·사용기록 연동 시스템) 설계 명세서

- **작성일**: 2026-10-17
- **대상 플랫폼**: Android (부모용 앱 `:app-parent` / 자녀용 앱 `:app-child`, Min SDK 26 / Target SDK 34)
- **기술 스택**: Kotlin, Jetpack Compose (Material 3), Coroutines/Flow, Room, Hilt/DI, Firebase (Firestore, Auth, Messaging), Kakao Map & Local REST API
- **배포 형태**: 개인/가족용 APK 직접 설치 (멀티 모듈 분리 빌드: `app-parent-debug.apk`, `app-child-debug.apk`) + 선택적 ADB Device Owner 스크립트 제공

---

## 1. 프로젝트 배경 및 목적

기존 Google 패밀리 링크(Family Link)는 단순 일일 총 사용시간이나 앱별 누적 시간만 제공할 뿐, 다음과 같은 핵심 맥락을 파악할 수 없다.
1. **웹/미디어 상세 내역 부재**: 자녀가 크롬 등 브라우저에서 어떤 웹페이지(URL·제목)를 조회했는지, 유튜브에서 어떤 영상을 시청했는지 알 수 없다.
2. **공간·시간(위치 + 앱 사용) 결합 부재**: 자녀가 **어느 장소에서 어떤 앱을 얼마나 사용했는지**(예: *"학교 앞 편의점에서 30분 동안 유튜브 시청"*) 지도 위에서 직관적으로 확인할 수 없다.
3. **세밀한 주기적 알림 부재**: 자녀가 스마트폰을 사용하는 동안 **30분(수정 가능)마다** 부모에게 현재 사용 시간과 주로 쓴 앱/위치를 알려주는 능동적 알림이 없다.

**FamilyLink Plus**는 안드로이드 네이티브 시스템 API(`AccessibilityService`, `UsageStatsManager`, `FusedLocationProviderClient`, `DevicePolicyManager`, `SYSTEM_ALERT_WINDOW`)와 온디바이스 체류지점(Stay-Point) 병합 엔진을 결합하여 위 한계를 해결하는 부모-자녀 연동형 안드로이드 솔루션이다.

---

## 2. 멀티 모듈 아키텍처 구조

프로젝트는 유지보수성, 권한 분리, 타입 안전성을 위해 4개의 Gradle 모듈로 구성한다.

```text
familylink/
├── core-model/     # 부모·자녀 공통 도메인 모델, 정책 평가 순수 로직, 체류지점 클러스터링 알고리즘
├── core-data/      # Room 로컬 DB, Firestore 실시간 동기화 저장소, Kakao Local 역지오코딩 API, 데모/로컬 브릿지
├── app-child/      # 자녀용 APK: 접근성 서비스, 사용시간/위치 수집 데몬, 차단 오버레이, 삭제/우회 방지(TamperGuard)
├── app-parent/     # 부모용 APK: 위치+앱 사용 통합 지도, 웹/유튜브 타임라인, 시간대/앱 제어, 30분 알림 수신, 안심존/상태 대시보드
└── scripts/        # ADB 권한 일괄 부여 및 Device Owner 활성화 스크립트 (setup-child-device.sh)
```

### 모듈 분리의 이점
- **최소 권한 원칙**: 부모용 APK(`:app-parent`)에는 접근성 서비스나 기기 관리자와 같은 민감 권한을 포함하지 않아 부모 스마트폰의 성능과 보안에 영향을 주지 않는다.
- **도메인 로직 공유**: `:core-model`에 정의된 정책 평가기(`PolicyEvaluator`), 체류지점 병합기(`StayPointClusterer`), 30분 마일스톤 계산기(`MilestoneTracker`)를 부모/자녀 앱이 동일하게 공유하고 단위 테스트로 검증한다.

---

## 3. 핵심 기능별 상세 설계 (FR-1 ~ FR-6)

### FR-1 (요구사항 4.1 & 4.6): 브라우저 웹페이지 및 유튜브 시청 기록 수집
- **수집 메커니즘 (`ChildAccessibilityService`)**:
  - `TYPE_WINDOW_STATE_CHANGED` 및 `TYPE_WINDOW_CONTENT_CHANGED` 이벤트를 수신하여 현재 포그라운드 패키지명을 식별한다.
  - **브라우저 URL 및 페이지 제목 추출**:
    - Chrome (`com.android.chrome`): `com.android.chrome:id/url_bar` 또는 `location_bar` 텍스트와 현재 활성 탭의 `AccessibilityNodeInfo` 타이틀 추출.
    - Samsung Internet (`com.sec.android.app.sbrowser`): `com.sec.android.app.sbrowser:id/location_bar_edit_text` 추출.
    - Naver Whale (`com.nhn.android.search`), Firefox (`org.mozilla.firefox`), Edge (`com.microsoft.emmx`) 등 주요 브라우저의 주소창 뷰 ID 및 `http/https` 패턴 매칭 지원.
  - **유튜브/SNS 콘텐츠 제목 추출**:
    - YouTube (`com.google.android.youtube`): 영상 재생 화면의 제목 노드 및 Shorts 오버레이 타이틀 노드를 추출하여 `ContentType.YOUTUBE_VIDEO` 또는 `ContentType.YOUTUBE_SHORTS`로 기록한다.
  - **노이즈 제거 및 디바운싱**:
    - 주소창에 포커스가 있는 상태(`isFocused == true`)에서 타이핑 중인 미완성 문자열은 무시한다.
    - 동일 URL 또는 동일 영상 제목이 3초 이내 반복 감지되거나 직전 기록과 동일한 경우 중복 저장하지 않고 체류 시간(`durationSeconds`)만 누적한다.

### FR-2 (요구사항 4.2): 위치 기록과 앱 사용 기록의 지도 동시 확인
- **온디바이스 Stay-Point(체류 지점) 클러스터링 (`StayPointClusterer`)**:
  - 자녀 기기에서 주기적으로 수집된 위치 좌표 $(lat, lng, timestamp)$를 분석하여 **반경 50m 이내에서 3분 이상 머무른 구간**을 하나의 `StayPointSession`으로 생성한다.
  - 반경 50m를 벗어나 이동 중인 구간은 이동 경로(`TransitPath`)로 분류한다.
- **한국어 상호명(POI) 역지오코딩 (`PlaceNameResolver`)**:
  - 체류 지점이 확정되거나 갱신될 때 **Kakao Local REST API**를 호출한다:
    1. 주변 주요 시설/편의점/학원/학교 키워드 및 카테고리 검색 (`/v2/local/search/category.json`, `/v2/local/search/keyword.json`)으로 반경 35m 내 대표 상호명과 주변 기준 건물(학교 등)을 결합해 **"OO초등학교 앞 GS25"**, **"역삼동 수학학원"** 형태의 친숙한 장소명을 생성한다.
    2. 상호명이 없을 경우 도로명/지번 주소(`/v2/local/geo/coord2address.json`)를 반환한다.
- **위치 세션 + 앱 사용/웹 기록 병합**:
  - 각 `StayPointSession(startTime, endTime, placeName, lat, lng)` 내부에 해당 시간 동안 실행된 앱별 사용 시간(`List<AppUsageSummary>`)과 방문 웹페이지/유튜브 목록(`List<WebContentLog>`)을 결합한다.
- **부모 앱 지도 UI (`UsageMapScreen`)**:
  - 날짜 선택기(오늘/어제/특정일) 및 시간대 필터를 제공한다.
  - 각 체류 지점 마커 위에 **`📍 학교앞 편의점 | 유튜브 30분 · 크롬 10분`** 형태의 요약 배지를 직접 렌더링한다.
  - 마커를 탭하면 하단 바텀시트에 체류 시간(예: `15:10 ~ 15:50, 40분 체류`), 앱별 사용 시간 막대그래프, 그 장소에서 조회한 크롬 웹페이지 및 유튜브 영상 리스트를 동시에 표시한다.

### FR-3 (요구사항 4.3 & 4.6): 사용 시간대 정의, 일일 총 사용시간 제한, 즉시 잠금/보너스 시간
- **정책 구성 (`ControlPolicy`)**:
  - **허용 시간대 (`AllowedTimeWindow`)**: 요일별 시작 시각 ~ 종료 시각 (기본값: **오전 10:00 ~ 오후 9:00 (`10:00 ~ 21:00`)**).
  - **일일 총 사용시간 한도 (`dailyLimitMinutes`)**: 허용 시간대 내에서도 하루 최대 사용 가능한 총 시간(예: 120분, 비활성화 가능).
  - **원격 즉시 제어**:
    - `isImmediateLocked: Boolean` — 부모가 '지금 즉시 잠금' 활성화 시 시간대와 무관하게 즉시 잠금.
    - `bonusMinutesToday: Int` — 부모가 '+15분 / +30분 / +60분 보너스 부여' 클릭 시 당일 한도 및 허용 시간을 즉시 연장.
- **정책 평가 우선순위 (`PolicyEvaluator`)**:
  1. `ALWAYS_ALLOWED` 앱(전화, 문자, 긴급통화, 설정 내 허용된 화면)은 언제나 실행 허용.
  2. `isImmediateLocked == true` 이면 차단(`LockReason.PARENT_INSTANT_LOCK`).
  3. 현재 시각이 허용 시간대(`10:00 ~ 21:00`) 밖이고 보너스 시간 부여 상태가 아니면 차단(`LockReason.OUTSIDE_ALLOWED_HOURS`).
  4. 당일 총 사용시간이 `dailyLimitMinutes + bonusMinutesToday`를 초과하면 차단(`LockReason.DAILY_QUOTA_EXCEEDED`).
  5. 현재 실행 앱이 `BLOCKED` 상태이거나 앱별 일일 허용 시간(`appLimitMinutes`)을 초과하면 차단(`LockReason.APP_BLOCKED` 또는 `LockReason.APP_QUOTA_EXCEEDED`).

### FR-4 (요구사항 4.4): 사용 가능한 앱 정의 (앱 허용/차단 및 개별 시간 제한)
- **설치 앱 목록 동기화**:
  - 자녀 앱은 런처 실행 가능 앱(`Intent.CATEGORY_LAUNCHER`) 목록(패키지명, 앱 이름, 카테고리)을 수집하여 부모 앱과 동기화한다.
- **앱별 제어 모드 (`AppAccessMode`)**:
  - `ALWAYS_ALLOWED`: 잠금 시간에도 항상 사용 가능 (예: 전화, 메시지, 사전 앱).
  - `ALLOWED`: 허용 시간대(`10:00 ~ 21:00`) 내에서 사용 가능 + 선택적으로 앱별 일일 최대 사용 시간(예: 유튜브 하루 40분) 설정 가능.
  - `BLOCKED`: 모든 시간대에 실행 차단.
- **차단 오버레이 (`LockOverlayView`)**:
  - 차단 조건 충족 시 `WindowManager` (`TYPE_APPLICATION_OVERLAY`) 및 접근성 홈 이동(`GLOBAL_ACTION_HOME`)을 결합해 즉각 차단 화면을 표시한다.
  - 차단 화면에는 차단 사유, 남은 보너스 시간 요청 버튼, 긴급 전화 걸기 버튼, 부모 비상 PIN(4~6자리) 입력 해제 버튼을 제공한다.

### FR-5 (요구사항 4.5): 사용 시간 30분마다 부모 알림 (주기 수정 가능)
- **마일스톤 알림 엔진 (`UsageMilestoneNotifier`)**:
  - 부모는 알림 주기를 기본 **30분**(10분, 15분, 30분, 45분, 60분, 90분, 120분 등 자유 수정 가능)으로 설정할 수 있다.
  - 자녀의 당일 누적 활성 화면 사용 시간(`totalUsedMinutesToday`)을 1분 단위로 추적하여, $\lfloor \text{usedMinutes} / \text{intervalMinutes} \rfloor$ 단계가 증가할 때마다(예: 30분, 60분, 90분, 120분...) `UsageMilestoneEvent`를 생성한다.
  - **알림 메시지 구성**:
    - 제목: `⏰ 자녀 스마트폰 사용 60분 경과 (30분 주기 알림)`
    - 본문: `현재 위치: 학교앞 편의점 | 최근 30분간 유튜브(22분), 크롬(8분) 사용 중`
  - 부모 앱에서 알림을 탭하면 즉시 해당 자녀의 위치+사용기록 지도 화면이나 원격 제어 화면으로 이동하며, 알림 액션 버튼으로 **'지금 잠그기'**를 바로 실행할 수 있다.

### FR-6 (요구사항 4.6): 우회/삭제 방지, 안심존(Geofencing), 단말기 상태 모니터링
- **우회 및 삭제 방지 (`TamperGuard`)**:
  - **기기 관리자 (`ChildDeviceAdminReceiver`)**: 활성화 시 일반 앱 삭제 버튼이 비활성화된다.
  - **설정 화면 접근 차단**: 자녀가 `com.android.settings` 또는 패키지 인스톨러(`com.google.android.packageinstaller`)에서 다음 키워드/화면(접근성 서비스 해제, 기기 관리자 해제, FamilyLink Plus 앱 정보/강제종료/저장공간 삭제, 날짜 및 시간 수동 변경)에 진입하면 접근성 서비스가 즉시 감지하여 **부모 PIN 입력 잠금 오버레이**를 띄운다.
  - **ADB 완전 보호 스크립트 (`scripts/setup-child-device.sh`)**: 개인/가족용 설치 장점을 극대화하기 위해 PC에 자녀 폰을 연결해 1회 실행하면 모든 필수 권한(`PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW`, `WRITE_SECURE_SETTINGS` 접근성 자동 복구, `dpm set-device-owner`)을 자동 활성화하는 스크립트를 제공한다.
- **안심존 진·출입 알림 (`SafeZoneManager`)**:
  - 부모가 지도에서 집, 학교, 학원 등의 위치와 반경(100m ~ 500m)을 등록하면 자녀 기기가 진입(`ENTER`) 및 이탈(`EXIT`) 시점을 감지하여 부모에게 즉시 알림을 발송한다.
- **단말기 상태 실시간 모니터링 (`DeviceHealthMonitor`)**:
  - 자녀 기기의 **배터리 잔량(%) 및 충전 상태**, **소리 모드(벨소리/진동/무음)**, **네트워크 상태(Wi-Fi/모바일 데이터)**, **핵심 권한 정상 작동 여부(접근성·위치·사용정보 권한 상태)**, **마지막 연결 시각**을 부모 앱 상단 상태 바에 실시간 표시한다.

---

## 4. 데이터 모델 및 저장소 스키마 (`:core-model` & `:core-data`)

### 주요 도메인 엔티티
```kotlin
data class ControlPolicy(
    val childId: String,
    val allowedStartHour: Int = 10,        // 기본 오전 10시
    val allowedStartMinute: Int = 0,
    val allowedEndHour: Int = 21,          // 기본 오후 9시
    val allowedEndMinute: Int = 0,
    val dailyLimitMinutes: Int? = 180,     // 일일 총 사용시간 제한 (null이면 무제한)
    val bonusMinutesToday: Int = 0,        // 오늘 부여된 보너스 시간(분)
    val isImmediateLocked: Boolean = false,// 원격 즉시 잠금 여부
    val alertIntervalMinutes: Int = 30,    // 부모 알림 주기 (기본 30분, 수정 가능)
    val parentPinHash: String = "0000",    // 부모 잠금 해제 PIN
    val appRules: Map<String, AppRule> = emptyMap(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)

data class AppRule(
    val packageName: String,
    val appName: String,
    val accessMode: AppAccessMode,         // ALWAYS_ALLOWED, ALLOWED, BLOCKED
    val dailyQuotaMinutes: Int? = null     // 개별 앱 일일 제한 시간(분)
)

data class StayPointSession(
    val sessionId: String,
    val childId: String,
    val dateKey: String,                   // "2026-10-17"
    val latitude: Double,
    val longitude: Double,
    val placeName: String,                 // 예: "역삼초 앞 GS25 편의점"
    val address: String,
    val arrivedAtMillis: Long,
    val departedAtMillis: Long,
    val appUsages: List<AppUsageSegment>,  // 해당 장소에서 사용한 앱 및 시간 목록
    val webLogs: List<WebContentLog>       // 해당 장소에서 조회한 웹/유튜브 목록
)

data class WebContentLog(
    val id: String,
    val timestampMillis: Long,
    val sourcePackage: String,             // com.android.chrome, com.google.android.youtube 등
    val contentType: ContentType,          // WEB_PAGE, SEARCH_QUERY, YOUTUBE_VIDEO, YOUTUBE_SHORTS
    val title: String,                     // 웹페이지 타이틀 또는 유튜브 영상 제목
    val urlOrQuery: String,                // 전체 URL 또는 검색어
    val durationSeconds: Int,
    val placeName: String?                 // 조회 당시 체류 장소명
)
```

### Firestore 컬렉션 구조
- `families/{familyId}`: 가족 그룹 메타데이터 및 부모/자녀 페어링 코드(6자리)
- `families/{familyId}/children/{childId}/policy/current`: 실시간 제어 정책 (`ControlPolicy`)
- `families/{familyId}/children/{childId}/status/health`: 단말기 상태 (`DeviceHealth`)
- `families/{familyId}/children/{childId}/staySessions/{sessionId}`: 위치+앱 사용 통합 세션 (`StayPointSession`)
- `families/{familyId}/children/{childId}/webLogs/{logId}`: 브라우저 및 유튜브 상세 로그 (`WebContentLog`)
- `families/{familyId}/children/{childId}/alerts/{alertId}`: 30분 주기 알림, 안심존 진·출입 알림, 우회 시도 알림 (`ParentAlert`)
- `families/{familyId}/children/{childId}/safeZones/{zoneId}`: 안심존 설정 (`SafeZone`)

---

## 5. 예외 처리, 보안 및 테스트 전략

1. **오프라인 및 데모/시뮬레이션 모드 지원**:
   - 자녀 폰의 네트워크가 일시 끊겨도 정책 집행(시간대 잠금, 앱 차단)과 체류지점/웹 기록 수집은 로컬 Room DB를 통해 100% 정상 동작하며, 연결 복구 시 자동 동기화된다.
   - 개발·검증 및 즉시 체험을 위해 외부 Firebase/Kakao API 키가 설정되지 않은 환경에서도 로컬 동기화 저장소와 실제 서울 학군(예: 학교, 학교앞 편의점, 학원, 집) 시나리오 데이터를 포함한 **인터랙티브 시뮬레이션/프리뷰 모드**를 기본 내장한다. (`local.properties`에 실제 키 입력 시 즉시 실서버/실지도 모드로 전환)
2. **배터리 및 성능 최적화**:
   - 화면이 꺼진 상태(`ACTION_SCREEN_OFF`)에서는 접근성 노드 탐색과 앱 사용시간 폴링을 일시 중지하고, 위치 추적 주기를 절전 모드(Balanced Power Accuracy)로 자동 전환한다.
3. **검증 및 테스트 계획**:
   - **단위 테스트 (`:core-model`, `:core-data`)**:
     - `PolicyEvaluatorTest`: 오전 10시~오후 9시 시간대 경계값, 보너스 시간 합산, `ALWAYS_ALLOWED`/`BLOCKED` 앱 판정, 즉시 잠금 우선순위 검증.
     - `StayPointClustererTest`: GPS 좌표 스트림과 앱 실행 로그를 입력받아 `"학교앞 편의점에서 유튜브 30분 사용"` 세션으로 정확히 병합되는지 검증.
     - `UsageMilestoneTrackerTest`: 30분(및 사용자 변경 주기 15분/45분 등) 경과 시점마다 중복 없이 정확히 부모 알림 이벤트가 발생하는지 검증.
     - `BrowserContentExtractorTest`: 크롬/삼성인터넷/유튜브 노드 트리에서 타이핑 중 노이즈를 걸러내고 URL과 영상 제목만 추출하는지 검증.
   - **빌드 및 통합 검증**:
     - `./gradlew testDebugUnitTest assembleDebug`를 통해 `:core-model`, `:core-data`, `:app-child`, `:app-parent` 전체 유닛 테스트 통과 및 두 APK(`app-parent-debug.apk`, `app-child-debug.apk`) 정상 생성 확인.
