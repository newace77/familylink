package com.familylink.data

import com.familylink.model.AlertType
import com.familylink.model.AppAccessMode
import com.familylink.model.AppRule
import com.familylink.model.AppUsageSegment
import com.familylink.model.ContentType
import com.familylink.model.ControlPolicy
import com.familylink.model.DeviceHealth
import com.familylink.model.NetworkState
import com.familylink.model.ParentAlert
import com.familylink.model.RingerState
import com.familylink.model.SafeZone
import com.familylink.model.StayPointSession
import com.familylink.model.WebContentLog

object DemoScenarioSeed {
    fun defaultAppRules(): Map<String, AppRule> = linkedMapOf(
        "com.android.dialer" to AppRule(
            packageName = "com.android.dialer",
            appName = "전화",
            accessMode = AppAccessMode.ALWAYS_ALLOWED,
            category = "필수 통신"
        ),
        "com.google.android.apps.messaging" to AppRule(
            packageName = "com.google.android.apps.messaging",
            appName = "메시지",
            accessMode = AppAccessMode.ALWAYS_ALLOWED,
            category = "필수 통신"
        ),
        "com.kakao.talk" to AppRule(
            packageName = "com.kakao.talk",
            appName = "카카오톡",
            accessMode = AppAccessMode.ALLOWED,
            dailyQuotaMinutes = 60,
            category = "메신저"
        ),
        "com.google.android.youtube" to AppRule(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            accessMode = AppAccessMode.ALLOWED,
            dailyQuotaMinutes = 45,
            category = "동영상/미디어"
        ),
        "com.android.chrome" to AppRule(
            packageName = "com.android.chrome",
            appName = "Chrome 브라우저",
            accessMode = AppAccessMode.ALLOWED,
            dailyQuotaMinutes = 60,
            category = "웹 브라우저"
        ),
        "com.sec.android.app.sbrowser" to AppRule(
            packageName = "com.sec.android.app.sbrowser",
            appName = "삼성 인터넷",
            accessMode = AppAccessMode.ALLOWED,
            dailyQuotaMinutes = 60,
            category = "웹 브라우저"
        ),
        "kr.co.ebsi.player" to AppRule(
            packageName = "kr.co.ebsi.player",
            appName = "EBS 중학 강의",
            accessMode = AppAccessMode.ALWAYS_ALLOWED,
            category = "학습/교육"
        ),
        "com.supercell.brawlstars" to AppRule(
            packageName = "com.supercell.brawlstars",
            appName = "브롤스타즈 (게임)",
            accessMode = AppAccessMode.BLOCKED,
            dailyQuotaMinutes = 0,
            category = "게임"
        ),
        "com.roblox.client" to AppRule(
            packageName = "com.roblox.client",
            appName = "로블록스 (Roblox)",
            accessMode = AppAccessMode.BLOCKED,
            dailyQuotaMinutes = 0,
            category = "게임"
        )
    )

    fun defaultPolicy(): ControlPolicy = ControlPolicy(
        childId = "child-1",
        childName = "민준 (초6)",
        allowedStartHour = 10,
        allowedStartMinute = 0,
        allowedEndHour = 21,
        allowedEndMinute = 0,
        dailyLimitMinutes = 120,
        bonusMinutesToday = 0,
        isImmediateLocked = false,
        alertIntervalMinutes = 30,
        parentPin = "0000",
        tamperProtectionEnabled = true,
        appRules = defaultAppRules()
    )

