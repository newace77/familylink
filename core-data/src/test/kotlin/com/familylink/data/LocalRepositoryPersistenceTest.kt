package com.familylink.data

import com.familylink.model.AlertType
import com.familylink.model.ContentType
import com.familylink.model.ParentAlert
import com.familylink.model.SafeZone
import com.familylink.model.StayPointSession
import com.familylink.model.WebContentLog
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.file.Files

class LocalRepositoryPersistenceTest {

    @Test
    fun `repository state round trips through storage file`() = runTest {
        val file = Files.createTempFile("familylink-state-", ".json").toFile()
        val first = LocalFamilySyncRepository(storageFile = file)

        first.updateAlertIntervalMinutes(45)
        first.addOrUpdateSafeZone(
            SafeZone("zone-t", "테스트 학원", 37.4981, 127.0562, radiusMeters = 120.0)
        )
        first.recordStayPointSession(
            StayPointSession(
                sessionId = "stay-t",
                childId = "child-1",
                dateKey = "2026-10-19",
                latitude = 37.4994,
                longitude = 127.0334,
                placeName = "GS25",
                address = "역삼로 123",
                arrivedAtMillis = 500L,
                departedAtMillis = 0L
            )
        )
        first.recordWebContentLog(
            WebContentLog(
                id = "w1",
                timestampMillis = 1_000L,
                sourcePackage = "com.android.chrome",
                browserOrAppName = "Chrome",
                contentType = ContentType.WEB_PAGE,
                title = "가정통신문",
                urlOrQuery = "https://example.com"
            )
        )
        first.emitAlert(
            ParentAlert(
                alertId = "a1",
                childId = "child-1",
                type = AlertType.BONUS_TIME_REQUEST,
                title = "제목",
                message = "본문"
            )
        )
        first.updateDeviceHealth { it.copy(currentPlaceName = "GS25", accessibilityEnabled = false) }

        val second = LocalFamilySyncRepository(storageFile = file)

        assertThat(second.policy.value.alertIntervalMinutes).isEqualTo(45)
        assertThat(second.safeZones.value.map { it.zoneId }).contains("zone-t")
        assertThat(second.staySessions.value.map { it.sessionId }).contains("stay-t")
        assertThat(second.webLogs.value.map { it.id }).contains("w1")
        assertThat(second.alerts.value.map { it.alertId }).contains("a1")
        assertThat(second.deviceHealth.value.currentPlaceName).isEqualTo("GS25")
        assertThat(second.deviceHealth.value.accessibilityEnabled).isFalse()
    }

    @Test
    fun `legacy policy-only snapshot still loads`() = runTest {
        val file = Files.createTempFile("familylink-legacy-", ".json").toFile()
        file.writeText("""{"allowedStartHour":8,"allowedEndHour":19,"parentPin":"1234"}""")
        val repo = LocalFamilySyncRepository(storageFile = file)
        assertThat(repo.policy.value.alertIntervalMinutes).isEqualTo(30)
        assertThat(repo.policy.value.parentPin).isEqualTo("1234")
        assertThat(repo.policy.value.allowedStartHour).isEqualTo(8)
    }
}
