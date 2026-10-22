package com.familylink.child

import com.familylink.data.FamilySyncRepository
import com.familylink.model.AlertType
import com.familylink.model.BrowserContentFilter
import com.familylink.model.LockDecision
import com.familylink.model.LockReason
import com.familylink.model.ParentAlert
import com.familylink.model.PolicyEvaluator
import com.familylink.model.TamperGuardDetector
import com.familylink.model.WebContentLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

class ChildMonitorController(
    private val repository: FamilySyncRepository
) {
    private val _activeLockState = MutableStateFlow(LockDecision(isBlocked = false))
    val activeLockState: StateFlow<LockDecision> = _activeLockState.asStateFlow()

    // 접근성 서비스 재시작 시에도 Cloud/로컬 저장소의 최신 웹 로그를 이어받아
    // 열려 있던 페이지의 재평가(중복 기록)를 막는다. 웹 로그 목록은 최신순 정렬이다.
    private var lastWebLog: WebContentLog? = repository.webLogs.value.firstOrNull()

    fun onForegroundWindowEvaluated(
        packageName: String,
        className: String?,
        visibleTexts: List<String>,
        urlCandidate: String?,
        titleCandidate: String?,
        isUrlBarFocused: Boolean,
        currentHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        currentMinute: Int = Calendar.getInstance().get(Calendar.MINUTE),
        nowMillis: Long = System.currentTimeMillis()
    ): LockDecision {
        val currentPolicy = repository.policy.value
        val currentHealth = repository.deviceHealth.value

        // 1. Check TamperGuard for Android Settings / PackageInstaller tampering
        if (currentPolicy.tamperProtectionEnabled) {
            val tamperResult = TamperGuardDetector.inspectWindow(
                packageName = packageName,
                className = className,
                visibleTexts = visibleTexts,
                protectedAppName = "FamilyLink Plus"
            )
            if (tamperResult.isTamperAttempt) {
                val decision = LockDecision(
                    isBlocked = true,
                    reason = LockReason.TAMPER_PROTECTION,
                    message = tamperResult.reason
                )
                _activeLockState.value = decision
                repository.emitAlert(
                    ParentAlert(
                        alertId = "tamper-$nowMillis",
                        childId = currentPolicy.childId,
                        type = AlertType.TAMPER_ATTEMPT,
                        title = "🚨 자녀 폰 설정/권한 변경 시도 차단됨",
                        message = "${tamperResult.reason} (위치: ${currentHealth.currentPlaceName})",
                        timestampMillis = nowMillis,
                        placeName = currentHealth.currentPlaceName
                    )
                )
                return decision
            }
        }

        // 2. Evaluate time window, daily quota, per-app rules, and instant lock
        val decision = PolicyEvaluator.evaluate(
            policy = currentPolicy,
            currentPackage = packageName,
            currentHour = currentHour,
            currentMinute = currentMinute,
            totalUsedMinutesToday = currentHealth.totalUsedMinutesToday,
            appUsedMinutesToday = currentHealth.appUsedMinutesToday
        )
        _activeLockState.value = decision

        if (decision.isBlocked) {
            return decision
        }

        // 3. Extract Browser URL / Search Query / YouTube Video & Shorts Title
        val extractedLog = BrowserContentFilter.extractContent(
            packageName = packageName,
            urlCandidate = urlCandidate,
            titleCandidate = titleCandidate,
            isUrlBarFocused = isUrlBarFocused,
            nowMillis = nowMillis,
            lastLog = lastWebLog,
            placeName = currentHealth.currentPlaceName,
            latitude = currentHealth.currentLatitude,
            longitude = currentHealth.currentLongitude
        )
        if (extractedLog != null) {
            lastWebLog = extractedLog
            repository.recordWebContentLog(extractedLog)
        }

        return decision
    }

    fun verifyParentPinAndUnlock(enteredPin: String): Boolean {
        val currentPolicy = repository.policy.value
        if (enteredPin.trim() == currentPolicy.parentPin) {
            repository.grantBonusMinutes(15)
            _activeLockState.value = LockDecision(isBlocked = false, reason = LockReason.NONE)
            return true
        }
        return false
    }

    fun requestBonusTimeFromParent(reasonText: String = "숙제 및 학습 확인용으로 15분 더 사용하고 싶어요!") {
        val currentPolicy = repository.policy.value
        val currentHealth = repository.deviceHealth.value
        repository.emitAlert(
            ParentAlert(
                alertId = "bonus-req-${System.currentTimeMillis()}",
                childId = currentPolicy.childId,
                type = AlertType.BONUS_TIME_REQUEST,
                title = "🙋 자녀 보너스 시간 요청 (${currentPolicy.childName})",
                message = "'$reasonText' (현재 위치: ${currentHealth.currentPlaceName})",
                placeName = currentHealth.currentPlaceName
            )
        )
    }
}
