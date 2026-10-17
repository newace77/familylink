package com.familylink.data

import android.content.Context
import com.familylink.model.AlertType
import com.familylink.model.AppAccessMode
import com.familylink.model.AppRule
import com.familylink.model.AppUsageSegment
import com.familylink.model.ControlPolicy
import com.familylink.model.DeviceHealth
import com.familylink.model.ParentAlert
import com.familylink.model.SafeZone
import com.familylink.model.StayPointSession
import com.familylink.model.UsageMilestoneTracker
import com.familylink.model.WebContentLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

/**
 * 부모 앱과 자녀 앱의 연동 진행 단계.
 * NOT_PAIRED: 아직 연동 없음 → WAITING: 부모가 코드 생성(자녀 입력 대기) → LINKED: 양쪽 연동 성공
 */
enum class PairingState { NOT_PAIRED, WAITING, LINKED }

interface FamilySyncRepository {
    val policy: StateFlow<ControlPolicy>
    val deviceHealth: StateFlow<DeviceHealth>
    val staySessions: StateFlow<List<StayPointSession>>
    val webLogs: StateFlow<List<WebContentLog>>
    val alerts: StateFlow<List<ParentAlert>>
    val safeZones: StateFlow<List<SafeZone>>

    fun updateAllowedTimeWindow(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int)
    fun updateDailyLimitMinutes(limitMinutes: Int?)
    fun updateAlertIntervalMinutes(intervalMinutes: Int)
    fun setImmediateLock(locked: Boolean)
    fun grantBonusMinutes(additionalMinutes: Int)
    fun updateParentPin(newPin: String)
    fun updateAppAccessMode(packageName: String, mode: AppAccessMode, dailyQuotaMinutes: Int? = null)
    fun addOrUpdateSafeZone(zone: SafeZone)
    fun removeSafeZone(zoneId: String)

    fun recordWebContentLog(log: WebContentLog)
    fun recordStayPointSession(session: StayPointSession)
    fun recordUsageProgress(
        totalUsedMinutesToday: Int,
        currentPlaceName: String,
        currentAppName: String,
        appUsagesToday: Map<String, Int> = emptyMap()
    ): ParentAlert?
    fun emitAlert(alert: ParentAlert)
    fun acknowledgeAlert(alertId: String)
    fun updateDeviceHealth(transform: (DeviceHealth) -> DeviceHealth)

    /** 부모 앱: 6자리 가족 코드를 생성하고 WAITING 상태로 진입한다. */
    suspend fun createFamilyCode(): String

    /** 자녀 앱: 부모가 알려준 6자리 코드로 연동을 시도한다. 유효 코드면 true + LINKED. */
    suspend fun linkFamilyCode(code: String): Boolean

    /** 현재 연동 진행 단계. */
    val pairingState: StateFlow<PairingState>
}

