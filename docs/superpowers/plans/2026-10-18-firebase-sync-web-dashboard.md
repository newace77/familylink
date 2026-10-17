# Firebase 실시간 동기화 + 웹 대시보드 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 부모 안드로이드 앱과 자녀 안드로이드 앱을 Firestore로 실시간 동기화하고, GitHub Pages 웹 대시보드(열람 전용)로 아이폰에서 열람 가능하게 한다.

**Architecture:** 기존 `FamilySyncRepository` 인터페이스 뒤에 `FirebaseFamilySyncRepository`를 추가한다(spec 접근 A). 모든 Firestore 문서는 모델을 JSON 문자열로 직렬화한 `{"data": "<json 문자열>"}` 형태로 저장하며, 직렬화의 단일 원천은 `ModelJson`이다. 상태의 진실원은 Firestore 하나이고 UI는 기존 StateFlow를 그대로 구독한다.

**Tech Stack:** Firebase BoM 34.0.0 (`com.google.firebase:firebase-firestore` — BoM v34부터 `-ktx` 폐통합, 사용 금지), google-services 플러그인 4.4.4 (조건부 적용), kotlinx-coroutines, org.json (기존), GitHub Pages.

**Spec:** `docs/superpowers/specs/2026-10-18-firebase-sync-web-dashboard-design.md`

## Global Constraints

- compileSdk 35, minSdk 26, Kotlin 2.2.10, JVM 17 — 기존 값 변경 금지
- Firebase 의존성: `com.google.firebase:firebase-bom:34.0.0` + `com.google.firebase:firebase-firestore` (`-ktx` 접미사 없이)
- google-services 플러그인 버전 4.4.4, **`app-*/google-services.json`이 존재할 때만 적용** (없으면 기존 데모 빌드가 그대로 성립)
- Android manifest 무변경 — 새 위험 권한 추가 금지. 유일 예외: POST_NOTIFICATIONS **런타임 요청**(Task 5)만 허용
- UI 텍스트는 한국어. `DemoScenarioSeed` 데모 동작 무손상 — 기존 테스트 전부 유지·통과
- 커밋 컨벤션: 기존 git log 스타일 (`feat(core-data): …`)

## Review Focus

실기기에서 가장 먼저 삐걱대기 쉬운 입력·조건 5가지와, 각각을 고정하는 테스트:

1. **없거나 잘못된 페어링 코드(6자리 아님 / 존재하지 않음)** → `linkFamilyCode` false 반환, `pairingState` 불변 → Task 2 테스트(`linkFamilyCode rejects non six digit input and keeps state`) + Task 4 테스트(`Firestore link rejects missing family`)
2. **손상·필드 누락 JSON 수신** → 크래시 없음; 파싱 실패 시 해당 StateFlow는 기존 값 유지(기본값으로 리셋 금지) → Task 1 테스트(`policyFromJson on empty JSONObject returns safe defaults`) + Task 4는 모든 파싱을 `runCatching`으로 감쌈
3. **동일 alertId 재도착(리스너 재첨부·오프라인 큐잉)** → 중복 표시 없음 → Task 4 테스트(`merger dedups reattached listener refire`)
4. **placeName이 null인 웹로그** → 부모 타임라인과 웹 대시보드에서 "위치 미확인" 표시, 크래시 없음 → Task 1 테스트(`webLog round-trips with null placeName`) + Task 8 체크리스트 6번 항목
5. **google-services.json 없는 환경에서 앱 실행** → Local 데모로 폴백, 크래시 없음 → Task 6 테스트(`selectRepositoryKind falls back to Local when Firebase unavailable`) + Task 3의 json 없는 빌드 확인

## 전제 조건 (실행 시작 전 사용자 수동 작업 — 1회)

Firebase 콘솔(console.firebase.google.com)에서: 새 프로젝트 생성(무료 Spark) → Firestore 데이터베이스 생성(테스트 모드) → 안드로이드 앱 2개 등록(`com.familylink.parent`, `com.familylink.child`) → 각 `google-services.json`을 `app-parent/`, `app-child/` 루트에 저장. **이 파일 없이도 아래 모든 Task는 통과해야 한다**(Review Focus 5). 실행 도중 json 파일을 넣으면 조건부 플러그인이 자동으로 Firebase 경로를 활성화한다.

