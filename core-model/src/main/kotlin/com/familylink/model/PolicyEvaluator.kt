package com.familylink.model

object PolicyEvaluator {
    private val SYSTEM_ESSENTIAL_PACKAGES = setOf(
        "com.familylink.child",
        "com.familylink.parent",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.android.server.telecom",
        "com.android.phone",
        "com.android.emergency",
        "com.android.systemui"
    )

    fun evaluate(
        policy: ControlPolicy,
        currentPackage: String,
        currentHour: Int,
        currentMinute: Int,
        totalUsedMinutesToday: Int,
        appUsedMinutesToday: Map<String, Int>
    ): LockDecision {
        val rule = policy.appRules[currentPackage]
        if (currentPackage in SYSTEM_ESSENTIAL_PACKAGES || rule?.accessMode == AppAccessMode.ALWAYS_ALLOWED) {
            return LockDecision(
                isBlocked = false,
                reason = LockReason.NONE,
                remainingDailyMinutes = computeRemainingDaily(policy, totalUsedMinutesToday)
            )
        }

        if (policy.isImmediateLocked) {
            return LockDecision(
                isBlocked = true,
                reason = LockReason.PARENT_INSTANT_LOCK,
                message = "부모님이 원격 즉시 잠금을 설정했습니다."
            )
        }

        if (rule?.accessMode == AppAccessMode.BLOCKED) {
            return LockDecision(
                isBlocked = true,
                reason = LockReason.APP_BLOCKED,
                message = "'${rule.appName}' 앱은 사용이 제한된 앱입니다."
            )
        }

        if (!isWithinAllowedWindow(
                currentHour = currentHour,
                currentMinute = currentMinute,
                startHour = policy.allowedStartHour,
                startMinute = policy.allowedStartMinute,
                endHour = policy.allowedEndHour,
                endMinute = policy.allowedEndMinute
            )
        ) {
            return LockDecision(
                isBlocked = true,
                reason = LockReason.OUTSIDE_ALLOWED_HOURS,
                message = "사용 가능 시간대(${policy.formattedAllowedWindow()})가 아닙니다."
            )
        }

        val effectiveDailyLimit = policy.effectiveDailyLimitMinutes()
        if (effectiveDailyLimit != null && totalUsedMinutesToday >= effectiveDailyLimit) {
            return LockDecision(
                isBlocked = true,
                reason = LockReason.DAILY_QUOTA_EXCEEDED,
                message = "오늘 사용 가능한 시간(${effectiveDailyLimit}분)을 모두 사용했습니다.",
                remainingDailyMinutes = 0
            )
        }

        val appQuota = rule?.dailyQuotaMinutes
        val appUsed = appUsedMinutesToday[currentPackage] ?: 0
        if (appQuota != null && appUsed >= appQuota) {
            return LockDecision(
                isBlocked = true,
                reason = LockReason.APP_QUOTA_EXCEEDED,
                message = "'${rule.appName}' 일일 허용 시간(${appQuota}분)을 모두 사용했습니다.",
                remainingDailyMinutes = computeRemainingDaily(policy, totalUsedMinutesToday),
                remainingAppMinutes = 0
            )
        }

        return LockDecision(
            isBlocked = false,
            reason = LockReason.NONE,
            remainingDailyMinutes = computeRemainingDaily(policy, totalUsedMinutesToday),
            remainingAppMinutes = appQuota?.let { (it - appUsed).coerceAtLeast(0) }
        )
    }

    fun isWithinAllowedWindow(
        currentHour: Int,
        currentMinute: Int,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ): Boolean {
        val nowMinutes = currentHour * 60 + currentMinute
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute

        return when {
            startMinutes == endMinutes -> true
            startMinutes < endMinutes -> nowMinutes in startMinutes until endMinutes
            else -> nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }

    private fun computeRemainingDaily(policy: ControlPolicy, totalUsedMinutesToday: Int): Int? {
        val effectiveLimit = policy.effectiveDailyLimitMinutes() ?: return null
        return (effectiveLimit - totalUsedMinutesToday).coerceAtLeast(0)
    }
}