    fun defaultWebLogs(nowMillis: Long = System.currentTimeMillis()): List<WebContentLog> = listOf(
        WebContentLog(
            id = "web-1",
            timestampMillis = nowMillis - 12 * 60_000L,
            sourcePackage = "com.google.android.youtube",
            browserOrAppName = "YouTube",
            contentType = ContentType.YOUTUBE_VIDEO,
            title = "마인크래프트 야생 건축 100일 생존기 하이라이트",
            urlOrQuery = "https://m.youtube.com/watch?v=mc100days",
            durationSeconds = 1140,
            placeName = "역삼초 앞 GS25 편의점",
            latitude = 37.4994,
            longitude = 127.0334
        ),
        WebContentLog(
            id = "web-2",
            timestampMillis = nowMillis - 24 * 60_000L,
            sourcePackage = "com.google.android.youtube",
            browserOrAppName = "YouTube",
            contentType = ContentType.YOUTUBE_SHORTS,
            title = "[Shorts] 편의점 신상 꿀조합 레시피 TOP 5",
            urlOrQuery = "https://m.youtube.com/shorts/gs25recipe",
            durationSeconds = 660,
            placeName = "역삼초 앞 GS25 편의점",
            latitude = 37.4994,
            longitude = 127.0334
        ),
        WebContentLog(
            id = "web-3",
            timestampMillis = nowMillis - 33 * 60_000L,
            sourcePackage = "com.android.chrome",
            browserOrAppName = "Chrome",
            contentType = ContentType.WEB_PAGE,
            title = "나무위키 - 마인크래프트 엔더 드래곤 공략",
            urlOrQuery = "https://namu.wiki/w/마인크래프트/엔더드래곤",
            durationSeconds = 420,
            placeName = "역삼초 앞 GS25 편의점",
            latitude = 37.4994,
            longitude = 127.0334
        ),
        WebContentLog(
            id = "web-4",
            timestampMillis = nowMillis - 38 * 60_000L,
            sourcePackage = "com.android.chrome",
            browserOrAppName = "Chrome",
            contentType = ContentType.SEARCH_QUERY,
            title = "Google 검색: 브롤스타즈 신규 브롤러 티어표",
            urlOrQuery = "https://www.google.com/search?q=브롤스타즈+신규+브롤러+티어표",
            durationSeconds = 180,
            placeName = "역삼초 앞 GS25 편의점",
            latitude = 37.4994,
            longitude = 127.0334
        ),
        WebContentLog(
            id = "web-5",
            timestampMillis = nowMillis - 95 * 60_000L,
            sourcePackage = "com.android.chrome",
            browserOrAppName = "Chrome",
            contentType = ContentType.WEB_PAGE,
            title = "EBS 초등 6학년 2학기 비례식과 비례배분 문제풀이",
            urlOrQuery = "https://primary.ebs.co.kr/course/math6",
            durationSeconds = 900,
            placeName = "대치동 최상위 수학학원",
            latitude = 37.4981,
            longitude = 127.0562
        ),
        WebContentLog(
            id = "web-6",
            timestampMillis = nowMillis - 180 * 60_000L,
            sourcePackage = "com.sec.android.app.sbrowser",
            browserOrAppName = "삼성 인터넷",
            contentType = ContentType.WEB_PAGE,
            title = "역삼초등학교 주간 가정통신문 및 급식 식단표",
            urlOrQuery = "https://yeoksam.es.kr/notice/weekly",
            durationSeconds = 240,
            placeName = "역삼초등학교",
            latitude = 37.5000,
            longitude = 127.0330
        )
    )

    fun defaultStaySessions(nowMillis: Long = System.currentTimeMillis()): List<StayPointSession> {
        val allWebLogs = defaultWebLogs(nowMillis)
        return listOf(
            StayPointSession(
                sessionId = "stay-conv-store",
                childId = "child-1",
                dateKey = "2026-10-17",
                latitude = 37.4994,
                longitude = 127.0334,
                placeName = "역삼초 앞 GS25 편의점",
                address = "서울 강남구 역삼로 123 (학교 정문 앞 30m)",
                arrivedAtMillis = nowMillis - 42 * 60_000L,
                departedAtMillis = nowMillis - 2 * 60_000L,
                appUsages = listOf(
                    AppUsageSegment("com.google.android.youtube", "YouTube", 30, category = "동영상/미디어"),
                    AppUsageSegment("com.android.chrome", "Chrome 브라우저", 10, category = "웹 브라우저")
                ),
                webLogs = allWebLogs.filter { it.placeName == "역삼초 앞 GS25 편의점" }
            ),
            StayPointSession(
                sessionId = "stay-math-academy",
                childId = "child-1",
                dateKey = "2026-10-17",
                latitude = 37.4981,
                longitude = 127.0562,
                placeName = "대치동 최상위 수학학원",
                address = "서울 강남구 도곡로 415 4층",
                arrivedAtMillis = nowMillis - 125 * 60_000L,
                departedAtMillis = nowMillis - 55 * 60_000L,
                appUsages = listOf(
                    AppUsageSegment("kr.co.ebsi.player", "EBS 중학 강의", 15, category = "학습/교육"),
                    AppUsageSegment("com.android.chrome", "Chrome 브라우저", 15, category = "웹 브라우저"),
                    AppUsageSegment("com.kakao.talk", "카카오톡", 8, category = "메신저")
                ),
                webLogs = allWebLogs.filter { it.placeName == "대치동 최상위 수학학원" }
            ),
            StayPointSession(
                sessionId = "stay-school",
                childId = "child-1",
                dateKey = "2026-10-17",
                latitude = 37.5000,
                longitude = 127.0330,
                placeName = "역삼초등학교",
                address = "서울 강남구 역삼로 120",
                arrivedAtMillis = nowMillis - 360 * 60_000L,
                departedAtMillis = nowMillis - 140 * 60_000L,
                appUsages = listOf(
                    AppUsageSegment("com.kakao.talk", "카카오톡", 6, category = "메신저"),
                    AppUsageSegment("com.sec.android.app.sbrowser", "삼성 인터넷", 4, category = "웹 브라우저")
                ),
                webLogs = allWebLogs.filter { it.placeName == "역삼초등학교" }
            ),
            StayPointSession(
                sessionId = "stay-home",
                childId = "child-1",
                dateKey = "2026-10-17",
                latitude = 37.4968,
                longitude = 127.0389,
                placeName = "역삼래미안 아파트 (우리집)",
                address = "서울 강남구 언주로 310",
                arrivedAtMillis = nowMillis - 450 * 60_000L,
                departedAtMillis = nowMillis - 375 * 60_000L,
                appUsages = listOf(
                    AppUsageSegment("com.kakao.talk", "카카오톡", 5, category = "메신저")
                ),
                webLogs = emptyList()
            )
        )
    }