---

### Task 1: ModelJson — 5종 모델 ↔ JSON 직렬화

**Files:**
- Create: `core-data/src/main/kotlin/com/familylink/data/ModelJson.kt`
- Test: `core-data/src/test/kotlin/com/familylink/data/ModelJsonTest.kt`

**Interfaces:**
- Consumes: `com.familylink.model`의 `ControlPolicy`, `DeviceHealth`, `SafeZone`, `ParentAlert`, `WebContentLog`, `StayPointSession` (기존 정의 — 변경 없음), `core-data`의 `DemoScenarioSeed`
- Produces: `object ModelJson`:
  - `fun policyToJson(policy: ControlPolicy): org.json.JSONObject` / `fun policyFromJson(json: org.json.JSONObject): ControlPolicy`
  - `fun deviceHealthToDevice...` 형식 X — 정확 이름: `deviceHealthToJson(health: DeviceHealth): org.json.JSONObject` / `deviceHealthFromJson(json: org.json.JSONObject): DeviceHealth`
  - `fun safeZonesToJson(zones: List<SafeZone>): org.json.JSONArray` / `fun safeZonesFromJson(array: org.json.JSONArray): List<SafeZone>`
  - `fun alertToJson(alert: ParentAlert): org.json.JSONObject` / `fun alertFromJson(json: org.json.JSONObject): ParentAlert`
  - `fun webLogToJson(log: WebContentLog): org.json.JSONObject` / `fun webLogFromJson(json: org.json.JSONObject): WebContentLog`
  - `fun staySessionToJson(session: StayPointSession): org.json.JSONObject` / `fun staySessionFromJson(json: org.json.JSONObject): StayPointSession`

  Task 4와 Task 7(app.js)이 이 서명/필드 규칙에 의존한다.

- [ ] **Step 1: 실패하는 테스트 작성**

Junit4 + Truth. 첫 테스트:

```kotlin
@Test fun `policy round-trips all scalar and appRules fields`() {
    val policy = DemoScenarioSeed.defaultPolicy()
    val json = ModelJson.policyToJson(policy)
    assertThat(ModelJson.policyFromJson(json)).isEqualTo(policy)
}
```

동일 형식으로 추가 7개: `deviceHealth round-trips all fields`, `safeZones round-trips radiusMeters decimals`, `alert round-trips optional placeName null`, `webLog round-trips with null placeName`, `staySession round-trips nested appUsages and webLogs`, `policyFromJson on empty JSONObject returns safe defaults`(필드 누락 → 모델 기본값; `dailyLimitMinutes` 미지정이면 null), `deviceHealthFromJson on empty JSONObject returns default DeviceHealth`.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :core-data:testDebugUnitTest --tests "com.familylink.data.ModelJsonTest"`
Expected: FAIL — `ModelJson` 미정의 참조

- [ ] **Step 3: ModelJson 구현**

필드 규칙(정확한 JSON 키 이름 — Firestore 문서와 웹 대시보드의 공용 계약):

- policy: `childId, childName, allowedStartHour, allowedStartMinute, allowedEndHour, allowedEndMinute, dailyLimitMinutes(null→-1), bonusMinutesToday, isImmediateLocked, alertIntervalMinutes, parentPin, tamperProtectionEnabled, updatedAtEpochMillis, appRules(배열: {packageName, appName, accessMode, dailyQuotaMinutes(null→-1), category})`
- deviceHealth: `childId, batteryPercent, isCharging, ringerState, networkState, currentPlaceName, currentLatitude, currentLongitude, currentForegroundApp, totalUsedMinutesToday, appUsedMinutesToday(map→JSONObject), accessibilityEnabled, usageStatsEnabled, overlayPermissionEnabled, deviceAdminEnabled, locationPermissionEnabled, lastUpdatedMillis`
- alert: `alertId, childId, type(enum name), title, message, timestampMillis, placeName(nullable), milestoneMinutes(nullable), isAcknowledged`
- webLog: `id, childId, timestampMillis, sourcePackage, browserOrAppName, contentType(enum name), title, urlOrQuery, durationSeconds, placeName(nullable), latitude(nullable), longitude(nullable)`
- staySession: `sessionId, childId, dateKey, latitude, longitude, placeName, address, arrivedAtMillis, departedAtMillis, appUsages(배열), webLogs(배열: webLogToJson)`
- AppRule↔JSON, AppUsageSegment↔JSON은 private 헬퍼 (공개 계약 아님)
- 모든 fromJson은 `optXxx(키, 모델 기본값)` 기반 — 크래시 불가; `dailyLimitMinutes`는 `optInt(..., -1).takeIf { it > 0 }`

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :core-data:testDebugUnitTest`
Expected: PASS — 기존 `FamilySyncRepositoryTest` 포함 전부

