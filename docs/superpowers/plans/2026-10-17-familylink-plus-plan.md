# FamilyLink Plus Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a multi-module Android parental control suite (`:app-parent` and `:app-child`) that tracks browser URLs & YouTube titles, correlates stay-point locations with app usage on a map (e.g., "30 mins of YouTube at convenience store in front of school"), enforces allowed usage hours (10:00–21:00) and app whitelists/limits, notifies the parent every N minutes (default 30m), and prevents tampering/uninstallation.

**Architecture:** A 4-module Kotlin/Jetpack Compose project (`:core-model`, `:core-data`, `:app-child`, `:app-parent`). `:core-model` houses pure, deterministic domain engines (`PolicyEvaluator`, `StayPointClusterer`, `UsageMilestoneTracker`, `BrowserContentFilter`, `TamperGuardDetector`, `SafeZoneEvaluator`) verified with unit tests. `:core-data` provides Kakao Local reverse-geocoding, local persistence, and real-time Parent-Child sync. `:app-child` runs the Android `AccessibilityService`, `UsageStatsManager` poller, `DeviceAdminReceiver`, and lock overlay, while `:app-parent` provides the interactive Location+Usage Map, Web/YouTube Timeline, Policy Controller, and 30-Minute Milestone Notification Center.

**Tech Stack:** Kotlin 2.2.10, Android Gradle Plugin 9.2.1 (Gradle 9.5.1, `compileSdk = 35`, `minSdk = 26`), Jetpack Compose (BOM 2025.01.00, Material 3), Coroutines/Flow, OkHttp/Moshi, JUnit 4 + Google Truth + Coroutines Test.

**Spec:** `docs/superpowers/specs/2026-10-17-familylink-plus-design.md`

## Global Constraints

- Minimum SDK: 26 (Android 8.0), Target/Compile SDK: 35, Java/Kotlin JVM Target: 17.
- Default allowed usage time window: `10:00` (오전 10시) to `21:00` (오후 9시).
- Default parent usage notification interval: `30` minutes (configurable from 5 to 180 minutes).
- Stay-point clustering thresholds: `50.0` meters radius and minimum `3` minutes (`180_000` ms) dwell time.
- Separate APK outputs: `:app-parent` (`com.familylink.parent`) and `:app-child` (`com.familylink.child`).

## Review Focus

- **Overnight / boundary time windows**: When `allowedStartHour/Minute` is compared against current local time, exact boundary minutes (`10:00` and `21:00`) and overnight windows (e.g., `22:00` to `06:00`) must evaluate deterministically without off-by-one minute errors.
- **In-progress typing in browser address bar**: When the user is typing in Chrome's `url_bar` (`isFocused == true`), partial characters must never be logged as visited web pages.
- **Duplicate 30-minute milestone alerts**: Polling cumulative usage multiple times within the 30th minute (e.g., at 30m 05s, 30m 20s, 30m 45s) must emit exactly one alert for the 30-minute milestone, and resetting or changing `alertIntervalMinutes` must recalculate the milestone index cleanly.
- **GPS jitter during a stay session**: A single noisy GPS reading or brief step within 50m of the cluster centroid must not fragment a 30-minute convenience store stay into dozens of 1-minute sessions.
- **Self-lockout on emergency / parent PIN entry**: `ALWAYS_ALLOWED` packages (dialer, emergency call, SMS) and the child app's own PIN unlock overlay must never be blocked by `PolicyEvaluator`.

---

### Task 1: Multi-Module Gradle Scaffolding & Policy Evaluation Engine (`:core-model`)

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `core-model/build.gradle.kts`
- Create: `core-model/src/main/kotlin/com/familylink/model/DomainModels.kt`
- Create: `core-model/src/main/kotlin/com/familylink/model/PolicyEvaluator.kt`
- Test: `core-model/src/test/kotlin/com/familylink/model/PolicyEvaluatorTest.kt`