    fun defaultSafeZones(): List<SafeZone> = listOf(
        SafeZone("zone-home", "우리집 (역삼래미안)", 37.4968, 127.0389, radiusMeters = 150.0),
        SafeZone("zone-school", "역삼초등학교", 37.5000, 127.0330, radiusMeters = 180.0),
        SafeZone("zone-academy", "대치동 최상위 수학학원", 37.4981, 127.0562, radiusMeters = 120.0)
    )

    fun defaultAlerts(nowMillis: Long = System.currentTimeMillis()): List<ParentAlert> = listOf(
        ParentAlert(
            alertId = SeedAlertIds.MILESTONE_60,
            type = AlertType.USAGE_MILESTONE,
            title = "⏰ 자녀 스마트폰 사용 60분 경과 (30분 주기 알림)",
            message = "현재 위치: 역삼초 앞 GS25 편의점 | 최근 30분: YouTube 30분, Chrome 10분 사용 중",
            timestampMillis = nowMillis - 5 * 60_000L,
            placeName = "역삼초 앞 GS25 편의점",
            milestoneMinutes = 60
        ),
        ParentAlert(
            alertId = SeedAlertIds.ZONE_EXIT_ACADEMY,
            type = AlertType.SAFE_ZONE_EXIT,
            title = "🚶 안심존 이탈: 대치동 최상위 수학학원",
            message = "민준(초6)이 '대치동 최상위 수학학원'에서 하원하여 이동 중입니다.",
            timestampMillis = nowMillis - 50 * 60_000L,
            placeName = "대치동 최상위 수학학원"
        ),
        ParentAlert(
            alertId = SeedAlertIds.MILESTONE_30,
            type = AlertType.USAGE_MILESTONE,
            title = "⏰ 자녀 스마트폰 사용 30분 경과 (30분 주기 알림)",
            message = "현재 위치: 대치동 최상위 수학학원 | 최근 30분: EBS 중학 강의 15분, Chrome 15분 사용",
            timestampMillis = nowMillis - 70 * 60_000L,
            placeName = "대치동 최상위 수학학원",
            milestoneMinutes = 30
        )
    )

    fun defaultDeviceHealth(nowMillis: Long = System.currentTimeMillis()): DeviceHealth = DeviceHealth(
        childId = "child-1",
        batteryPercent = 78,
        isCharging = false,
        ringerState = RingerState.VIBRATE,
        networkState = NetworkState.CELLULAR,
        currentPlaceName = "역삼초 앞 GS25 편의점",
        currentLatitude = 37.4994,
        currentLongitude = 127.0334,
        currentForegroundApp = "YouTube",
        totalUsedMinutesToday = 68,
        appUsedMinutesToday = mapOf(
            "com.google.android.youtube" to 30,
            "com.android.chrome" to 25,
            "com.kakao.talk" to 19,
            "kr.co.ebsi.player" to 15,
            "com.sec.android.app.sbrowser" to 4
        ),
        accessibilityEnabled = true,
        usageStatsEnabled = true,
        overlayPermissionEnabled = true,
        deviceAdminEnabled = true,
        locationPermissionEnabled = true,
        lastUpdatedMillis = nowMillis
    )
}