- [ ] **Step 5: 커밋**

```bash
git add core-data/src/main/kotlin/com/familylink/data/ModelJson.kt core-data/src/test/kotlin/com/familylink/data/ModelJsonTest.kt
git commit -m "feat(core-data): 모델 5종 JSON 직렬화 계층 추가"
```

---

### Task 2: 인터페이스 확장 + PairingState + Local stub

**Files:**
- Modify: `core-data/src/main/kotlin/com/familylink/data/FamilySyncRepository.kt` (인터페이스 + `LocalFamilySyncRepository`)
- Test: `core-data/src/test/kotlin/com/familylink/data/FamilySyncRepositoryTest.kt` (기존 유지 + 추가)

**Interfaces:**
- Consumes: 기존 `FamilySyncRepository`/`LocalFamilySyncRepository` (Task 1 결과에 논리 의존 없음)
- Produces (Task 4·6가 의존):
  - `enum class PairingState { NOT_PAIRED, WAITING, LINKED }` (같은 파일에 정의)
  - 인터페이스 신규 3원소: `suspend fun createFamilyCode(): String`, `suspend fun linkFamilyCode(code: String): Boolean`, `val pairingState: kotlinx.coroutines.flow.StateFlow<PairingState>`

- [ ] **Step 1: 실패하는 테스트 추가**

```kotlin
@Test fun `pairing state defaults to NOT_PAIRED`() { /* repo.pairingState.value == NOT_PAIRED */ }
@Test fun `createFamilyCode returns six digit code and moves to WAITING`() { /* createFamilyCode() 길이==6 && 상태==WAITING */ }
@Test fun `linkFamilyCode with any six digit code demo-links`() { /* createFamilyCode 후 linkFamilyCode("123456")==true && 상태==LINKED */ }
@Test fun `linkFamilyCode rejects non six digit input and keeps state`() { /* createFamilyCode 후 linkFamilyCode("abc")==false && 상태==WAITING 유지 */ }
```

각 검증값은 위 괄호의 기대 결과 그대로.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :core-data:testDebugUnitTest`
Expected: FAIL — `PairingState`/페어링 메서드 미존재

- [ ] **Step 3: 구현**

- 인터페이스에 위 3원소 추가, 파일에 `PairingState` enum 정의
- `LocalFamilySyncRepository`: `_pairingState = MutableStateFlow(PairingState.NOT_PAIRED)`; `createFamilyCode()`은 `Random`으로 6자리 생성 → WAITING 후 코드 반환; `linkFamilyCode(code)`: 6자리 && 숫자면 데모 규칙 **임의 코드도 LINKED**(데모 흐름 유지 — 실제 규칙은 Task 4가 교체), 무효 입력은 false + 상태 불변

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :core-data:testDebugUnitTest` — Expected: PASS (기존 테스트 포함)

- [ ] **Step 5: 커밋**

```bash
git add core-data/src/main/kotlin/com/familylink/data/FamilySyncRepository.kt core-data/src/test/kotlin/com/familylink/data/FamilySyncRepositoryTest.kt
git commit -m "feat(core-data): 페어링 메서드 3원소 + PairingState 인터페이스 확장"
```

---

### Task 3: Gradle Firebase 의존성 + FirestoreSchema

**Files:**
- Modify: `build.gradle.kts` — 루트 plugins 블록에 `id("com.google.gms.google-services") version "4.4.4" apply false`
- Modify: `core-data/build.gradle.kts` — dependencies에 `implementation(platform("com.google.firebase:firebase-bom:34.0.0"))`, `implementation("com.google.firebase:firebase-firestore")`
- Modify: `app-parent/build.gradle.kts`, `app-child/build.gradle.kts` — plugins 블록 밖(파일 하단)에:

```kotlin
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
```

- Create: `core-data/src/main/kotlin/com/familylink/data/FirestoreSchema.kt`

**Interfaces:**
- Consumes: 없음
- Produces (Task 4·7가 동일 문자열 사용):
  `object FirestoreSchema { const val FAMILIES = "families"; const val POLICY = "policy"; const val DEVICE_HEALTH = "deviceHealth"; const val SAFE_ZONES = "safeZones"; const val PAIRED = "paired"; const val CHILD_LINKED = "childLinked"; const val ALERTS = "alerts"; const val WEB_LOGS = "webLogs"; const val STAY_SESSIONS = "staySessions"; const val DATA = "data"; const val TIMESTAMP_MILLIS = "timestampMillis"; const val PREFS_NAME = "familylink_pairing"; const val KEY_FAMILY_CODE = "family_code" }`

- [ ] **Step 1: 세 Gradle 파일 + FirestoreSchema.kt 작성** (위 상수 그대로)

- [ ] **Step 2: json 유무 무관 빌드 확인**

Run: `./gradlew :core-data:assembleDebug :app-parent:assembleDebug :app-child:assembleDebug`
Expected: PASS — 현재 json 없음 (Review Focus 5의 빌드 레벨 전제)

- [ ] **Step 3: 커밋**

```bash
git add build.gradle.kts core-data/build.gradle.kts app-parent/build.gradle.kts app-child/build.gradle.kts core-data/src/main/kotlin/com/familylink/data/FirestoreSchema.kt
git commit -m "build(root): Firebase BoM 34 + google-services 조건부 플러그인, Firestore 스키마 상수"
```

---

### Task 4: FirebaseFamilySyncRepository 코어 + FamilyPairingStore

**Files:**
- Create: `core-data/src/main/kotlin/com/familylink/data/FamilyPairingStore.kt`
- Create: `core-data/src/main/kotlin/com/familylink/data/FirebaseFamilySyncRepository.kt`
- Create: `core-data/src/main/kotlin/com/familylink/data/SyncListMerger.kt`
- Test: `core-data/src/test/kotlin/com/familylink/data/SyncListMergerTest.kt`

**Interfaces:**
- Consumes: Task 1 `ModelJson`, Task 2 페어링 서명, Task 3 `FirestoreSchema`
- Produces (Task 5·6가 의존):
  - `class FamilyPairingStore(context: android.content.Context) { fun savedCode(): String?; fun save(code: String); fun clear() }`
  - `object SyncListMerger { fun <T> merge(current: List<T>, incoming: List<T>, idOf: (T) -> String): List<T> }` — incoming 우선, id로 dedup (정렬은 리스너 쿼리가 담당 — merge는 dedup만)
  - `class FirebaseFamilySyncRepository(db: com.google.firebase.firestore.FirebaseFirestore, pairingStore: FamilyPairingStore) : FamilySyncRepository { fun startSync(familyCode: String); val lastCreatedCode: StateFlow<String?> }` — `lastCreatedCode`는 부모 UI가 방금 생성한 코드를 표시하기 위한 마지막 생성 코드 흐름(createFamilyCode 성공 시 값 설정)

- [ ] **Step 1: SyncListMerger 실패 테스트**

```kotlin
@Test fun `merger dedups by id keeping incoming`() { /* current=[a(id=1)], incoming=[b(id=1), c(id=2)] → [b, c] */ }
@Test fun `merger dedups reattached listener refire`() { /* 동일 3개 문서를 두 번 merge → 결과 3개 (Review Focus 3) */ }
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :core-data:testDebugUnitTest --tests "*SyncListMergerTest*"` — Expected: FAIL

- [ ] **Step 3: SyncListMerger 구현**

`current.filterNot { idOf(it) in incoming.map(idOf) } + incoming` (incoming이 최신 — dedup 우선권)

- [ ] **Step 4: 통과 확인**

Run 동일 — Expected: PASS

- [ ] **Step 5: FamilyPairingStore 구현**

