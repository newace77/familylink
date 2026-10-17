package com.familylink.model

data class MilestoneCheckResult(
    val alert: ParentAlert?,
    val newMilestoneStep: Int
)

object UsageMilestoneTracker {
    fun checkMilestone(
        childId: String,
        totalUsedMinutesToday: Int,
        alertIntervalMinutes: Int,
        lastNotifiedMilestoneStep: Int,
        currentPlaceName: String?,
        recentAppUsages: List<AppUsageSegment>,
        nowMillis: Long = System.currentTimeMillis()
    ): MilestoneCheckResult {
        val safeInterval = alertIntervalMinutes.coerceAtLeast(1)
        val currentStep = totalUsedMinutesToday / safeInterval

        if (currentStep <= 0 || currentStep <= lastNotifiedMilestoneStep) {
            return MilestoneCheckResult(
                alert = null,
                newMilestoneStep = maxOf(lastNotifiedMilestoneStep, currentStep.coerceAtMost(lastNotifiedMilestoneStep))
            )
        }

        val milestoneMinutes = currentStep * safeInterval
        val topAppsSummary = recentAppUsages
            .sortedByDescending { it.usageMinutes }
            .take(3)
            .joinToString(", ") { "${it.appName} ${it.usageMinutes}분" }
            .ifBlank { "스마트폰 활성 사용" }

        val placeLabel = currentPlaceName?.takeIf { it.isNotBlank() } ?: "위치 확인 중"
        val alert = ParentAlert(
            alertId = "milestone-${childId}-${milestoneMinutes}-${nowMillis}",
            childId = childId,
            type = AlertType.USAGE_MILESTONE,
            title = "⏰ 자녀 스마트폰 사용 ${milestoneMinutes}분 경과 (${safeInterval}분 주기 알림)",
            message = "현재 위치: $placeLabel | 주요 사용 앱: $topAppsSummary",
            timestampMillis = nowMillis,
            placeName = placeLabel,
            milestoneMinutes = milestoneMinutes
        )

        return MilestoneCheckResult(
            alert = alert,
            newMilestoneStep = currentStep
        )
    }
}