**Interfaces:**
- Produces:
  - `ControlPolicy`, `AppRule`, `AppAccessMode`, `LockReason`, `LockDecision` in `com.familylink.model`
  - `PolicyEvaluator.evaluate(policy: ControlPolicy, currentPackage: String, currentHour: Int, currentMinute: Int, totalUsedMinutesToday: Int, appUsedMinutesToday: Map<String, Int>): LockDecision`

- [ ] **Step 1: Bootstrap Gradle wrapper and multi-module configuration**
  Copy cached Gradle 9.5.1 wrapper files (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) from `/Users/anjaemo/Documents/Code/my-stock-dashboard-app`, create `local.properties` pointing `sdk.dir=/Users/anjaemo/Library/Android/sdk`, and configure `settings.gradle.kts` with `:core-model`, `:core-data`, `:app-child`, `:app-parent`.

- [ ] **Step 2: Write the failing test `PolicyEvaluatorTest.kt`**
```kotlin
package com.familylink.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PolicyEvaluatorTest {
    private val defaultPolicy = ControlPolicy(
        childId = "child-1",
        allowedStartHour = 10,
        allowedStartMinute = 0,
        allowedEndHour = 21,
        allowedEndMinute = 0,
        dailyLimitMinutes = 120,
        bonusMinutesToday = 0,
        isImmediateLocked = false,
        alertIntervalMinutes = 30,
        appRules = mapOf(
            "com.android.dialer" to AppRule("com.android.dialer", "전화", AppAccessMode.ALWAYS_ALLOWED),
            "com.google.android.youtube" to AppRule("com.google.android.youtube", "YouTube", AppAccessMode.ALLOWED, dailyQuotaMinutes = 40),
            "com.supercell.brawlstars" to AppRule("com.supercell.brawlstars", "브롤스타즈", AppAccessMode.BLOCKED)
        )
    )

    @Test
    fun `blocks usage before 10 AM and at or after 9 PM for normal apps`() {
        val before10 = PolicyEvaluator.evaluate(defaultPolicy, "com.google.android.youtube", 9, 59, 10, emptyMap())
        assertThat(before10.isBlocked).isTrue()
        assertThat(before10.reason).isEqualTo(LockReason.OUTSIDE_ALLOWED_HOURS)

        val at10 = PolicyEvaluator.evaluate(defaultPolicy, "com.google.android.youtube", 10, 0, 10, emptyMap())
        assertThat(at10.isBlocked).isFalse()

        val at21 = PolicyEvaluator.evaluate(defaultPolicy, "com.google.android.youtube", 21, 0, 10, emptyMap())
        assertThat(at21.isBlocked).isTrue()
        assertThat(at21.reason).isEqualTo(LockReason.OUTSIDE_ALLOWED_HOURS)
    }

    @Test
    fun `always allows dialer even during instant lock or outside allowed hours`() {
        val lockedPolicy = defaultPolicy.copy(isImmediateLocked = true)
        val decision = PolicyEvaluator.evaluate(lockedPolicy, "com.android.dialer", 23, 30, 300, emptyMap())
        assertThat(decision.isBlocked).isFalse()
    }

    @Test
    fun `enforces daily total limit and respects bonus minutes`() {
        val exhausted = PolicyEvaluator.evaluate(defaultPolicy, "com.google.android.youtube", 15, 0, 120, emptyMap())
        assertThat(exhausted.isBlocked).isTrue()
        assertThat(exhausted.reason).isEqualTo(LockReason.DAILY_QUOTA_EXCEEDED)

        val withBonus = PolicyEvaluator.evaluate(defaultPolicy.copy(bonusMinutesToday = 30), "com.google.android.youtube", 15, 0, 140, emptyMap())
        assertThat(withBonus.isBlocked).isFalse()
    }

    @Test
    fun `enforces blocked app and per-app daily quota`() {
        val blockedGame = PolicyEvaluator.evaluate(defaultPolicy, "com.supercell.brawlstars", 14, 0, 20, emptyMap())
        assertThat(blockedGame.isBlocked).isTrue()
        assertThat(blockedGame.reason).isEqualTo(LockReason.APP_BLOCKED)

        val youtubeOverQuota = PolicyEvaluator.evaluate(
            defaultPolicy,
            "com.google.android.youtube",
            14,
            0,
            50,
            mapOf("com.google.android.youtube" to 40)
        )
        assertThat(youtubeOverQuota.isBlocked).isTrue()
        assertThat(youtubeOverQuota.reason).isEqualTo(LockReason.APP_QUOTA_EXCEEDED)
    }
}
```