class LocalFamilySyncRepository(
    private val storageFile: File? = null
) : FamilySyncRepository {

    private val _policy = MutableStateFlow(DemoScenarioSeed.defaultPolicy())
    override val policy: StateFlow<ControlPolicy> = _policy.asStateFlow()

    private val _deviceHealth = MutableStateFlow(DemoScenarioSeed.defaultDeviceHealth())
    override val deviceHealth: StateFlow<DeviceHealth> = _deviceHealth.asStateFlow()

    private val _staySessions = MutableStateFlow(DemoScenarioSeed.defaultStaySessions())
    override val staySessions: StateFlow<List<StayPointSession>> = _staySessions.asStateFlow()

    private val _webLogs = MutableStateFlow(DemoScenarioSeed.defaultWebLogs())
    override val webLogs: StateFlow<List<WebContentLog>> = _webLogs.asStateFlow()

    private val _alerts = MutableStateFlow(DemoScenarioSeed.defaultAlerts())
    override val alerts: StateFlow<List<ParentAlert>> = _alerts.asStateFlow()

    private val _safeZones = MutableStateFlow(DemoScenarioSeed.defaultSafeZones())
    override val safeZones: StateFlow<List<SafeZone>> = _safeZones.asStateFlow()

    // 데모 구현 Stub: 로컬 흐름용 1인 가족 데모 규칙. 실제 Firestore 규칙은 Task 4가 교체한다.
    private val _pairingState = MutableStateFlow(PairingState.NOT_PAIRED)
    override val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()

    override suspend fun createFamilyCode(): String {
        val code = Random.nextInt(100_000, 1_000_000).toString()
        _pairingState.value = PairingState.WAITING
        return code
    }

    override suspend fun linkFamilyCode(code: String): Boolean {
        val valid = code.length == 6 && code.all { it.isDigit() }
        if (!valid) return false
        _pairingState.value = PairingState.LINKED
        return true
    }

    private var lastNotifiedMilestoneStep: Int =
        _deviceHealth.value.totalUsedMinutesToday / _policy.value.alertIntervalMinutes.coerceAtLeast(1)

    init {
        loadPersistedPolicyIfExists()
    }

    override fun updateAllowedTimeWindow(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        _policy.update {
            it.copy(
                allowedStartHour = startHour.coerceIn(0, 23),
                allowedStartMinute = startMinute.coerceIn(0, 59),
                allowedEndHour = endHour.coerceIn(0, 23),
                allowedEndMinute = endMinute.coerceIn(0, 59),
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        }
        persistPolicySnapshot()
    }

    override fun updateDailyLimitMinutes(limitMinutes: Int?) {
        _policy.update {
            it.copy(
                dailyLimitMinutes = limitMinutes?.coerceIn(15, 720),
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        }
        persistPolicySnapshot()
    }

    override fun updateAlertIntervalMinutes(intervalMinutes: Int) {
        val safeInterval = intervalMinutes.coerceIn(5, 180)
        _policy.update {
            it.copy(
                alertIntervalMinutes = safeInterval,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        }
        lastNotifiedMilestoneStep = _deviceHealth.value.totalUsedMinutesToday / safeInterval
        persistPolicySnapshot()
    }

    override fun setImmediateLock(locked: Boolean) {
        _policy.update {
            it.copy(
                isImmediateLocked = locked,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        }
        if (locked) {
            emitAlert(
                ParentAlert(
                    alertId = "lock-${System.currentTimeMillis()}",
                    type = AlertType.POLICY_LOCK_TRIGGERED,
                    title = "🔒 부모 원격 즉시 잠금 실행됨",
                    message = "자녀 스마트폰이 즉시 잠금 상태로 전환되었습니다 (전화·문자 제외).",
                    placeName = _deviceHealth.value.currentPlaceName
                )
            )
        }
        persistPolicySnapshot()
    }

    override fun grantBonusMinutes(additionalMinutes: Int) {
        _policy.update {
            it.copy(
                bonusMinutesToday = (it.bonusMinutesToday + additionalMinutes).coerceAtLeast(0),
                isImmediateLocked = false,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        }
        persistPolicySnapshot()
    }

    override fun updateParentPin(newPin: String) {
        if (newPin.length in 4..6 && newPin.all { it.isDigit() }) {
            _policy.update {
                it.copy(parentPin = newPin, updatedAtEpochMillis = System.currentTimeMillis())
            }
            persistPolicySnapshot()
        }
    }

    override fun updateAppAccessMode(packageName: String, mode: AppAccessMode, dailyQuotaMinutes: Int?) {
        _policy.update { current ->
            val existing = current.appRules[packageName] ?: AppRule(packageName = packageName, appName = packageName)
            val updatedMap = current.appRules.toMutableMap()
            updatedMap[packageName] = existing.copy(
                accessMode = mode,
                dailyQuotaMinutes = dailyQuotaMinutes ?: existing.dailyQuotaMinutes
            )
            current.copy(appRules = updatedMap, updatedAtEpochMillis = System.currentTimeMillis())
        }
        persistPolicySnapshot()
    }

    override fun addOrUpdateSafeZone(zone: SafeZone) {
        _safeZones.update { current ->
            val without = current.filterNot { it.zoneId == zone.zoneId }
            listOf(zone) + without
        }
    }

    override fun removeSafeZone(zoneId: String) {
        _safeZones.update { current -> current.filterNot { it.zoneId == zoneId } }
    }

    override fun recordWebContentLog(log: WebContentLog) {
        _webLogs.update { current ->
            listOf(log) + current.filterNot { it.id == log.id }
        }
        // Attach to latest matching stay session if placeName matches
        val targetPlace = log.placeName
        if (!targetPlace.isNullOrBlank()) {
            _staySessions.update { sessions ->
                sessions.mapIndexed { index, session ->
                    if (index == 0 && session.placeName == targetPlace) {
                        session.copy(webLogs = listOf(log) + session.webLogs)
                    } else {
                        session
                    }
                }
            }
        }
    }

    override fun recordStayPointSession(session: StayPointSession) {
        _staySessions.update { current ->
            listOf(session) + current.filterNot { it.sessionId == session.sessionId }
        }
    }

    override fun recordUsageProgress(
        totalUsedMinutesToday: Int,
        currentPlaceName: String,
        currentAppName: String,
        appUsagesToday: Map<String, Int>
    ): ParentAlert? {
        _deviceHealth.update { current ->
            current.copy(
                totalUsedMinutesToday = totalUsedMinutesToday,
                currentPlaceName = currentPlaceName,
                currentForegroundApp = currentAppName,
                appUsedMinutesToday = if (appUsagesToday.isNotEmpty()) appUsagesToday else current.appUsedMinutesToday,
                lastUpdatedMillis = System.currentTimeMillis()
            )
        }

        val activeStayApps = _staySessions.value.firstOrNull()?.appUsages
            ?: listOf(AppUsageSegment("active.app", currentAppName, _policy.value.alertIntervalMinutes))

        val check = UsageMilestoneTracker.checkMilestone(
            childId = _policy.value.childId,
            totalUsedMinutesToday = totalUsedMinutesToday,
            alertIntervalMinutes = _policy.value.alertIntervalMinutes,
            lastNotifiedMilestoneStep = lastNotifiedMilestoneStep,
            currentPlaceName = currentPlaceName,
            recentAppUsages = activeStayApps
        )

        lastNotifiedMilestoneStep = check.newMilestoneStep
        check.alert?.let { emitAlert(it) }
        return check.alert
    }

    override fun emitAlert(alert: ParentAlert) {
        _alerts.update { current -> listOf(alert) + current }
    }

    override fun acknowledgeAlert(alertId: String) {
        _alerts.update { current ->
            current.map { if (it.alertId == alertId) it.copy(isAcknowledged = true) else it }
        }
    }

    override fun updateDeviceHealth(transform: (DeviceHealth) -> DeviceHealth) {
        _deviceHealth.update(transform)
    }

    private fun persistPolicySnapshot() {
        val file = storageFile ?: return
        runCatching {
            val p = _policy.value
            val root = JSONObject().apply {
                put("allowedStartHour", p.allowedStartHour)
                put("allowedStartMinute", p.allowedStartMinute)
                put("allowedEndHour", p.allowedEndHour)
                put("allowedEndMinute", p.allowedEndMinute)
                put("dailyLimitMinutes", p.dailyLimitMinutes ?: -1)
                put("bonusMinutesToday", p.bonusMinutesToday)
                put("isImmediateLocked", p.isImmediateLocked)
                put("alertIntervalMinutes", p.alertIntervalMinutes)
                put("parentPin", p.parentPin)
            }
            file.parentFile?.mkdirs()
            file.writeText(root.toString())
        }
    }

    private fun loadPersistedPolicyIfExists() {
        val file = storageFile ?: return
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText())
            val dailyLimit = root.optInt("dailyLimitMinutes", 120).takeIf { it > 0 }
            _policy.update {
                it.copy(
                    allowedStartHour = root.optInt("allowedStartHour", it.allowedStartHour),
                    allowedStartMinute = root.optInt("allowedStartMinute", it.allowedStartMinute),
                    allowedEndHour = root.optInt("allowedEndHour", it.allowedEndHour),
                    allowedEndMinute = root.optInt("allowedEndMinute", it.allowedEndMinute),
                    dailyLimitMinutes = dailyLimit,
                    bonusMinutesToday = root.optInt("bonusMinutesToday", it.bonusMinutesToday),
                    isImmediateLocked = root.optBoolean("isImmediateLocked", it.isImmediateLocked),
                    alertIntervalMinutes = root.optInt("alertIntervalMinutes", it.alertIntervalMinutes),
                    parentPin = root.optString("parentPin", it.parentPin)
                )
            }
        }
    }
}

object FamilyRepositoryProvider {
    @Volatile
    private var instance: FamilySyncRepository? = null

    fun getInstance(context: Context? = null): FamilySyncRepository {
        return instance ?: synchronized(this) {
            instance ?: LocalFamilySyncRepository(
                storageFile = context?.filesDir?.let { File(it, "familylink_policy_state.json") }
            ).also { instance = it }
        }
    }
}
