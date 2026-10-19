package com.familylink.model

enum class AppAccessMode {
    ALWAYS_ALLOWED,
    ALLOWED,
    BLOCKED
}

enum class LockReason(val displayTitle: String, val displayDescription: String) {
    NONE("사용 가능", "현재 정상적으로 사용 가능한 상태입니다."),
    PARENT_INSTANT_LOCK("부모님 즉시 잠금", "부모님이 스마트폰을 즉시 잠금 상태로 설정했습니다."),
    OUTSIDE_ALLOWED_HOURS("사용 가능 시간 외", "설정된 스마트폰 사용 가능 시간대가 아닙니다."),
    DAILY_QUOTA_EXCEEDED("일일 사용 시간 초과", "오늘 허용된 총 스마트폰 사용 시간을 모두 사용했습니다."),
    APP_BLOCKED("차단된 앱", "부모님이 사용을 제한한 앱입니다."),
    APP_QUOTA_EXCEEDED("앱 일일 제한 초과", "이 앱의 오늘 허용된 사용 시간을 모두 사용했습니다."),
    TAMPER_PROTECTION("보안 및 설정 보호", "부모님 승인 없이 설정이나 권한을 변경할 수 없습니다.")
}

data class AppRule(
    val packageName: String,
    val appName: String,
    val accessMode: AppAccessMode = AppAccessMode.ALLOWED,
    val dailyQuotaMinutes: Int? = null,
    val category: String = "일반"
)

data class ControlPolicy(
    val childId: String = "child-1",
    val childName: String = "우리 아이",
    val allowedStartHour: Int = 10,
    val allowedStartMinute: Int = 0,
    val allowedEndHour: Int = 21,
    val allowedEndMinute: Int = 0,
    val dailyLimitMinutes: Int? = 180,
    val bonusMinutesToday: Int = 0,
    val isImmediateLocked: Boolean = false,
    val alertIntervalMinutes: Int = 30,
    val parentPin: String = "0000",
    val tamperProtectionEnabled: Boolean = true,
    val appRules: Map<String, AppRule> = emptyMap(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
) {
    fun formattedAllowedWindow(): String =
        String.format("%02d:%02d ~ %02d:%02d", allowedStartHour, allowedStartMinute, allowedEndHour, allowedEndMinute)

    fun effectiveDailyLimitMinutes(): Int? =
        dailyLimitMinutes?.let { (it + bonusMinutesToday).coerceAtLeast(0) }
}

data class LockDecision(
    val isBlocked: Boolean,
    val reason: LockReason = LockReason.NONE,
    val message: String = reason.displayDescription,
    val remainingDailyMinutes: Int? = null,
    val remainingAppMinutes: Int? = null
)

enum class ContentType(val label: String) {
    WEB_PAGE("웹페이지"),
    SEARCH_QUERY("검색어"),
    YOUTUBE_VIDEO("YouTube 영상"),
    YOUTUBE_SHORTS("YouTube 쇼츠")
}

data class WebContentLog(
    val id: String,
    val childId: String = "child-1",
    val timestampMillis: Long,
    val sourcePackage: String,
    val browserOrAppName: String,
    val contentType: ContentType,
    val title: String,
    val urlOrQuery: String,
    val durationSeconds: Int = 0,
    val placeName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class AppUsageSegment(
    val packageName: String,
    val appName: String,
    val usageMinutes: Int,
    val usageSeconds: Int = usageMinutes * 60,
    val category: String = "일반"
)

data class StayPointSession(
    val sessionId: String,
    val childId: String = "child-1",
    val dateKey: String,
    val latitude: Double,
    val longitude: Double,
    val placeName: String,
    val address: String,
    val arrivedAtMillis: Long,
    val departedAtMillis: Long,
    val appUsages: List<AppUsageSegment> = emptyList(),
    val webLogs: List<WebContentLog> = emptyList()
) {
    val dwellMinutes: Int
        get() = ((departedAtMillis - arrivedAtMillis).coerceAtLeast(0L) / 60_000L).toInt()

    /** departedAtMillis == 0L 이면 아직 체류 중(진행 중) 세션이다. */
    val isOngoing: Boolean
        get() = departedAtMillis == 0L

    val totalScreenMinutes: Int
        get() = appUsages.sumOf { it.usageMinutes }

    fun headlineBadge(): String {
        val topApps = appUsages
            .sortedByDescending { it.usageMinutes }
            .take(2)
            .joinToString(", ") { "${it.appName} ${it.usageMinutes}분" }
        return if (topApps.isNotBlank()) {
            "$placeName · $topApps"
        } else {
            "$placeName (${dwellMinutes}분 체류)"
        }
    }
}

data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val placeName: String = "알 수 없는 위치",
    val address: String = ""
)

data class AppUsageSlice(
    val packageName: String,
    val appName: String,
    val startMillis: Long,
    val endMillis: Long,
    val category: String = "일반"
)

enum class RingerState(val label: String) {
    NORMAL("벨소리"),
    VIBRATE("진동"),
    SILENT("무음")
}

enum class NetworkState(val label: String) {
    WIFI("Wi-Fi 연결"),
    CELLULAR("모바일 데이터"),
    OFFLINE("오프라인")
}

data class DeviceHealth(
    val childId: String = "child-1",
    val batteryPercent: Int = 85,
    val isCharging: Boolean = false,
    val ringerState: RingerState = RingerState.NORMAL,
    val networkState: NetworkState = NetworkState.WIFI,
    val currentPlaceName: String = "위치 미확인",
    val currentLatitude: Double = 0.0,
    val currentLongitude: Double = 0.0,
    val currentForegroundApp: String = "",
    val totalUsedMinutesToday: Int = 0,
    val appUsedMinutesToday: Map<String, Int> = emptyMap(),
    val accessibilityEnabled: Boolean = true,
    val usageStatsEnabled: Boolean = true,
    val overlayPermissionEnabled: Boolean = true,
    val deviceAdminEnabled: Boolean = true,
    val locationPermissionEnabled: Boolean = false,
    val lastUpdatedMillis: Long = System.currentTimeMillis()
)

data class SafeZone(
    val zoneId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = 150.0,
    val notifyOnEnter: Boolean = true,
    val notifyOnExit: Boolean = true
)

enum class AlertType(val badgeText: String) {
    USAGE_MILESTONE("30분 주기 알림"),
    SAFE_ZONE_ENTER("안심존 진입"),
    SAFE_ZONE_EXIT("안심존 이탈"),
    TAMPER_ATTEMPT("우회/설정 변경 감지"),
    BONUS_TIME_REQUEST("보너스 시간 요청"),
    POLICY_LOCK_TRIGGERED("사용 제한 잠금")
}

data class ParentAlert(
    val alertId: String,
    val childId: String = "child-1",
    val type: AlertType,
    val title: String,
    val message: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val placeName: String? = null,
    val milestoneMinutes: Int? = null,
    val isAcknowledged: Boolean = false
)