- [ ] **Step 3: Implement `DomainModels.kt` and `PolicyEvaluator.kt` in `:core-model`**
  Implement all domain data classes and `PolicyEvaluator.evaluate(...)` handling `ALWAYS_ALLOWED` packages, `PARENT_INSTANT_LOCK`, `OUTSIDE_ALLOWED_HOURS` (supporting both standard daytime `[start, end)` and overnight windows), `DAILY_QUOTA_EXCEEDED`, `APP_BLOCKED`, and `APP_QUOTA_EXCEEDED`.

- [ ] **Step 4: Run `:core-model:test` to verify tests pass**
  Run: `./gradlew :core-model:test`
  Expected: `BUILD SUCCESSFUL` with all `PolicyEvaluatorTest` cases passing.

- [ ] **Step 5: Commit Task 1**
```bash
git add .
git commit -m "feat(core-model): add multi-module setup, domain models, and PolicyEvaluator"
```

---

### Task 2: Stay-Point Location+Usage Merger, 30-Min Milestone Tracker & Browser/Tamper Parsers (`:core-model`)

**Files:**
- Create: `core-model/src/main/kotlin/com/familylink/model/StayPointClusterer.kt`
- Create: `core-model/src/main/kotlin/com/familylink/model/UsageMilestoneTracker.kt`
- Create: `core-model/src/main/kotlin/com/familylink/model/BrowserContentFilter.kt`
- Create: `core-model/src/main/kotlin/com/familylink/model/TamperGuardDetector.kt`
- Create: `core-model/src/main/kotlin/com/familylink/model/SafeZoneEvaluator.kt`
- Test: `core-model/src/test/kotlin/com/familylink/model/StayPointAndTrackersTest.kt`

**Interfaces:**
- Consumes: `StayPointSession`, `AppUsageSegment`, `WebContentLog`, `ParentAlert`, `SafeZone` from `DomainModels.kt`
- Produces:
  - `StayPointClusterer.buildSessions(childId: String, dateKey: String, locations: List<LocationPoint>, appSlices: List<AppUsageSlice>, webLogs: List<WebContentLog>, radiusMeters: Double = 50.0, minStayMillis: Long = 180_000L): List<StayPointSession>`
  - `UsageMilestoneTracker.checkMilestone(childId: String, totalUsedMinutesToday: Int, alertIntervalMinutes: Int, lastNotifiedMilestoneStep: Int, currentPlaceName: String?, recentAppUsages: List<AppUsageSegment>, nowMillis: Long): MilestoneCheckResult`
  - `BrowserContentFilter.extractContent(packageName: String, urlCandidate: String?, titleCandidate: String?, isUrlBarFocused: Boolean, nowMillis: Long, lastLog: WebContentLog?): WebContentLog?`
  - `TamperGuardDetector.inspectWindow(packageName: String, className: String?, visibleTexts: List<String>, protectedAppName: String): TamperCheckResult`
  - `SafeZoneEvaluator.evaluateTransitions(childId: String, previousLat: Double?, previousLng: Double?, currentLat: Double, currentLng: Double, zones: List<SafeZone>, nowMillis: Long): List<ParentAlert>`