SharedPreferences(`FirestoreSchema.PREFS_NAME`), 3메서드 서명 그대로. `KEY_FAMILY_CODE` 상수 사용.

- [ ] **Step 6: FirebaseFamilySyncRepository 구현** (서명은 확정, 몸체는 아래 지침)

- 6종 StateFlow 모두 `DemoScenarioSeed` 기본값으로 초기화 — 리스너 도착 전 UI 공백 방지 (Local과 동일)
- `startSync(familyCode: String)`:
  - 단일 문서 리스너: `db.document("$FAMILIES/$familyCode").addSnapshotListener` → POLICY/DEVICE_HEALTH/SAFE_ZONES 3필드를 `data?.get(키) as? String` → `JSONObject(str)` → `ModelJson.*FromJson` → 대응 StateFlow 반영. **전 과정 `runCatching`, 파싱 실패 시 해당 StateFlow 무변경**(Review Focus 2)
  - PAIRED 필드는 맵 형태: `{childLinked: Boolean, atMillis: Long}` → `childLinked == true`면 `pairingState = LINKED`
  - 컬렉션 3종: `db.collection("$FAMILIES/$familyCode/$ALERTS").orderBy(TIMESTAMP_MILLIS, Query.Direction.DESCENDING).limit(N)` — alerts 100 / webLogs 200 / staySessions 200; `SyncListMerger.merge`로 반영
- 쓰기:
  - policy 수정 메서드 7종(`updateAllowedTimeWindow`, `updateDailyLimitMinutes`, `updateAlertIntervalMinutes`, `setImmediateLock`, `grantBonusMinutes`, `updateParentPin`, `updateAppAccessMode`) — 전부 Local과 동일한 copy 논리 후 `doc.set(mapOf(POLICY to ModelJson.policyToJson(newPolicy).toString()))` (정책 전체 교체 — 단일 작성자 원칙) + 옵티머스틱 로컬 반영
  - `updateDeviceHealth` → DEVICE_HEALTH 필드
  - `addOrUpdateSafeZone`/`removeSafeZone` → SAFE_ZONES 배열 필드
  - `recordWebContentLog`/`recordStayPointSession`/`emitAlert` → `collection("$FAMILIES/$familyCode/$COLLECTION").add(mapOf(DATA to json, TIMESTAMP_MILLIS to ms))` + 옵티머스틱 로컬
  - `acknowledgeAlert` → 해당 문서 `update(mapOf("isAcknowledged" to true))` + 로컬 반영
  - `recordUsageProgress` → Local의 마일스톤 로직 동일 적용 + `updateDeviceHealth(DEVICE_HEALTH)` 쓰기
- 페어링:
  - `createFamilyCode()`: 6자리 생성 → `doc.set(mapOf(POLICY to seedPolicyJson, SAFE_ZONES to seedZonesJson, PAIRED to emptyMap, "createdAtMillis" to now))` 성공 후 `pairingStore.save(code)` → `lastCreatedCode` 설정 → WAITING → 코드 반환
  - `linkFamilyCode(code)`: 6자리 숫자 검증 → `doc.get().await()` — 문서 존재 시 `doc.set(mapOf(PAIRED to mapOf(CHILD_LINKED to true, "atMillis" to now)), SetOptions.merge())` → LINKED → true; 검증 실패·문서 부재 → false, 상태 불변 (Review Focus 1)
  - 생성자: `pairingStore.savedCode() != null` → WAITING (생성된 코드 있으나 childLinked 미확인), 아니면 NOT_PAIRED

- [ ] **Step 7: 컴파일 확인**

Run: `./gradlew :core-data:assembleDebug` — Expected: PASS

- [ ] **Step 8: 커밋**

```bash
git add core-data/src/main/kotlin/com/familylink/data/ core-data/src/test/kotlin/com/familylink/data/SyncListMergerTest.kt
git commit -m "feat(core-data): Firebase 리파지터리 실시간 구현체 + 페어링 저장소"
```

---

### Task 5: 업스트림 기록 정비 — 웹로그 UUID + 부모 알림 라우팅

