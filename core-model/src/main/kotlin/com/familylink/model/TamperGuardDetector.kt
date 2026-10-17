package com.familylink.model

data class TamperCheckResult(
    val isTamperAttempt: Boolean,
    val reason: String = ""
)

object TamperGuardDetector {
    private val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller"
    )

    private val SENSITIVE_KEYWORDS = listOf(
        "접근성" to "접근성 서비스 해제 시도 감지",
        "Accessibility" to "접근성 서비스 해제 시도 감지",
        "기기 관리자" to "기기 관리자 권한 해제 시도 감지",
        "Device admin" to "기기 관리자 권한 해제 시도 감지",
        "강제 종료" to "보호 앱 강제 종료 또는 삭제 화면 진입 감지",
        "제거" to "보호 앱 삭제 시도 감지",
        "날짜 및 시간" to "사용 시간대 우회를 위한 날짜/시간 변경 시도 감지"
    )

    fun inspectWindow(
        packageName: String,
        className: String?,
        visibleTexts: List<String>,
        protectedAppName: String = "FamilyLink Plus"
    ): TamperCheckResult {
        if (packageName !in SETTINGS_PACKAGES) {
            return TamperCheckResult(isTamperAttempt = false)
        }

        val joined = visibleTexts.joinToString(" ")
        val mentionsProtectedApp = visibleTexts.any {
            it.contains(protectedAppName, ignoreCase = true) ||
                it.contains("familylink", ignoreCase = true)
        }

        // Date & Time or Device Admin pages are always blocked in Settings when tamper protection is active
        if (joined.contains("날짜 및 시간") || joined.contains("기기 관리자") || joined.contains("Device admin")) {
            return TamperCheckResult(
                isTamperAttempt = true,
                reason = "설정 내 기기 관리자 또는 날짜/시간 변경 화면 접근 차단"
            )
        }

        if (mentionsProtectedApp) {
            for ((keyword, reason) in SENSITIVE_KEYWORDS) {
                if (joined.contains(keyword, ignoreCase = true)) {
                    return TamperCheckResult(isTamperAttempt = true, reason = reason)
                }
            }
        }

        return TamperCheckResult(isTamperAttempt = false)
    }
}