- [ ] **Step 1: Write the failing test `StayPointAndTrackersTest.kt`**
  Write unit tests verifying:
  1. `StayPointClusterer` clusters 35 minutes of coordinates at `"학교앞 GS25 편의점"` (within 50m) and attaches 30 minutes of YouTube usage and 5 minutes of Chrome usage + visited URLs into a single `StayPointSession` with formatted summary `"학교앞 GS25 편의점 · YouTube 30분, Chrome 5분"`.
  2. `UsageMilestoneTracker` triggers at 30m, 60m, 90m (and when reconfigured to 15m, triggers at 15m, 30m, 45m) and does NOT emit duplicate alerts when polled repeatedly at 30m.
  3. `BrowserContentFilter` ignores focused typing (`isUrlBarFocused = true`), extracts clean URLs/titles from Chrome (`com.android.chrome`), Samsung Internet (`com.sec.android.app.sbrowser`), Whale (`com.nhn.android.search`), and video/Shorts titles from YouTube (`com.google.android.youtube`), and debounces identical URLs within 3 seconds.
  4. `TamperGuardDetector` flags Android Settings screens containing `"접근성"`, `"기기 관리자"`, `"강제 종료"`, `"제거"`, or `"날짜 및 시간"`.
  5. `SafeZoneEvaluator` emits `SAFE_ZONE_ENTER` and `SAFE_ZONE_EXIT` alerts when crossing a geofence radius.

- [ ] **Step 2: Implement the 5 domain engines in `:core-model`**
  Implement Haversine distance calculation (`StayPointClusterer.distanceMeters`), stay-point clustering with app/web log attribution, milestone step tracking, browser/YouTube content extraction, settings tamper detection, and geofence transition detection.

- [ ] **Step 3: Run `:core-model:test` to verify all tests pass**
  Run: `./gradlew :core-model:test`
  Expected: `BUILD SUCCESSFUL` with all unit tests passing.

- [ ] **Step 4: Commit Task 2**
```bash
git add core-model/
git commit -m "feat(core-model): add StayPointClusterer, UsageMilestoneTracker, BrowserContentFilter, TamperGuard, and SafeZoneEvaluator"
```

---

### Task 3: Shared Data Layer, Kakao Local Reverse Geocoding & Family Sync Repository (`:core-data`)

**Files:**
- Create: `core-data/build.gradle.kts`
- Create: `core-data/src/main/AndroidManifest.xml`
- Create: `core-data/src/main/kotlin/com/familylink/data/PlaceNameResolver.kt`
- Create: `core-data/src/main/kotlin/com/familylink/data/FamilySyncRepository.kt`
- Create: `core-data/src/main/kotlin/com/familylink/data/DemoScenarioSeed.kt`
- Test: `core-data/src/test/kotlin/com/familylink/data/FamilySyncRepositoryTest.kt`

**Interfaces:**
- Consumes: All `:core-model` types and engines
- Produces:
  - `PlaceNameResolver.resolveKoreanPlaceName(latitude: Double, longitude: Double): ResolvedPlace`
  - `FamilySyncRepository` (reactive `StateFlow`s for `policy`, `deviceHealth`, `staySessions`, `webLogs`, `alerts`, `safeZones`, `installedApps`, plus mutation methods for Parent controls and Child telemetry ingestion)
  - `FamilyRepositoryProvider.getInstance(context: Context): FamilySyncRepository`

- [ ] **Step 1: Write the failing test `FamilySyncRepositoryTest.kt`**
  Write unit tests verifying:
  1. `PlaceNameResolver` parses Kakao Local JSON responses (e.g., nearby school + convenience store -> `"역삼초 앞 GS25 편의점"`) and falls back gracefully when offline or without an API key.
  2. `InMemoryAndFileFamilySyncRepository` persists policy updates (e.g., changing allowed hours to `10:00~21:00`, changing alert interval from `30` to `20` mins, toggling instant lock, granting `+30분` bonus time, blocking an app), records new browser/YouTube logs, updates stay-point map sessions, and emits 30-minute milestone alerts.