**Files:**
- Modify: `core-model/src/main/kotlin/com/familylink/model/BrowserContentFilter.kt` — 라인 49 `id = "yt-${nowMillis}"`, 라인 91 `id = "web-${nowMillis}"` → `id = UUID.randomUUID().toString()` (`java.util.UUID` import 추가)
- Modify: `app-parent/src/main/kotlin/com/familylink/parent/ui/ParentMainActivity.kt` — alerts → 시스템 알림 자동 라우팅
- Test: `core-model/src/test/kotlin/com/familylink/model/StayPointAndTrackersTest.kt` — extractContent 계열 테스트에 id 형식 단언이 있으면 제거(id는 UUID 형식이라 형식 단언 금지)

**Interfaces:**
- Consumes: Task 4 `FirebaseFamilySyncRepository`의 `emitAlert`/`recordWebContentLog` (자녀→Firestore 업스트림은 이미 동작), 기존 `ParentNotificationHelper.showNotification(context, alert)`
- Produces: 부모 앱 리스너 경로 — 새 alert 도착(masked: alerts StateFlow 새 alertId) → `ParentNotificationHelper.showNotification` 자동 호출; POST_NOTIFICATIONS 런타임 요청 1회

- [ ] **Step 1: UUID 정정 + 잔류 확인**

```bash
grep -rn 'id = "web-\|id = "yt-' core-model core-data   # 결과 없어야 함
./gradlew :core-model:test :core-data:testDebugUnitTest  # 전부 PASS
```

- [ ] **Step 2: 부모 Activity 알림 라우팅 구현**

- `onCreate`: `registerForActivityResult(ActivityResultContracts.RequestPermission())` 런처 등록 — Android 13(TIRAMISU) 이상이고 권한 미허용 시 진입 1회 요청 (minSdk 26이라 SDK 분기 필수)
- Compose 트리: `val alerts by controller.alerts.collectAsState()` + `LaunchedEffect(alerts)` — 마지막으로 처리한 최신 alertId를 추적, 새 alertId 도착마다 `onPostSystemNotification(alert)` 호출 (기존 콜백 재사용 — 새 파라미터 없음)

- [ ] **Step 3: 전체 빌드·테스트**

Run: `./gradlew testDebugUnitTest assembleDebug` — Expected: PASS

- [ ] **Step 4: 커밋**

```bash
git add core-model/ app-parent/src/main/kotlin/com/familylink/parent/ui/ParentMainActivity.kt
git commit -m "feat(app-parent): alerts 실시간 알림 라우팅 + 웹로그 UUID 정정"
```

---

### Task 6: FamilyRepositoryProvider 선택 로직 + 페어링 UI

**Files:**
- Modify: `core-data/src/main/kotlin/com/familylink/data/FamilySyncRepository.kt` — `FamilyRepositoryProvider` 객체 (기존 라인 297–308)
- Modify: `app-parent/src/main/kotlin/com/familylink/parent/ui/ParentDashboardController.kt` — 페어링 노출 추가
- Create: `app-parent/src/main/kotlin/com/familylink/parent/ui/PairingBanner.kt`
- Create: `app-child/src/main/kotlin/com/familylink/child/ui/FamilyPairingScreen.kt`
- Modify: `app-child/src/main/kotlin/com/familylink/child/ui/ChildMainActivity.kt`, `app-parent/src/main/kotlin/com/familylink/parent/ui/ParentMainActivity.kt`
- Test: `core-data/src/test/kotlin/com/familylink/data/RepositorySelectionTest.kt`

**Interfaces:**
- Consumes: Task 2 `PairingState`, Task 4 `FirebaseFamilySyncRepository`/`FamilyPairingStore`/`lastCreatedCode`
- Produces:
  - `enum class RepositoryKind { FIREBASE, LOCAL }` (core-data, `FamilyRepositoryProvider`와 같은 파일)
  - `FamilyRepositoryProvider.selectRepositoryKind(firebaseInitialized: Boolean): RepositoryKind` — 순수 함수 (JVM 테스트 대상)
  - `@Composable fun PairingBanner(state: PairingState, onCreateCode: () -> Unit, lastCreatedCode: String?)` — 부모, TopAppBar 우 하단 배너; LINKED면 랜더링 안 함
  - `@Composable fun FamilyPairingScreen(onConfirm: (String) -> Boolean)` — 자녀 fullscreen, LINKED 전까지 표시

