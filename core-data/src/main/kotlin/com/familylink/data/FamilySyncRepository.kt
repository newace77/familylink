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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

/**
 * 부모 앱과 자녀 앱의 연동 진행 단계.
 * NOT_PAIRED: 아직 연동 없음 → WAITING: 부모가 코드 생성(자녀 입력 대기) → LINKED: 양쪽 연동 성공
 */
enum class PairingState { NOT_PAIRED, WAITING, LINKED }

/** 저장소 종류: FIREBASE = Firestore 실시간 동기화, LOCAL = 단일 기기 데모 */
enum class RepositoryKind { FIREBASE, LOCAL }

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

    /** 마지막으로 생성된 6자리 가족 코드 (createFamilyCode 성공 시 설정). LINKED 후에는 무의미. */
    val lastCreatedCode: StateFlow<String?>
}

class LocalFamilySyncRepository(
    private val storageFile: File? = null
) : FamilySyncRepository {

    private val _policy = MutableStateFlow(DemoScenarioSeed.defaultPolicy())
    override val policy: StateFlow<ControlPolicy> = _policy.asStateFlow()

    private val _deviceHealth = MutableStateFlow(DeviceHealth())
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

    private val _lastCreatedCode = MutableStateFlow<String?>(null)
    override val lastCreatedCode: StateFlow<String?> = _lastCreatedCode.asStateFlow()

    override suspend fun createFamilyCode(): String {
        val code = Random.nextInt(100_000, 1_000_000).toString()
        _lastCreatedCode.value = code
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
        persistStateSnapshot()
    }

    override fun updateDailyLimitMinutes(limitMinutes: Int?) {
        _policy.update {
            it.copy(
                dailyLimitMinutes = limitMinutes?.coerceIn(15, 720),
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        }
        persistStateSnapshot()
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
        persistStateSnapshot()
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
        persistStateSnapshot()
    }

    override fun grantBonusMinutes(additionalMinutes: Int) {
        _policy.update {
            it.copy(
                bonusMinutesToday = (it.bonusMinutesToday + additionalMinutes).coerceAtLeast(0),
                isImmediateLocked = false,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        }
        persistStateSnapshot()
    }

    override fun updateParentPin(newPin: String) {
        if (newPin.length in 4..6 && newPin.all { it.isDigit() }) {
            _policy.update {
                it.copy(parentPin = newPin, updatedAtEpochMillis = System.currentTimeMillis())
            }
            persistStateSnapshot()
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
        persistStateSnapshot()
    }

    override fun addOrUpdateSafeZone(zone: SafeZone) {
        _safeZones.update { current ->
            val without = current.filterNot { it.zoneId == zone.zoneId }
            listOf(zone) + without
        }
        persistStateSnapshot()
    }

    override fun removeSafeZone(zoneId: String) {
        _safeZones.update { current -> current.filterNot { it.zoneId == zoneId } }
        persistStateSnapshot()
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
        persistStateSnapshot()
    }

    override fun recordStayPointSession(session: StayPointSession) {
        _staySessions.update { current ->
            listOf(session) + current.filterNot { it.sessionId == session.sessionId }
        }
        persistStateSnapshot()
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
        persistStateSnapshot()
    }

    override fun acknowledgeAlert(alertId: String) {
        _alerts.update { current ->
            current.map { if (it.alertId == alertId) it.copy(isAcknowledged = true) else it }
        }
        persistStateSnapshot()
    }

    override fun updateDeviceHealth(transform: (DeviceHealth) -> DeviceHealth) {
        _deviceHealth.update(transform)
        persistStateSnapshot()
    }

    /**
     * 전체 상태가 영구파일로 라운드트립된다 (로드맵 4).
     * policy/deviceHealth/staySessions/webLogs/alerts/safeZones — ModelJson 계약 재사용.
     * 레거시 파일(policy 필드만 있는 JSON)도 로드 시 그대로 소화한다.
     */
    fun persistStateSnapshot() {
        val file = storageFile ?: return
        runCatching {
            val root = JSONObject().apply {
                put("version", 2)
                put("policy", ModelJson.policyToJson(_policy.value))
                put("deviceHealth", ModelJson.deviceHealthToJson(_deviceHealth.value))
                put("staySessions", ModelJson.staySessionsToJson(_staySessions.value))
                put("webLogs", ModelJson.webLogsToJson(_webLogs.value))
                put("alerts", ModelJson.alertsToJson(_alerts.value))
                put("safeZones", ModelJson.safeZonesToJson(_safeZones.value))
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
            val policyJson = root.optJSONObject("policy")
            if (policyJson != null) {
                // v2 전체 상태 복원
                _policy.value = ModelJson.policyFromJson(policyJson)
                _deviceHealth.value = ModelJson.deviceHealthFromJson(root.optJSONObject("deviceHealth") ?: JSONObject())
                _staySessions.value = ModelJson.staySessionsFromJson(root.optJSONArray("staySessions") ?: JSONArray())
                _webLogs.value = ModelJson.webLogsFromJson(root.optJSONArray("webLogs") ?: JSONArray())
                _alerts.value = ModelJson.alertsFromJson(root.optJSONArray("alerts") ?: JSONArray())
                _safeZones.value = ModelJson.safeZonesFromJson(root.optJSONArray("safeZones") ?: JSONArray())
                return
            }
            // 레거시(정책 단독 스냅샷) 마이그레이션
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

    /**
     * Firebase 초기화 여부만으로 저장소 종류를 결정하는 순수 함수 (JVM 테스트 대상).
     * 페어링 유무는 선택 기준이 아니다 — 초기화만 되어 있으면 Firebase repo가
     * NOT_PAIRED부터 이어받는다 (Review Focus 5).
     */
    fun selectRepositoryKind(firebaseInitialized: Boolean): RepositoryKind =
        if (firebaseInitialized) RepositoryKind.FIREBASE else RepositoryKind.LOCAL

    fun getInstance(context: Context? = null): FamilySyncRepository {
        return instance ?: synchronized(this) {
            instance ?: run {
                val firebaseInitialized = context != null && runCatching {
                    com.google.firebase.FirebaseApp.getApps(context).isNotEmpty()
                }.getOrDefault(false)
                val repository = when (selectRepositoryKind(firebaseInitialized)) {
                    RepositoryKind.FIREBASE -> FirebaseFamilySyncRepository(
                        db = com.google.firebase.firestore.FirebaseFirestore.getInstance(),
                        pairingStore = FamilyPairingStore(requireNotNull(context) { "Firebase 경로에는 Context가 필요하다" })
                    )
                    RepositoryKind.LOCAL -> LocalFamilySyncRepository(
                        storageFile = context?.filesDir?.let { File(it, "familylink_policy_state.json") }
                    )
                }
                repository.also { instance = it }
            }
        }
    }
}