- [ ] **Step 2: Implement `PlaceNameResolver`, `DemoScenarioSeed`, and `FamilySyncRepository` in `:core-data`**
  Implement Kakao Local API reverse geocoder (`https://dapi.kakao.com/v2/local/search/category.json` & `coord2address.json`), cross-app JSON state synchronization (plus Firestore hook), and realistic initial Korean scenario data (including *"학교앞 GS25 편의점에서 유튜브 30분 사용"*, *"대치동 수학학원에서 EBSi/크롬 25분 사용"*, Chrome web history, YouTube watched titles, and Safe Zones).

- [ ] **Step 3: Run `:core-data:test` to verify tests pass**
  Run: `./gradlew :core-data:test`
  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit Task 3**
```bash
git add core-data/
git commit -m "feat(core-data): add Kakao Local PlaceNameResolver and FamilySyncRepository"
```

---

### Task 4: Child Android App (`:app-child`) — Accessibility Service, Usage/Location Daemon, Lock Overlay & TamperGuard

**Files:**
- Create: `app-child/build.gradle.kts`
- Create: `app-child/src/main/AndroidManifest.xml`
- Create: `app-child/src/main/res/xml/accessibility_service_config.xml`
- Create: `app-child/src/main/res/xml/device_admin_policies.xml`
- Create: `app-child/src/main/res/values/strings.xml`
- Create: `app-child/src/main/kotlin/com/familylink/child/service/ChildAccessibilityService.kt`
- Create: `app-child/src/main/kotlin/com/familylink/child/service/ChildMonitoringForegroundService.kt`
- Create: `app-child/src/main/kotlin/com/familylink/child/service/ChildDeviceAdminReceiver.kt`
- Create: `app-child/src/main/kotlin/com/familylink/child/ui/ChildMainActivity.kt`
- Create: `app-child/src/main/kotlin/com/familylink/child/ui/ChildDashboardScreen.kt`
- Create: `app-child/src/main/kotlin/com/familylink/child/ui/LockOverlayScreen.kt`
- Create: `scripts/setup-child-device.sh`
- Test: `app-child/src/test/kotlin/com/familylink/child/ChildMonitorControllerTest.kt`

**Interfaces:**
- Consumes: `:core-model` and `:core-data`
- Produces:
  - Installable `:app-child` APK (`com.familylink.child`)
  - `ChildAccessibilityService` (extracts Chrome/Samsung Internet/Whale URLs & YouTube titles, enforces app/time lock, blocks Settings tampering)
  - `ChildMonitoringForegroundService` (tracks `UsageStatsManager`, location stay-points, 30-min alerts, battery/ringer/network health)
  - `scripts/setup-child-device.sh` (ADB one-click permission & Device Owner provisioning script)

- [ ] **Step 1: Write the failing test `ChildMonitorControllerTest.kt`**
  Write unit tests verifying that the child controller:
  1. Immediately transitions to locked state when a blocked app (`com.supercell.brawlstars`) is opened or current time is outside `10:00~21:00`.
  2. Unlocks when the valid parent PIN (`0000`) is submitted or when the parent grants `+30` bonus minutes.
  3. Emits a `TAMPER_ATTEMPT` alert to the parent and locks the screen when the child enters Android Settings -> Accessibility or Device Admin.

- [ ] **Step 2: Implement `:app-child` services, receivers, Compose UI, and ADB setup script**
  Implement the accessibility node walker for Chrome/Samsung Internet/Whale/YouTube, the foreground usage & location service, the Device Admin receiver, the Compose dashboard + PIN-protected lock screen, and `scripts/setup-child-device.sh`.

- [ ] **Step 3: Run `:app-child:testDebugUnitTest` and `:app-child:assembleDebug`**
  Run: `./gradlew :app-child:testDebugUnitTest :app-child:assembleDebug`
  Expected: `BUILD SUCCESSFUL` and `app-child/build/outputs/apk/debug/app-child-debug.apk` generated.