- [ ] **Step 1: 선택 로직 실패 테스트**

```kotlin
@Test fun `selectRepositoryKind falls back to Local when Firebase unavailable`() {
    assertThat(FamilyRepositoryProvider.selectRepositoryKind(firebaseInitialized = false)).isEqualTo(RepositoryKind.LOCAL)
}
@Test fun `selectRepositoryKind picks Firebase whenever initialized regardless of pairing`() {
    assertThat(FamilyRepositoryProvider.selectRepositoryKind(firebaseInitialized = true)).isEqualTo(RepositoryKind.FIREBASE)
}
```

- [ ] **Step 2: 실패 확인** — Run: `./gradlew :core-data:testDebugUnitTest` — Expected: FAIL

- [ ] **Step 3: 구현**

- `FamilyRepositoryProvider`: firebase 판별은 `com.google.firebase.FirebaseApp.getApps(context).isNotEmpty()`; true → `FirebaseFamilySyncRepository(FirebaseFirestore.getInstance(), FamilyPairingStore(context))`, false → 기존 `LocalFamilySyncRepository`. 인스턴스 캐시·동기화 블록 기존 패턴 유지. **페어링 유무는 선택 기준이 아니다** — Firebase 초기화만 되어 있으면 Firebase repo가 NOT_PAIRED부터 이어받는다 (Review Focus 5)
- `ParentDashboardController`: repository의 `pairingState`, `lastCreatedCode`를 그대로 노출(`val pairingState: StateFlow<PairingState> = repository.pairingState` 등) + `suspend fun createFamilyCode(): String = repository.createFamilyCode()`
- `ParentMainActivity`: `val pairingState by controller.pairingState.collectAsState()`, `val lastCreatedCode by controller.lastCreatedCode.collectAsState()` — 기존 `ChildDeviceStatusBanner` 아래 `if (pairingState != PairingState.LINKED) PairingBanner(...)`; `onCreateCode`는 `scope.launch { controller.createFamilyCode() }`
- `ChildMainActivity`: `pairingState != LINKED` → `FamilyPairingScreen` (Local 데모 경로도 동일 흐름 — Task 2 데모 규칙상 임의 6자리 입력 → LINKED → 대시보드); `onConfirm`은 coroutine 스코프에서 `repo.linkFamilyCode(code)` 호출 후 결과 반환
- Firebase 경로의 LINKED 전환은 PAIRED 리스너 도착으로 자동 — 배너 소멸·자녀 대시보드 복귀

- [ ] **Step 4: 통과 + 전체 빌드**

Run: `./gradlew testDebugUnitTest assembleDebug` — Expected: PASS
수동: json 없는 상태에서 APK 2개 실행 — 데모 폴백 동작 확인 (Review Focus 5)

- [ ] **Step 5: 커밋**

```bash
git add core-data/ app-parent/ app-child/
git commit -m "feat(app): 리파지터리 Firebase/Local 선택 + 페어링 UI (부모 배너·자녀 화면)"
```

---

### Task 7: 웹 대시보드 (GitHub Pages, 열람 전용)

**Files:**
- Create: `web-dashboard/index.html`
- Create: `web-dashboard/app.js`
- Create: `web-dashboard/manifest.json`
- Create: `web-dashboard/README.md` (배포 가이드 — 사용자 실행 절차)

**Interfaces:**
- Consumes: Task 3 `FirestoreSchema`와 동일한 컬렉션·필드 문자열, Task 1의 JSON 필드 규칙 (문서의 `data` 필드 = JSON 문자열 → `JSON.parse` 후 렌더)
- Produces: 정적 사이트 — 첫 접속에 6자리 코드 입력(`localStorage("familylink_family_code")`) → 6종(`policy, deviceHealth, safeZones(list doc), alerts, webLogs, staySessions`) onSnapshot 실시간 뷰. **제어 버튼 없음 — 열람 전용**(spec 확정)

- [ ] **Step 1: 4파일 작성**

