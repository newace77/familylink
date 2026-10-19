package com.familylink.data

import com.familylink.model.AppAccessMode
import com.familylink.model.AppRule
import com.familylink.model.AppUsageSegment
import com.familylink.model.ControlPolicy
import com.familylink.model.ContentType
import com.familylink.model.DeviceHealth
import com.familylink.model.NetworkState
import com.familylink.model.ParentAlert
import com.familylink.model.RingerState
import com.familylink.model.SafeZone
import com.familylink.model.StayPointSession
import com.familylink.model.WebContentLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * Firestore 문서와 웹 대시보드가 공유하는 직렬화 계약.
 * 모든 문서는 {"data": "<json 문자열>"} 형태로 저장하는 대신,
 * 생산/소비 양쪽에서 이 객체를 통해서만 JSON 키를 알게 한다.
 *
 * fromJson은 전부 optXxx 기반이라 크래시하지 않는다.
 * 누락된 dailyLimitMinutes는 "제한 없음(null)"으로 복원한다.
 */
object ModelJson {

    // ---------- ControlPolicy ----------

    fun policyToJson(policy: ControlPolicy): JSONObject = JSONObject().apply {
        put("childId", policy.childId)
        put("childName", policy.childName)
        put("allowedStartHour", policy.allowedStartHour)
        put("allowedStartMinute", policy.allowedStartMinute)
        put("allowedEndHour", policy.allowedEndHour)
        put("allowedEndMinute", policy.allowedEndMinute)
        put("dailyLimitMinutes", policy.dailyLimitMinutes ?: -1)
        put("bonusMinutesToday", policy.bonusMinutesToday)
        put("isImmediateLocked", policy.isImmediateLocked)
        put("alertIntervalMinutes", policy.alertIntervalMinutes)
        put("parentPin", policy.parentPin)
        put("tamperProtectionEnabled", policy.tamperProtectionEnabled)
        put("updatedAtEpochMillis", policy.updatedAtEpochMillis)
        put("appRules", JSONArray().apply {
            policy.appRules.values.forEach { rule ->
                put(JSONObject().apply {
                    put("packageName", rule.packageName)
                    put("appName", rule.appName)
                    put("accessMode", rule.accessMode.name)
                    put("dailyQuotaMinutes", rule.dailyQuotaMinutes ?: -1)
                    put("category", rule.category)
                })
            }
        })
    }

    fun policyFromJson(json: JSONObject): ControlPolicy = ControlPolicy(
        childId = json.optString("childId", "child-1"),
        childName = json.optString("childName", "우리 아이"),
        allowedStartHour = json.optInt("allowedStartHour", 10),
        allowedStartMinute = json.optInt("allowedStartMinute", 0),
        allowedEndHour = json.optInt("allowedEndHour", 21),
        allowedEndMinute = json.optInt("allowedEndMinute", 0),
        dailyLimitMinutes = json.optInt("dailyLimitMinutes", -1).takeIf { it > 0 },
        bonusMinutesToday = json.optInt("bonusMinutesToday", 0),
        isImmediateLocked = json.optBoolean("isImmediateLocked", false),
        alertIntervalMinutes = json.optInt("alertIntervalMinutes", 30),
        parentPin = json.optString("parentPin", "0000"),
        tamperProtectionEnabled = json.optBoolean("tamperProtectionEnabled", true),
        updatedAtEpochMillis = json.optLong("updatedAtEpochMillis", System.currentTimeMillis()),
        appRules = run {
            val rules = mutableMapOf<String, AppRule>()
            val ruleArray = json.optJSONArray("appRules") ?: JSONArray("[]")
            for (i in 0 until ruleArray.length()) {
                val row = ruleArray.getJSONObject(i)
                val rule = AppRule(
                    packageName = row.optString("packageName"),
                    appName = row.optString("appName", row.optString("packageName")),
                    accessMode = runCatching {
                        AppAccessMode.valueOf(row.optString("accessMode", "ALLOWED"))
                    }.getOrDefault(AppAccessMode.ALLOWED),
                    // dailyQuotaMinutes=0은 "명시적 0(차단)"이라 의미 있는 값이다.
                    // 누락(-1)만 null로 흡수하고 0은 그대로 둔다.
                    dailyQuotaMinutes = row.optInt("dailyQuotaMinutes", -1).takeIf { it >= 0 },
                    category = row.optString("category", "일반")
                )
                rules[rule.packageName] = rule
            }
            rules
        }
    )

    // ---------- DeviceHealth ----------