- [ ] **Step 4: Commit Task 4**
```bash
git add app-child/ scripts/
git commit -m "feat(app-child): implement Accessibility URL/YouTube tracker, policy lock overlay, and TamperGuard"
```

---

### Task 5: Parent Android App (`:app-parent`) — Location+Usage Map, Web/YouTube History, Policy Controls & 30-Min Alerts

**Files:**
- Create: `app-parent/build.gradle.kts`
- Create: `app-parent/src/main/AndroidManifest.xml`
- Create: `app-parent/src/main/res/values/strings.xml`
- Create: `app-parent/src/main/kotlin/com/familylink/parent/notification/ParentNotificationHelper.kt`
- Create: `app-parent/src/main/kotlin/com/familylink/parent/ui/ParentMainActivity.kt`
- Create: `app-parent/src/main/kotlin/com/familylink/parent/ui/ParentViewModel.kt`
- Create: `app-parent/src/main/kotlin/com/familylink/parent/ui/tabs/LocationUsageMapTab.kt`
- Create: `app-parent/src/main/kotlin/com/familylink/parent/ui/tabs/WebHistoryTimelineTab.kt`
- Create: `app-parent/src/main/kotlin/com/familylink/parent/ui/tabs/PolicyControlTab.kt`
- Create: `app-parent/src/main/kotlin/com/familylink/parent/ui/tabs/AlertsAndSafeZonesTab.kt`
- Create: `README.md`
- Test: `app-parent/src/test/kotlin/com/familylink/parent/ParentViewModelTest.kt`

**Interfaces:**
- Consumes: `:core-model` and `:core-data`
- Produces:
  - Installable `:app-parent` APK (`com.familylink.parent`)
  - 4-tab Parent Control Dashboard with real-time Child Device Health banner, Interactive Stay-Point + App Usage Map (`"학교앞 편의점에서 유튜브 30분 사용"`), Browser & YouTube History Timeline, Allowed Hours (`10:00~21:00`) & App Whitelist/Quota Editor, Configurable 30-Min Alert Interval & Notification Dispatcher.

- [ ] **Step 1: Write the failing test `ParentViewModelTest.kt`**
  Write unit tests verifying:
  1. Selecting a stay-point marker on the map exposes the exact correlated app usage durations (e.g., `"학교앞 GS25 편의점"` -> `"YouTube 30분, Chrome 10분"`) and the web pages/videos viewed at that location.
  2. Updating the allowed time window (`10:00 ~ 21:00`), changing the periodic notification interval (e.g., `30분` -> `15분`), toggling an app between `ALWAYS_ALLOWED` / `ALLOWED` / `BLOCKED`, or tapping `지금 즉시 잠금` / `+30분 보너스` immediately updates `ControlPolicy`.
  3. Simulating or receiving 30 minutes of child usage emits a `USAGE_MILESTONE` alert with place + app context.

- [ ] **Step 2: Implement `:app-parent` UI tabs, ViewModel, Notification Helper, and `README.md`**
  Implement all 4 tabs in Jetpack Compose (Material 3) with rich visual map markers, route polylines, safe-zone circles, bottom-sheet drill-down for stay-point app/web logs, browser/YouTube search & filter, time window & app rules editor, and Android system notification posting for 30-minute milestones.

- [ ] **Step 3: Run full test suite and assemble both APKs**
  Run: `./gradlew testDebugUnitTest assembleDebug`
  Expected: `BUILD SUCCESSFUL` across `:core-model`, `:core-data`, `:app-child`, `:app-parent`, producing both `app-parent-debug.apk` and `app-child-debug.apk`.

- [ ] **Step 4: Commit Task 5**
```bash
git add app-parent/ README.md
git commit -m "feat(app-parent): implement Location+Usage Map, Web/YouTube Timeline, Policy Controls, and 30m Alerts"
```