- `app.js`: Firebase v10+ 모듈 CDN(ESM import) 사용; 경로 `collection(db, "families", code, "<컬렉션>")`·단일 문서 `doc(db, "families", code)`; 각 모델 JSON의 `placeName`이 null/빈문자열이면 **"위치 미확인"** 표시 (Review Focus 4); Firebase config는 `app.js` 상단 `FIREBASE_CONFIG` 상수 — README에서 사용자가 콘솔 값을 붙여넣는 구조(다른 기본값 없음)
- `index.html`: 4 섹션 — 요약 헤더(오늘 사용시간·잠금 배지·배터리/소리/네트워크), 위치 타임라인, 웹·유튜브 기록(검색 입력 1개), 알림 히스토리
- `manifest.json`: 아이폰 홈 화면 추가용 최소 PWA 메타
- `README.md`: 8단계 배포 가이드 — ① Firebase 콘솔 → Web 앱 등록 → config 복사 ② `app.js` FIREBASE_CONFIG 붙여넣기 ③ 별도 공개 GitHub 리포(예: `familylink-dashboard`) 생성 ④ `web-dashboard/` 내용 push ⑤ Settings→Pages→Branch main ⑥ 발급 URL 확인 ⑦ 아이폰 Safari 접속 → 코드 입력 → 홈 화면 추가 ⑧ 부모·자녀 앱과 동시 확인

- [ ] **Step 2: 로컬 렌더 확인**

Run: `cd web-dashboard && python3 -m http.server 8080` → 브라우저에서 코드 입력 화면·4 섹션 렌더 확인 (실데이터 연결은 Task 8 체크리스트에서)

- [ ] **Step 3: 커밋**

```bash
git add web-dashboard/
git commit -m "feat(web): GitHub Pages 열람 대시보드 (Firestore 실시간 구독)"
```

---

### Task 8: 통합 검증 체크리스트 + 최종 확인

**Files:**
- Create: `docs/verification/2026-10-18-firebase-integration-checklist.md`

**Interfaces:** Consumes: 없음. Produces: 사용자 실행 절차 문서.

- [ ] **Step 1: 전체 자동화 확인**

Run: `./gradlew testDebugUnitTest assembleDebug` — Expected: PASS (spec 성공 기준 5)

- [ ] **Step 2: 체크리스트 문서 작성** — 각 항목: 실행 절차 + 예상 결과:

1. 부모 APK 부모 폰 설치 → "가족 연결 시작" → 6자리 코드 표시
2. 자녀 APK 자녀 폰 설치 → 코드 입력 → 연결 후 대시보드 전환 (spec 기준 1 전제)
3. 부모 "즉시 잠금" ON → 3초 내 자녀 폰 잠금 화면 (spec 기준 1)
4. 자녀 설정 변경 시도 → 부모 폰 시스템 알림 도착 (spec 기준 2)
5. 자녀 크롬 웹페이지 방문 → 부모 타임라인 + 웹 대시보드에 표시 (spec 기준 3)
6. 웹 대시보드에서 placeName 없는 기록 "위치 미확인" 표시 (Review Focus 4)
7. 아이폰 Safari → 페이지 접속 → 코드 입력 → 현황 실시간 갱신 (spec 기준 4)
8. 자녀 폰 Wi-Fi 끊기 → 복구 시 오프라인 큐잉 기록 전송 확인
9. 구독 실패 시나리오: Firestore 규칙·네트워크 오류 발생 시에도 앱 크래시 없음 확인

- [ ] **Step 3: 커밋**

```bash
git add docs/verification/
git commit -m "docs(verification): Firebase 통합 실기기 체크리스트"
```

---

## 매핑 참고

- 스펙 P1~P6 → 본 계획 8 Task: P1→Task 1·3, P2→Task 2·4·6, P3→Task 4, P4→Task 4·5, P5→Task 7, P6→Task 8
- 스펙 범위 밖(명시 유지): FCM 백그라운드 푸시, Firestore 규칙 정밀 강화/App Check, 코드 갱신·만료, 다자녀, 웹 제어
- 알림: 스펙 §6 기준 `staySessions`은 위치 추적(로드맵 2번)이 미구현 상태라 자녀 폰에서 실시간 생성되지 않는다 — 실험 중 staySessions이 데모 시드면 정상이며, 로드맵 2번 구현 후 실데이터가 채워진다
