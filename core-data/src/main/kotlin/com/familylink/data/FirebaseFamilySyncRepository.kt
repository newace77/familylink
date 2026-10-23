package com.familylink.data

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
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.util.Log
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

/**
 * Firestore 실시간 리스너 기반 FamilySyncRepository 구현체.
 *
 * - 트리: families/{familyCode} 문서(policy·deviceHealth·safeZones 필드) + alerts/webLogs/staySessions 컬렉션
 * - 모든 문서는 {"data": "<ModelJson 직렬화 JSON>"} 형태 — 웹 대시보드와 동일 계약
 * - 읽기는 addSnapshotListener(실시간), 쓰기는 옵티머스틱 로컬 반영 후 Firestore set
 * - 병합 경계: 본 클래스는 기존 값 방어(파싱 실패 시 무변경) + SyncListMerger dedup
 */
class FirebaseFamilySyncRepository(
    private val db: FirebaseFirestore,
    private val pairingStore: FamilyPairingStore
) : FamilySyncRepository {

    companion object {
        private const val TAG = "FamilySyncRepo"
    }

    private val seedPolicy = DemoScenarioSeed.defaultPolicy()

    private val _policy = MutableStateFlow(seedPolicy)
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

    private val _pairingState = MutableStateFlow(
        if (pairingStore.savedCode() != null) PairingState.WAITING else PairingState.NOT_PAIRED
    )
    override val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()

    private val _lastCreatedCode = MutableStateFlow<String?>(null)

    /** 부모 UI가 방금 생성한 코드 표시용. createFamilyCode 성공 시 설정. */
    override val lastCreatedCode: StateFlow<String?> = _lastCreatedCode.asStateFlow()

    private var familyCode: String? = pairingStore.savedCode()
    private var syncedCode: String? = null
    private var lastNotifiedMilestoneStep: Int =
        _deviceHealth.value.totalUsedMinutesToday / _policy.value.alertIntervalMinutes.coerceAtLeast(1)

    private val listeners = mutableListOf<ListenerRegistration>()

    /**
     * Issue 5 견고성: 정책·안심존 쓰기의 ack 확인/재시도를 담당하는 독립 스코프.
     * UI 쓰레드를 막지 않으면서 실패한 set을 재시도한다.
     */
    private val writesScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val echoGuard = FieldEchoGuard()

    init {
        // 저장된 코드가 있으면(부모가 코드 생성 후 재시작, 또는 자녀가 연동 후 재시작)
        // 리스너를 즉시 다는데 — 그렇지 않으면 부모 앱은 영원히 리스너 없이 씨앗 데이터만 본다.
        pairingStore.savedCode()?.let { startSync(it) }
    }

    // ---------- 라이프사이클 ----------

    /** 페어링 코드가 확정된 뒤 호출: families/{code} 실시간 구독을 연다. */
    fun startSync(familyCode: String) {
        if (syncedCode == familyCode && listeners.isNotEmpty()) return
        syncedCode = familyCode
        this.familyCode = familyCode
        pairingStore.save(familyCode)
        // Issue 6: 실제 가족 구독이 시작되면 데모 시드 알림은 더 이상 표시하지 않는다.
        _alerts.update { AlertListOps.removeDemoAlerts(it) }
        val rootPath = "${FirestoreSchema.FAMILIES}/$familyCode"

        listeners += db.document(rootPath).addSnapshotListener { snapshot, _ ->
            val data = snapshot?.takeIf { it.exists() }?.data ?: return@addSnapshotListener
            applyRootDocument(data)
        }

        listeners += listenCollection(rootPath + "/" + FirestoreSchema.ALERTS, alertsLimit = 100) { snapshots ->
            val items = snapshots.mapNotNull { doc ->
                runCatching {
                    ModelJson.alertFromJson(JSONObject(doc.getString(FirestoreSchema.DATA)))
                }.getOrNull()
            }
            // Issue 6: 병합 값에 데모 시드가 남아 있으면 함께 제거하고 최신순을 유지한다.
            _alerts.update {
                AlertListOps.sortedNewestFirst(
                    AlertListOps.removeDemoAlerts(SyncListMerger.merge(it, items) { a -> a.alertId })
                )
            }
        }
        listeners += listenCollection(rootPath + "/" + FirestoreSchema.WEB_LOGS, alertsLimit = 200) { snapshots ->
            val items = snapshots.mapNotNull { doc ->
                runCatching {
                    ModelJson.webLogFromJson(JSONObject(doc.getString(FirestoreSchema.DATA)))
                }.getOrNull()
            }
            _webLogs.update { SyncListMerger.merge(it, items) { l -> l.id } }
        }
        listeners += listenCollection(rootPath + "/" + FirestoreSchema.STAY_SESSIONS, alertsLimit = 200) { snapshots ->
            val items = snapshots.mapNotNull { doc ->
                runCatching {
                    ModelJson.staySessionFromJson(JSONObject(doc.getString(FirestoreSchema.DATA)))
                }.getOrNull()
            }
            _staySessions.update { SyncListMerger.merge(it, items) { s -> s.sessionId } }
        }
    }

    private fun listenCollection(
        path: String,
        alertsLimit: Int,
        apply: (List<com.google.firebase.firestore.DocumentSnapshot>) -> Unit
    ): ListenerRegistration =
        db.collection(path)
            .orderBy(FirestoreSchema.TIMESTAMP_MILLIS, Query.Direction.DESCENDING)
            .limit(alertsLimit.toLong())
            .addSnapshotListener { snapshot, _ ->
                val docs = snapshot?.documents ?: return@addSnapshotListener
                apply(docs)
            }

    private fun applyRootDocument(data: Map<String, Any?>) {
        // 기존 값 방어: 파싱 실패 시 해당 StateFlow 무변경(Review Focus 2)
        runCatching {
            data[FirestoreSchema.POLICY] as? String
        }.getOrNull()?.let { raw ->
            if (!echoGuard.shouldApplyServerUpdate(FIELD_POLICY, raw)) {
                Log.w(TAG, "skip stale policy snapshot while parent write is pending")
            } else {
                runCatching { ModelJson.policyFromJson(JSONObject(raw)) }.onSuccess { parsed ->
                    _policy.value = parsed
                }
            }
        }
        runCatching {
            data[FirestoreSchema.DEVICE_HEALTH] as? String
        }.getOrNull()?.let { raw ->
            runCatching { ModelJson.deviceHealthFromJson(JSONObject(raw)) }.onSuccess { parsed ->
                _deviceHealth.value = parsed
            }
        }
        runCatching {
            data[FirestoreSchema.SAFE_ZONES] as? String
        }.getOrNull()?.let { raw ->
            if (!echoGuard.shouldApplyServerUpdate(FIELD_SAFE_ZONES, raw)) {
                Log.w(TAG, "skip stale safeZones snapshot while parent write is pending")
            } else {
                runCatching { ModelJson.safeZonesFromJson(org.json.JSONArray(raw)) }.onSuccess { parsed ->
                    _safeZones.value = parsed
                }
            }
        }
        val paired = data[FirestoreSchema.PAIRED] as? Map<*, *>
        if (paired?.get(FirestoreSchema.CHILD_LINKED) == true) {
            _pairingState.value = PairingState.LINKED
        }
    }

    // ---------- 정책 쓰기(단일 작성자: 부모) ----------

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
        writePolicy()
    }

    override fun updateDailyLimitMinutes(limitMinutes: Int?) {
        _policy.update {
            it.copy(
                dailyLimitMinutes = limitMinutes?.coerceIn(15, 720),
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        }
        writePolicy()
    }

    override fun updateAlertIntervalMinutes(intervalMinutes: Int) {
        val safeInterval = intervalMinutes.coerceIn(5, 180)
        _policy.update {
            it.copy(alertIntervalMinutes = safeInterval, updatedAtEpochMillis = System.currentTimeMillis())
        }
        lastNotifiedMilestoneStep = _deviceHealth.value.totalUsedMinutesToday / safeInterval
        writePolicy()
    }

    override fun setImmediateLock(locked: Boolean) {
        _policy.update {
            it.copy(isImmediateLocked = locked, updatedAtEpochMillis = System.currentTimeMillis())
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
        writePolicy()
    }

    override fun grantBonusMinutes(additionalMinutes: Int) {
        _policy.update {
            it.copy(
                bonusMinutesToday = (it.bonusMinutesToday + additionalMinutes).coerceAtLeast(0),
                isImmediateLocked = false,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        }
        writePolicy()
    }

    override fun updateParentPin(newPin: String) {
        if (newPin.length in 4..6 && newPin.all { it.isDigit() }) {
            _policy.update { it.copy(parentPin = newPin, updatedAtEpochMillis = System.currentTimeMillis()) }
            writePolicy()
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
        writePolicy()
    }

    private fun writePolicy() {
        val code = familyCode ?: return
        val json = ModelJson.policyToJson(_policy.value).toString()
        echoGuard.onLocalWriteQueued(FIELD_POLICY, json)
        writeRootFieldWithRetry(code, FIELD_POLICY, json)
    }

    // ---------- 자녀 측 기록 ----------

    override fun updateDeviceHealth(transform: (DeviceHealth) -> DeviceHealth) {
        var newHealth = _deviceHealth.value
        _deviceHealth.update { current ->
            newHealth = transform(current)
            newHealth
        }
        val code = familyCode ?: return
        db.document("${FirestoreSchema.FAMILIES}/$code")
            .set(
                mapOf(FirestoreSchema.DEVICE_HEALTH to ModelJson.deviceHealthToJson(newHealth).toString()),
                SetOptions.merge()
            )
    }

    override fun addOrUpdateSafeZone(zone: SafeZone) {
        _safeZones.update { current ->
            val without = current.filterNot { it.zoneId == zone.zoneId }
            listOf(zone) + without
        }
        writeSafeZones()
    }

    override fun removeSafeZone(zoneId: String) {
        _safeZones.update { current -> current.filterNot { it.zoneId == zoneId } }
        writeSafeZones()
    }

    private fun writeSafeZones() {
        val code = familyCode ?: return
        val json = ModelJson.safeZonesToJson(_safeZones.value).toString()
        echoGuard.onLocalWriteQueued(FIELD_SAFE_ZONES, json)
        writeRootFieldWithRetry(code, FIELD_SAFE_ZONES, json)
    }

    /**
     * Issue 5 견고성: fire-and-forget set 대신 ack + 지수 백오프 재시도(0.5/1/2s).
     * 마지막 시도까지 실패하면 로그만 남기고 로컬 의도(echoGuard pending)는 유지해
     * 리스너의 stale 스냅샷이 UI를 되돌리지 않게 한다.
     */
    private fun writeRootFieldWithRetry(code: String, fieldKey: String, json: String) {
        writesScope.launch {
            val backoffMillis = longArrayOf(0, 500, 1_000, 2_000)
            var success = false
            for (attempt in backoffMillis.indices) {
                if (backoffMillis[attempt] > 0) delay(backoffMillis[attempt])
                val task = db.document("${FirestoreSchema.FAMILIES}/$code")
                    .set(mapOf(fieldKey to json), SetOptions.merge())
                val result = runCatching { task.awaitTask() }
                if (result.isSuccess) {
                    success = true
                    break
                }
                Log.w(TAG, "write $fieldKey attempt=${attempt + 1} failed: ${result.exceptionOrNull()?.message}")
            }
            echoGuard.onLocalWriteResult(fieldKey, success)
            if (!success) {
                Log.e(TAG, "write $fieldKey permanently failed after retries; keeping local intent")
            }
        }
    }

    override fun recordWebContentLog(log: WebContentLog) {
        _webLogs.update { current -> SyncListMerger.merge(current, listOf(log)) { it.id } }
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
        upsertCollectionItem(FirestoreSchema.WEB_LOGS, log.id, ModelJson.webLogToJson(log))
    }

    override fun recordStayPointSession(session: StayPointSession) {
        _staySessions.update { current ->
            SyncListMerger.merge(current, listOf(session)) { it.sessionId }
        }
        upsertCollectionItem(FirestoreSchema.STAY_SESSIONS, session.sessionId, ModelJson.staySessionToJson(session))
    }

    override fun emitAlert(alert: ParentAlert) {
        _alerts.update { current ->
            AlertListOps.sortedNewestFirst(AlertListOps.removeDemoAlerts(SyncListMerger.merge(current, listOf(alert)) { it.alertId }))
        }
        upsertCollectionItem(FirestoreSchema.ALERTS, alert.alertId, ModelJson.alertToJson(alert))
    }

    override fun acknowledgeAlert(alertId: String) {
        _alerts.update { current ->
            current.map { if (it.alertId == alertId) it.copy(isAcknowledged = true) else it }
        }
        val code = familyCode ?: return
        // data 필드 JSON의 isAcknowledged를 갱신해야 다른 기기·웹 대시보드에도 반영된다.
        val updatedJson = _alerts.value.firstOrNull { it.alertId == alertId }
            ?.let { ModelJson.alertToJson(it).toString() }
            ?: return
        db.document("${FirestoreSchema.FAMILIES}/$code/${FirestoreSchema.ALERTS}/$alertId")
            .update(mapOf(FirestoreSchema.DATA to updatedJson))
    }

    /** 결정적 문서 id = 데이터 id: 리스너 중복 제거와 acknowledge 경로를 단순화한다. */
    private fun upsertCollectionItem(collectionKey: String, itemId: String, json: JSONObject) {
        val code = familyCode ?: return
        db.document("${FirestoreSchema.FAMILIES}/$code/$collectionKey/$itemId")
            .set(mapOf(FirestoreSchema.DATA to json.toString(), FirestoreSchema.TIMESTAMP_MILLIS to System.currentTimeMillis()))
    }

    override fun recordUsageProgress(
        totalUsedMinutesToday: Int,
        currentPlaceName: String,
        currentAppName: String,
        appUsagesToday: Map<String, Int>
    ): ParentAlert? {
        updateDeviceHealth { current ->
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

    // ---------- 페어링 ----------

    override suspend fun createFamilyCode(): String {
        val code = Random.nextInt(100_000, 1_000_000).toString()
        val now = System.currentTimeMillis()
        val seed = mapOf(
            FirestoreSchema.POLICY to ModelJson.policyToJson(seedPolicy).toString(),
            FirestoreSchema.SAFE_ZONES to ModelJson.safeZonesToJson(DemoScenarioSeed.defaultSafeZones()).toString(),
            FirestoreSchema.PAIRED to emptyMap<String, Any>(),
            "createdAtMillis" to now
        )
        val set = runCatching {
            db.document("${FirestoreSchema.FAMILIES}/$code").set(seed).awaitTask()
        }
        if (set.isFailure) {
            // Ruling: 쓰기 실패 시 코드 미확정 상태 유지. UI는 ""를 실패 신호로 표시한다.
            return ""
        }
        pairingStore.save(code)
        familyCode = code
        _lastCreatedCode.value = code
        _pairingState.value = PairingState.WAITING
        // 부모가 스스로 리스너를 열어야 PAIRED/LINKED 전환과 정책·기록 실시간 반영이 시작된다.
        startSync(code)
        return code
    }

    override suspend fun linkFamilyCode(code: String): Boolean {
        val valid = code.length == 6 && code.all { it.isDigit() }
        if (!valid) return false
        val exists = runCatching {
            db.document("${FirestoreSchema.FAMILIES}/$code").get().awaitTask().exists()
        }.getOrDefault(false)
        // 문서 부재/읽기 실패는 잘못된 코드(Review Focus 1)와 동일하게 처리하고 상태를 불변으로 둔다.
        if (!exists) return false
        familyCode = code
        val linked = runCatching {
            db.document("${FirestoreSchema.FAMILIES}/$code")
                .set(
                    mapOf(
                        FirestoreSchema.PAIRED to mapOf(
                            FirestoreSchema.CHILD_LINKED to true,
                            "atMillis" to System.currentTimeMillis()
                        )
                    ),
                    SetOptions.merge()
                ).awaitTask()
        }.isSuccess
        if (!linked) {
            familyCode = pairingStore.savedCode()
            return false
        }
        pairingStore.save(code)
        _pairingState.value = PairingState.LINKED
        startSync(code)
        return true
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCoroutine { continuation ->
        addOnSuccessListener { value -> continuation.resume(value) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
    }
}
