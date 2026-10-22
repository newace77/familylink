package com.familylink.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StayPointAndTrackersTest {

    @Test
    fun `StayPointClusterer merges convenience store stay with 30m YouTube and 5m Chrome usage`() {
        val baseTime = 1_700_000_000_000L
        val locations = listOf(
            LocationPoint(37.49940, 127.03340, baseTime, "학교앞 GS25 편의점", "서울 강남구 역삼동 123"),
            LocationPoint(37.49942, 127.03341, baseTime + 10 * 60_000L, "학교앞 GS25 편의점", "서울 강남구 역삼동 123"),
            LocationPoint(37.49939, 127.03338, baseTime + 35 * 60_000L, "학교앞 GS25 편의점", "서울 강남구 역삼동 123")
        )
        val appSlices = listOf(
            AppUsageSlice(
                packageName = "com.google.android.youtube",
                appName = "YouTube",
                startMillis = baseTime + 1 * 60_000L,
                endMillis = baseTime + 31 * 60_000L
            ),
            AppUsageSlice(
                packageName = "com.android.chrome",
                appName = "Chrome",
                startMillis = baseTime + 31 * 60_000L,
                endMillis = baseTime + 36 * 60_000L
            )
        )
        val webLogs = listOf(
            WebContentLog(
                id = "w1",
                timestampMillis = baseTime + 32 * 60_000L,
                sourcePackage = "com.android.chrome",
                browserOrAppName = "Chrome",
                contentType = ContentType.WEB_PAGE,
                title = "나무위키 - 마인크래프트 공략",
                urlOrQuery = "https://namu.wiki/w/마인크래프트"
            )
        )

        val sessions = StayPointClusterer.buildSessions(
            childId = "child-1",
            dateKey = "2026-10-17",
            locations = locations,
            appSlices = appSlices,
            webLogs = webLogs
        )

        assertThat(sessions).hasSize(1)
        val session = sessions.first()
        assertThat(session.placeName).isEqualTo("학교앞 GS25 편의점")
        assertThat(session.dwellMinutes).isEqualTo(35)
        assertThat(session.headlineBadge()).isEqualTo("학교앞 GS25 편의점 · YouTube 30분, Chrome 5분")
        assertThat(session.webLogs).hasSize(1)
        assertThat(session.webLogs.first().title).contains("마인크래프트")
    }

    @Test
    fun `UsageMilestoneTracker triggers every 30 minutes without duplicates and supports custom intervals`() {
        val recentApps = listOf(
            AppUsageSegment("com.google.android.youtube", "YouTube", 22),
            AppUsageSegment("com.android.chrome", "Chrome", 8)
        )

        val at29 = UsageMilestoneTracker.checkMilestone(
            childId = "child-1",
            totalUsedMinutesToday = 29,
            alertIntervalMinutes = 30,
            lastNotifiedMilestoneStep = 0,
            currentPlaceName = "학교앞 GS25 편의점",
            recentAppUsages = recentApps,
            nowMillis = 1000L
        )
        assertThat(at29.alert).isNull()
        assertThat(at29.newMilestoneStep).isEqualTo(0)

        val at30 = UsageMilestoneTracker.checkMilestone(
            childId = "child-1",
            totalUsedMinutesToday = 30,
            alertIntervalMinutes = 30,
            lastNotifiedMilestoneStep = 0,
            currentPlaceName = "학교앞 GS25 편의점",
            recentAppUsages = recentApps,
            nowMillis = 2000L
        )
        assertThat(at30.alert).isNotNull()
        assertThat(at30.newMilestoneStep).isEqualTo(1)
        assertThat(at30.alert!!.milestoneMinutes).isEqualTo(30)
        assertThat(at30.alert!!.message).contains("학교앞 GS25 편의점")
        assertThat(at30.alert!!.message).contains("YouTube 22분")

        val duplicateAt31 = UsageMilestoneTracker.checkMilestone(
            childId = "child-1",
            totalUsedMinutesToday = 31,
            alertIntervalMinutes = 30,
            lastNotifiedMilestoneStep = at30.newMilestoneStep,
            currentPlaceName = "학교앞 GS25 편의점",
            recentAppUsages = recentApps,
            nowMillis = 3000L
        )
        assertThat(duplicateAt31.alert).isNull()

        // Custom interval: 15 minutes at 45 minutes used
        val customIntervalAt45 = UsageMilestoneTracker.checkMilestone(
            childId = "child-1",
            totalUsedMinutesToday = 45,
            alertIntervalMinutes = 15,
            lastNotifiedMilestoneStep = 2,
            currentPlaceName = "대치동 수학학원",
            recentAppUsages = recentApps,
            nowMillis = 4000L
        )
        assertThat(customIntervalAt45.alert).isNotNull()
        assertThat(customIntervalAt45.alert!!.milestoneMinutes).isEqualTo(45)
        assertThat(customIntervalAt45.newMilestoneStep).isEqualTo(3)
    }

    @Test
    fun `BrowserContentFilter ignores focused typing and extracts Chrome URLs and YouTube titles with debounce`() {
        val typingLog = BrowserContentFilter.extractContent(
            packageName = "com.android.chrome",
            urlCandidate = "https://www.google.c",
            titleCandidate = "Google",
            isUrlBarFocused = true,
            nowMillis = 10_000L,
            lastLog = null
        )
        assertThat(typingLog).isNull()

        val committedChromeLog = BrowserContentFilter.extractContent(
            packageName = "com.android.chrome",
            urlCandidate = "m.naver.com/news/123",
            titleCandidate = "네이버 뉴스 - 과학 기술",
            isUrlBarFocused = false,
            nowMillis = 12_000L,
            lastLog = null
        )
        assertThat(committedChromeLog).isNotNull()
        assertThat(committedChromeLog!!.contentType).isEqualTo(ContentType.WEB_PAGE)
        assertThat(committedChromeLog.urlOrQuery).isEqualTo("https://m.naver.com/news/123")

        val debouncedDuplicate = BrowserContentFilter.extractContent(
            packageName = "com.android.chrome",
            urlCandidate = "https://m.naver.com/news/123",
            titleCandidate = "네이버 뉴스 - 과학 기술",
            isUrlBarFocused = false,
            nowMillis = 13_500L,
            lastLog = committedChromeLog
        )
        assertThat(debouncedDuplicate).isNull()

        val youtubeLog = BrowserContentFilter.extractContent(
            packageName = "com.google.android.youtube",
            urlCandidate = null,
            titleCandidate = "[Shorts] 중학생 필수 영단어 50개 요약",
            isUrlBarFocused = false,
            nowMillis = 20_000L,
            lastLog = committedChromeLog
        )
        assertThat(youtubeLog).isNotNull()
        assertThat(youtubeLog!!.contentType).isEqualTo(ContentType.YOUTUBE_SHORTS)
        assertThat(youtubeLog.title).contains("중학생 필수 영단어")
    }

    @Test
    fun `BrowserContentFilter suppresses repeated evaluation of the same page regardless of elapsed time`() {
        val firstLog = BrowserContentFilter.extractContent(
            packageName = "com.android.chrome",
            urlCandidate = "m.naver.com/home",
            titleCandidate = "NAVER",
            isUrlBarFocused = false,
            nowMillis = 12_000L,
            lastLog = null
        )
        assertThat(firstLog).isNotNull()

        // 같은 페이지가 열려 있는 동안의 늦은 창 이벤트(3초 초과)도 새 기록이 아니어야 한다
        val staleDuplicate = BrowserContentFilter.extractContent(
            packageName = "com.android.chrome",
            urlCandidate = "https://m.naver.com/home",
            titleCandidate = "NAVER",
            isUrlBarFocused = false,
            nowMillis = 15_000L,
            lastLog = firstLog
        )
        assertThat(staleDuplicate).isNull()

        // 훨씬 많은 시간이 지나도 같은 페이지가 유지 중이면 새 기록을 만들지 않는다
        val muchLaterDuplicate = BrowserContentFilter.extractContent(
            packageName = "com.android.chrome",
            urlCandidate = "https://m.naver.com/home",
            titleCandidate = "NAVER",
            isUrlBarFocused = false,
            nowMillis = 900_000L,
            lastLog = firstLog
        )
        assertThat(muchLaterDuplicate).isNull()

        // 다른 URL을 거쳐 다시 열면 새로 기록한다
        val navigatedAway = BrowserContentFilter.extractContent(
            packageName = "com.android.chrome",
            urlCandidate = "https://mail.google.com/mail/u/0",
            titleCandidate = "Gmail",
            isUrlBarFocused = false,
            nowMillis = 905_000L,
            lastLog = firstLog
        )
        assertThat(navigatedAway).isNotNull()

        // YouTube도 같은 영상 유지 중엔 중복 기록하지 않는다
        val youtubeFirst = BrowserContentFilter.extractContent(
            packageName = "com.google.android.youtube",
            urlCandidate = null,
            titleCandidate = "[Shorts] 중학생 필수 영단어 50개 요약",
            isUrlBarFocused = false,
            nowMillis = 1_000_000L,
            lastLog = navigatedAway
        )
        assertThat(youtubeFirst).isNotNull()
        val youtubeDuplicate = BrowserContentFilter.extractContent(
            packageName = "com.google.android.youtube",
            urlCandidate = null,
            titleCandidate = "[Shorts] 중학생 필수 영단어 50개 요약",
            isUrlBarFocused = false,
            nowMillis = 1_500_000L,
            lastLog = youtubeFirst
        )
        assertThat(youtubeDuplicate).isNull()
    }

    @Test
    fun `TamperGuardDetector detects Settings accessibility and device admin tampering`() {
        val safeSettings = TamperGuardDetector.inspectWindow(
            packageName = "com.android.settings",
            className = "com.android.settings.Settings\$WifiSettingsActivity",
            visibleTexts = listOf("Wi-Fi", "집 공유기 연결됨"),
            protectedAppName = "FamilyLink Plus"
        )
        assertThat(safeSettings.isTamperAttempt).isFalse()

        val accessibilityTamper = TamperGuardDetector.inspectWindow(
            packageName = "com.android.settings",
            className = "com.android.settings.SubSettings",
            visibleTexts = listOf("접근성", "FamilyLink Plus", "사용 안함", "설치된 앱"),
            protectedAppName = "FamilyLink Plus"
        )
        assertThat(accessibilityTamper.isTamperAttempt).isTrue()
        assertThat(accessibilityTamper.reason).contains("접근성")
    }

    @Test
    fun `TamperGuardDetector detects Samsung-specific device admin and installed apps screens`() {
        // 삼성 표기: "디바이스 관리자"는 기기 관리자 해제 시도로 항상 차단
        val samsungAdmin = TamperGuardDetector.inspectWindow(
            packageName = "com.android.settings",
            className = "com.android.settings.SubSettings",
            visibleTexts = listOf("디바이스 관리자", "FamilyLink Plus", "취소"),
            protectedAppName = "FamilyLink Plus"
        )
        assertThat(samsungAdmin.isTamperAttempt).isTrue()
        assertThat(samsungAdmin.reason).contains("기기 관리자")

        // 삼성 설정 앱 목록: 보호 앱이 목록에 들어온 상태로 "설치된 앱" 화면 진입 차단
        val installedApps = TamperGuardDetector.inspectWindow(
            packageName = "com.android.settings",
            className = "com.android.settings.applications.ApplicationsSettings",
            visibleTexts = listOf("설치된 앱", "FamilyLink Plus", "YouTube", "설정"),
            protectedAppName = "FamilyLink Plus"
        )
        assertThat(installedApps.isTamperAttempt).isTrue()

        // 보호 앱이 언급되지 않는 설정 앱 목록(일반 사용)은 과잉 차단하지 않는다
        val otherAppsOnly = TamperGuardDetector.inspectWindow(
            packageName = "com.android.settings",
            className = "com.android.settings.applications.ApplicationsSettings",
            visibleTexts = listOf("설치된 앱", "YouTube", "Google", "설정"),
            protectedAppName = "FamilyLink Plus"
        )
        assertThat(otherAppsOnly.isTamperAttempt).isFalse()
    }

    @Test
    fun `SafeZoneEvaluator detects entry and exit transitions`() {
        val schoolZone = SafeZone(
            zoneId = "zone-school",
            name = "역삼초등학교",
            latitude = 37.5000,
            longitude = 127.0330,
            radiusMeters = 150.0
        )

        val enterAlerts = SafeZoneEvaluator.evaluateTransitions(
            childId = "child-1",
            previousLat = 37.5030,
            previousLng = 127.0330,
            currentLat = 37.5001,
            currentLng = 127.0331,
            zones = listOf(schoolZone),
            nowMillis = 50_000L
        )
        assertThat(enterAlerts).hasSize(1)
        assertThat(enterAlerts.first().type).isEqualTo(AlertType.SAFE_ZONE_ENTER)
        assertThat(enterAlerts.first().title).contains("역삼초등학교")

        val exitAlerts = SafeZoneEvaluator.evaluateTransitions(
            childId = "child-1",
            previousLat = 37.5001,
            previousLng = 127.0331,
            currentLat = 37.5035,
            currentLng = 127.0330,
            zones = listOf(schoolZone),
            nowMillis = 90_000L
        )
        assertThat(exitAlerts).hasSize(1)
        assertThat(exitAlerts.first().type).isEqualTo(AlertType.SAFE_ZONE_EXIT)
    }
}