    fun deviceHealthToJson(health: DeviceHealth): JSONObject = JSONObject().apply {
        put("childId", health.childId)
        put("batteryPercent", health.batteryPercent)
        put("isCharging", health.isCharging)
        put("ringerState", health.ringerState.name)
        put("networkState", health.networkState.name)
        put("currentPlaceName", health.currentPlaceName)
        put("currentLatitude", health.currentLatitude)
        put("currentLongitude", health.currentLongitude)
        put("currentForegroundApp", health.currentForegroundApp)
        put("totalUsedMinutesToday", health.totalUsedMinutesToday)
        put("appUsedMinutesToday", JSONObject().apply {
            health.appUsedMinutesToday.forEach { (pkg, minutes) -> put(pkg, minutes) }
        })
        put("accessibilityEnabled", health.accessibilityEnabled)
        put("usageStatsEnabled", health.usageStatsEnabled)
        put("overlayPermissionEnabled", health.overlayPermissionEnabled)
        put("deviceAdminEnabled", health.deviceAdminEnabled)
        put("locationPermissionEnabled", health.locationPermissionEnabled)
        put("lastUpdatedMillis", health.lastUpdatedMillis)
    }

    fun deviceHealthFromJson(json: JSONObject): DeviceHealth = DeviceHealth(
        childId = json.optString("childId", "child-1"),
        batteryPercent = json.optInt("batteryPercent", 85),
        isCharging = json.optBoolean("isCharging", false),
        ringerState = runCatching {
            RingerState.valueOf(json.optString("ringerState", "NORMAL"))
        }.getOrDefault(RingerState.NORMAL),
        networkState = runCatching {
            NetworkState.valueOf(json.optString("networkState", "WIFI"))
        }.getOrDefault(NetworkState.WIFI),
        currentPlaceName = json.optString("currentPlaceName", "위치 미확인"),
        currentLatitude = json.optDouble("currentLatitude", 0.0),
        currentLongitude = json.optDouble("currentLongitude", 0.0),
        currentForegroundApp = json.optString("currentForegroundApp", ""),
        totalUsedMinutesToday = json.optInt("totalUsedMinutesToday", 0),
        appUsedMinutesToday = run {
            val map = mutableMapOf<String, Int>()
            val appObj = json.optJSONObject("appUsedMinutesToday") ?: JSONObject()
            appObj.keys().forEach { pkg -> map[pkg] = appObj.optInt(pkg, 0) }
            map
        },
        accessibilityEnabled = json.optBoolean("accessibilityEnabled", true),
        usageStatsEnabled = json.optBoolean("usageStatsEnabled", true),
        overlayPermissionEnabled = json.optBoolean("overlayPermissionEnabled", true),
        deviceAdminEnabled = json.optBoolean("deviceAdminEnabled", true),
        locationPermissionEnabled = json.optBoolean("locationPermissionEnabled", false),
        lastUpdatedMillis = json.optLong("lastUpdatedMillis", System.currentTimeMillis())
    )

    // ---------- SafeZone ----------

    fun safeZonesToJson(zones: List<SafeZone>): JSONArray = JSONArray().apply {
        zones.forEach { zone ->
            put(JSONObject().apply {
                put("zoneId", zone.zoneId)
                put("name", zone.name)
                put("latitude", zone.latitude)
                put("longitude", zone.longitude)
                put("radiusMeters", zone.radiusMeters)
                put("notifyOnEnter", zone.notifyOnEnter)
                put("notifyOnExit", zone.notifyOnExit)
            })
        }
    }

