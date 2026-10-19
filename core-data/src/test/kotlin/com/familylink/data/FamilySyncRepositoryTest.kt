package com.familylink.data

import com.familylink.model.AppAccessMode
import com.familylink.model.ContentType
import com.familylink.model.WebContentLog
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FamilySyncRepositoryTest {

    @Test
    fun `PlaceNameResolver formats Korean school and convenience store POI from Kakao Local JSON`() {
        val sampleCategoryJson = """
            {
              "documents": [
                {
                  "place_name": "GS25 역삼초교점",
                  "category_group_name": "편의점",
                  "address_name": "서울 강남구 역삼동 712-1",
                  "road_address_name": "서울 강남구 역삼로 123",
                  "distance": "12"
                },
                {
                  "place_name": "역삼초등학교",
                  "category_group_name": "학교",
                  "address_name": "서울 강남구 역삼동 710",
                  "road_address_name": "서울 강남구 역삼로 120",
                  "distance": "45"
                }
              ]
            }
        """.trimIndent()

        val resolved = PlaceNameResolver.parseKakaoDocumentsJson(
            json = sampleCategoryJson,
            fallbackLat = 37.4994,
            fallbackLng = 127.0334
        )
        assertThat(resolved.placeName).isEqualTo("역삼초등학교 앞 GS25 역삼초교점")
        assertThat(resolved.address).isEqualTo("서울 강남구 역삼로 123")
    }

    @Test
    fun `LocalFamilySyncRepository seeds realistic school convenience store scenario and updates policy and alerts`() = runTest {
        val repository = LocalFamilySyncRepository(storageFile = null)

        // Default seeded policy should have 10:00 ~ 21:00 and 30m alert interval
        val initialPolicy = repository.policy.value
        assertThat(initialPolicy.allowedStartHour).isEqualTo(10)
        assertThat(initialPolicy.allowedEndHour).isEqualTo(21)
        assertThat(initialPolicy.alertIntervalMinutes).isEqualTo(30)

        // Default seeded stay sessions should include school front convenience store with YouTube 30 mins
        val staySessions = repository.staySessions.value
        assertThat(staySessions).isNotEmpty()
        assertThat(staySessions.any {
            it.placeName.contains("편의점") && it.appUsages.any { app -> app.appName == "YouTube" && app.usageMinutes == 30 }
        }).isTrue()

        // Parent updates allowed hours, notification interval, and app access mode
        repository.updateAllowedTimeWindow(startHour = 9, startMinute = 30, endHour = 20, endMinute = 30)
        repository.updateAlertIntervalMinutes(15)
        repository.updateAppAccessMode("com.google.android.youtube", AppAccessMode.BLOCKED, dailyQuotaMinutes = 20)

        val updated = repository.policy.value
        assertThat(updated.allowedStartHour).isEqualTo(9)
        assertThat(updated.allowedStartMinute).isEqualTo(30)
        assertThat(updated.allowedEndHour).isEqualTo(20)
        assertThat(updated.alertIntervalMinutes).isEqualTo(15)
        assertThat(updated.appRules["com.google.android.youtube"]?.accessMode).isEqualTo(AppAccessMode.BLOCKED)

        // Child records new Chrome web log and crosses 90-minute usage milestone
        repository.recordWebContentLog(
            WebContentLog(
                id = "web-test-1",
                timestampMillis = 1_700_000_500_000L,
                sourcePackage = "com.android.chrome",
                browserOrAppName = "Chrome",
                contentType = ContentType.WEB_PAGE,
                title = "EBS 중학 수학 강의",
                urlOrQuery = "https://mid.ebs.co.kr/course/math",
                placeName = "대치동 수학학원"
            )
        )
        assertThat(repository.webLogs.value.first().title).isEqualTo("EBS 중학 수학 강의")

        repository.recordUsageProgress(
            totalUsedMinutesToday = 90,
            currentPlaceName = "역삼초 앞 GS25 편의점",
            currentAppName = "YouTube"
        )
        assertThat(repository.alerts.value.first().milestoneMinutes).isEqualTo(90)
    }

    @Test
    fun `pairing state defaults to NOT_PAIRED`() = runTest {
        val repository = LocalFamilySyncRepository(storageFile = null)
        assertThat(repository.pairingState.value).isEqualTo(PairingState.NOT_PAIRED)
    }

    @Test
    fun `createFamilyCode returns six digit code and moves to WAITING`() = runTest {
        val repository = LocalFamilySyncRepository(storageFile = null)
        val code = repository.createFamilyCode()
        assertThat(code).hasLength(6)
        assertThat(code.all { it.isDigit() }).isTrue()
        assertThat(repository.pairingState.value).isEqualTo(PairingState.WAITING)
    }

    @Test
    fun `linkFamilyCode with any six digit code demo-links`() = runTest {
        val repository = LocalFamilySyncRepository(storageFile = null)
        repository.createFamilyCode()
        assertThat(repository.linkFamilyCode("123456")).isTrue()
        assertThat(repository.pairingState.value).isEqualTo(PairingState.LINKED)
    }

    @Test
    fun `linkFamilyCode rejects non six digit input and keeps state`() = runTest {
        val repository = LocalFamilySyncRepository(storageFile = null)
        repository.createFamilyCode()
        assertThat(repository.linkFamilyCode("abc")).isFalse()
        assertThat(repository.linkFamilyCode("12345")).isFalse()
        assertThat(repository.pairingState.value).isEqualTo(PairingState.WAITING)
    }
}
