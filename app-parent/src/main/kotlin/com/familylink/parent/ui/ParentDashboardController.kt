package com.familylink.parent.ui

import com.familylink.data.FamilySyncRepository
import com.familylink.data.PairingState
import com.familylink.model.AppAccessMode
import com.familylink.model.ContentType
import com.familylink.model.ControlPolicy
import com.familylink.model.DeviceHealth
import com.familylink.model.ParentAlert
import com.familylink.model.SafeZone
import com.familylink.model.StayPointSession
import com.familylink.model.WebContentLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ParentDashboardController(
    private val repository: FamilySyncRepository
) {
    val policy: StateFlow<ControlPolicy> = repository.policy
    val deviceHealth: StateFlow<DeviceHealth> = repository.deviceHealth
    val staySessions: StateFlow<List<StayPointSession>> = repository.staySessions
    val webLogs: StateFlow<List<WebContentLog>> = repository.webLogs
    val alerts: StateFlow<List<ParentAlert>> = repository.alerts
    val safeZones: StateFlow<List<SafeZone>> = repository.safeZones
    val pairingState: StateFlow<PairingState> = repository.pairingState
    val lastCreatedCode: StateFlow<String?> = repository.lastCreatedCode

    suspend fun createFamilyCode(): String = repository.createFamilyCode()

    private val _selectedStaySession = MutableStateFlow(repository.staySessions.value.firstOrNull())
    val selectedStaySession: StateFlow<StayPointSession?> = _selectedStaySession.asStateFlow()

    fun selectStaySession(session: StayPointSession) {
        _selectedStaySession.value = session
    }

    fun filterWebLogs(contentType: ContentType?, searchQuery: String): List<WebContentLog> {
        val query = searchQuery.trim()
        return repository.webLogs.value.filter { log ->
            val matchesType = contentType == null || log.contentType == contentType
            val matchesQuery = query.isBlank() ||
                log.title.contains(query, ignoreCase = true) ||
                log.urlOrQuery.contains(query, ignoreCase = true) ||
                (log.placeName?.contains(query, ignoreCase = true) == true)
            matchesType && matchesQuery
        }
    }

    fun updateAllowedTimeWindow(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        repository.updateAllowedTimeWindow(startHour, startMinute, endHour, endMinute)
    }

    fun updateDailyLimitMinutes(limitMinutes: Int?) {
        repository.updateDailyLimitMinutes(limitMinutes)
    }

    fun updateAlertIntervalMinutes(intervalMinutes: Int) {
        repository.updateAlertIntervalMinutes(intervalMinutes)
    }

    fun toggleInstantLock(locked: Boolean) {
        repository.setImmediateLock(locked)
    }

    fun grantBonusTime(minutes: Int) {
        repository.grantBonusMinutes(minutes)
    }

    fun updateParentPin(newPin: String) {
        repository.updateParentPin(newPin)
    }

    fun setAppAccessMode(packageName: String, mode: AppAccessMode, dailyQuotaMinutes: Int? = null) {
        repository.updateAppAccessMode(packageName, mode, dailyQuotaMinutes)
    }

    fun addSafeZone(name: String, latitude: Double, longitude: Double, radiusMeters: Double) {
        val zone = SafeZone(
            zoneId = "zone-${System.currentTimeMillis()}",
            name = name,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters
        )
        repository.addOrUpdateSafeZone(zone)
    }

    fun removeSafeZone(zoneId: String) {
        repository.removeSafeZone(zoneId)
    }

    fun acknowledgeAlert(alertId: String) {
        repository.acknowledgeAlert(alertId)
    }

    fun simulateNextUsageMilestone(): ParentAlert? {
        val interval = repository.policy.value.alertIntervalMinutes.coerceAtLeast(1)
        val currentUsed = repository.deviceHealth.value.totalUsedMinutesToday
        val nextStepMinutes = ((currentUsed / interval) + 1) * interval
        return repository.recordUsageProgress(
            totalUsedMinutesToday = nextStepMinutes,
            currentPlaceName = repository.deviceHealth.value.currentPlaceName,
            currentAppName = repository.deviceHealth.value.currentForegroundApp
        )
    }
}