    fun safeZonesFromJson(array: JSONArray): List<SafeZone> {
        val zones = mutableListOf<SafeZone>()
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
            zones.add(
                SafeZone(
                    zoneId = row.optString("zoneId"),
                    name = row.optString("name"),
                    latitude = row.optDouble("latitude", 0.0),
                    longitude = row.optDouble("longitude", 0.0),
                    radiusMeters = row.optDouble("radiusMeters", 150.0),
                    notifyOnEnter = row.optBoolean("notifyOnEnter", true),
                    notifyOnExit = row.optBoolean("notifyOnExit", true)
                )
            )
        }
        return zones
    }

    // ---------- ParentAlert ----------

    fun alertToJson(alert: ParentAlert): JSONObject = JSONObject().apply {
        put("alertId", alert.alertId)
        put("childId", alert.childId)
        put("type", alert.type.name)
        put("title", alert.title)
        put("message", alert.message)
        put("timestampMillis", alert.timestampMillis)
        put("placeName", alert.placeName ?: JSONObject.NULL)
        put("milestoneMinutes", alert.milestoneMinutes ?: -1)
        put("isAcknowledged", alert.isAcknowledged)
    }

    fun alertFromJson(json: JSONObject): ParentAlert = ParentAlert(
        alertId = json.optString("alertId"),
        childId = json.optString("childId", "child-1"),
        type = runCatching {
            com.familylink.model.AlertType.valueOf(json.optString("type", "USAGE_MILESTONE"))
        }.getOrDefault(com.familylink.model.AlertType.USAGE_MILESTONE),
        title = json.optString("title"),
        message = json.optString("message"),
        timestampMillis = json.optLong("timestampMillis", System.currentTimeMillis()),
        // AOSP org.json은 JSONObject.NULL을 optString으로 꺼내면 "null" 문자열이 된다
        // (JSON.toString → String.valueOf). isNull 선검사 필수.
        placeName = if (json.isNull("placeName")) null else
            json.optString("placeName", "").takeIf { it.isNotBlank() },
        milestoneMinutes = json.optInt("milestoneMinutes", -1).takeIf { it > 0 },
        isAcknowledged = json.optBoolean("isAcknowledged", false)
    )

    // ---------- WebContentLog ----------

    fun webLogToJson(log: WebContentLog): JSONObject = JSONObject().apply {
        put("id", log.id)
        put("childId", log.childId)
        put("timestampMillis", log.timestampMillis)
        put("sourcePackage", log.sourcePackage)
        put("browserOrAppName", log.browserOrAppName)
        put("contentType", log.contentType.name)
        put("title", log.title)
        put("urlOrQuery", log.urlOrQuery)
        put("durationSeconds", log.durationSeconds)
        put("placeName", log.placeName ?: JSONObject.NULL)
        put("latitude", log.latitude ?: JSONObject.NULL)
        put("longitude", log.longitude ?: JSONObject.NULL)
    }

    fun webLogFromJson(json: JSONObject): WebContentLog = WebContentLog(
        id = json.optString("id"),
        childId = json.optString("childId", "child-1"),
        timestampMillis = json.optLong("timestampMillis", 0L),
        sourcePackage = json.optString("sourcePackage"),
        browserOrAppName = json.optString("browserOrAppName"),
        contentType = runCatching {
            ContentType.valueOf(json.optString("contentType", "WEB_PAGE"))
        }.getOrDefault(ContentType.WEB_PAGE),
        title = json.optString("title"),
        urlOrQuery = json.optString("urlOrQuery"),
        durationSeconds = json.optInt("durationSeconds", 0),
        placeName = if (json.isNull("placeName")) null else
            json.optString("placeName", "").takeIf { it.isNotBlank() },
        latitude = if (json.isNull("latitude")) null else json.optDouble("latitude"),
        longitude = if (json.isNull("longitude")) null else json.optDouble("longitude")
    )

    // ---------- StayPointSession ----------

    fun staySessionToJson(session: StayPointSession): JSONObject = JSONObject().apply {
        put("sessionId", session.sessionId)
        put("childId", session.childId)
        put("dateKey", session.dateKey)
        put("latitude", session.latitude)
        put("longitude", session.longitude)
        put("placeName", session.placeName)
        put("address", session.address)
        put("arrivedAtMillis", session.arrivedAtMillis)
        put("departedAtMillis", session.departedAtMillis)
        put("appUsages", JSONArray().apply {
            session.appUsages.forEach { usage ->
                put(JSONObject().apply {
                    put("packageName", usage.packageName)
                    put("appName", usage.appName)
                    put("usageMinutes", usage.usageMinutes)
                    put("usageSeconds", usage.usageSeconds)
                    put("category", usage.category)
                })
            }
        })
        put("webLogs", JSONArray().apply {
            session.webLogs.forEach { put(webLogToJson(it)) }
        })
    }

    fun staySessionFromJson(json: JSONObject): StayPointSession {
        val usages = mutableListOf<AppUsageSegment>()
        val usageArray = json.optJSONArray("appUsages") ?: JSONArray("[]")
        for (i in 0 until usageArray.length()) {
            val row = usageArray.getJSONObject(i)
            usages.add(
                AppUsageSegment(
                    packageName = row.optString("packageName"),
                    appName = row.optString("appName"),
                    usageMinutes = row.optInt("usageMinutes", 0),
                    usageSeconds = row.optInt("usageSeconds", 0),
                    category = row.optString("category", "일반")
                )
            )
        }
        val logs = mutableListOf<WebContentLog>()
        val logArray = json.optJSONArray("webLogs") ?: JSONArray("[]")
        for (i in 0 until logArray.length()) {
            logs.add(webLogFromJson(logArray.getJSONObject(i)))
        }
        return StayPointSession(
            sessionId = json.optString("sessionId"),
            childId = json.optString("childId", "child-1"),
            dateKey = json.optString("dateKey"),
            latitude = json.optDouble("latitude", 0.0),
            longitude = json.optDouble("longitude", 0.0),
            placeName = json.optString("placeName"),
            address = json.optString("address"),
            arrivedAtMillis = json.optLong("arrivedAtMillis", 0L),
            departedAtMillis = json.optLong("departedAtMillis", 0L),
            appUsages = usages,
            webLogs = logs
        )
    }
}
