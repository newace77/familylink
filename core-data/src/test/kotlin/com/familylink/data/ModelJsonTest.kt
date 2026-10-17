package com.familylink.data

import com.familylink.model.AlertType
import com.familylink.model.ControlPolicy
import com.familylink.model.ContentType
import com.familylink.model.DeviceHealth
import com.familylink.model.ParentAlert
import com.familylink.model.WebContentLog
import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

class ModelJsonTest {

    @Test
    fun `policy round-trips all scalar and appRules fields`() {
        val policy = DemoScenarioSeed.defaultPolicy()
        val json = ModelJson.policyToJson(policy)
        assertThat(ModelJson.policyFromJson(json)).isEqualTo(policy)
    }

    @Test
    fun `deviceHealth round-trips all fields`() {
        val health = DemoScenarioSeed.defaultDeviceHealth()
        val json = ModelJson.deviceHealthToJson(health)
        assertThat(ModelJson.deviceHealthFromJson(json)).isEqualTo(health)
    }

    @Test
    fun `safeZones round-trips radiusMeters decimals`() {
        val zones = DemoScenarioSeed.defaultSafeZones()
        val json = ModelJson.safeZonesToJson(zones)
        assertThat(ModelJson.safeZonesFromJson(json)).isEqualTo(zones)
    }

    @Test
    fun `alert round-trips optional placeName null`() {
        val alert = ParentAlert(
            alertId = "alert-1",
            type = AlertType.BONUS_TIME_REQUEST,
            title = "제목",
            message = "본문",
            placeName = null,
            milestoneMinutes = null
        )
        val json = ModelJson.alertToJson(alert)
        assertThat(ModelJson.alertFromJson(json)).isEqualTo(alert)
    }

    @Test
    fun `webLog round-trips with null placeName`() {
        val log = WebContentLog(
            id = "abc",
            timestampMillis = 1_700_000_000_000L,
            sourcePackage = "com.android.chrome",
            browserOrAppName = "Chrome",
            contentType = ContentType.WEB_PAGE,
            title = "제목",
            urlOrQuery = "https://example.com",
            durationSeconds = 90,
            placeName = null,
            latitude = null,
            longitude = null
        )
        val json = ModelJson.webLogToJson(log)
        assertThat(ModelJson.webLogFromJson(json)).isEqualTo(log)
    }

    @Test
    fun `staySession round-trips nested appUsages and webLogs`() {
        val log = WebContentLog(
            id = "abc",
            timestampMillis = 1_700_000_000_000L,
            sourcePackage = "com.android.chrome",
            browserOrAppName = "Chrome",
            contentType = ContentType.WEB_PAGE,
            title = "제목",
            urlOrQuery = "https://example.com"
        )
        val session = DemoScenarioSeed.defaultStaySessions().first().copy(
            webLogs = listOf(log)
        )
        val json = ModelJson.staySessionToJson(session)
        assertThat(ModelJson.staySessionFromJson(json)).isEqualTo(session)
    }

    @Test
    fun `policyFromJson on empty JSONObject returns safe defaults`() {
        // 모델 기본값이되, 누락 dailyLimitMinutes는 "제한 없음(null)"으로 복원한다.
        val expected = ControlPolicy(dailyLimitMinutes = null)
        val parsed = ModelJson.policyFromJson(JSONObject("{}"))
        assertThat(parsed).isEqualTo(expected)
        assertThat(parsed.childId).isEqualTo("child-1")
        assertThat(parsed.dailyLimitMinutes).isNull()
    }

    @Test
    fun `deviceHealthFromJson on empty JSONObject returns neutral defaults`() {
        // Ruling: 모델 기본값에는 데모 장소명이 박혀 있으므로(역삼초 앞 GS25),
        // 누락 필드 복원은 데모 데이터가 아닌 중립 기본값을 사용한다.
        val parsed = ModelJson.deviceHealthFromJson(JSONObject("{}"))
        val expected = DeviceHealth(
            childId = "child-1",
            batteryPercent = 85,
            isCharging = false,
            ringerState = com.familylink.model.RingerState.NORMAL,
            networkState = com.familylink.model.NetworkState.WIFI,
            currentPlaceName = "알 수 없는 위치",
            currentLatitude = 0.0,
            currentLongitude = 0.0,
            currentForegroundApp = "",
            totalUsedMinutesToday = 0,
            lastUpdatedMillis = parsed.lastUpdatedMillis
        )
        assertThat(parsed).isEqualTo(expected)
    }

    @Test
    fun `safeZonesFromJson on empty JSONArray returns empty list`() {
        assertThat(ModelJson.safeZonesFromJson(JSONArray("[]"))).isEmpty()
    }
}
