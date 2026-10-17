package com.familylink.parent

import com.familylink.data.LocalFamilySyncRepository
import com.familylink.model.AlertType
import com.familylink.model.AppAccessMode
import com.familylink.model.ContentType
import com.familylink.parent.ui.ParentDashboardController
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ParentViewModelTest {

    @Test
    fun `exposes school convenience store stay session with YouTube 30m and Chrome web logs`() {
        val repository = LocalFamilySyncRepository(storageFile = null)
        val controller = ParentDashboardController(repository)

        val selected = controller.selectedStaySession.value
        assertThat(selected).isNotNull()
        assertThat(selected!!.placeName).isEqualTo("역삼초 앞 GS25 편의점")
        assertThat(selected.headlineBadge()).contains("YouTube 30분")
        assertThat(selected.webLogs).isNotEmpty()
    }

    @Test
    fun `filters web history by browser vs YouTube and updates time window, app rules, and 30m alert interval`() {
        val repository = LocalFamilySyncRepository(storageFile = null)
        val controller = ParentDashboardController(repository)

        val chromeOnly = controller.filterWebLogs(contentType = ContentType.WEB_PAGE, searchQuery = "마인크래프트")
        assertThat(chromeOnly).hasSize(1)
        assertThat(chromeOnly.first().urlOrQuery).contains("namu.wiki")

        controller.updateAllowedTimeWindow(startHour = 10, startMinute = 30, endHour = 21, endMinute = 30)
        controller.updateAlertIntervalMinutes(20)
        controller.setAppAccessMode("com.google.android.youtube", AppAccessMode.BLOCKED, dailyQuotaMinutes = 30)
        controller.toggleInstantLock(true)

        val policy = controller.policy.value
        assertThat(policy.formattedAllowedWindow()).isEqualTo("10:30 ~ 21:30")
        assertThat(policy.alertIntervalMinutes).isEqualTo(20)
        assertThat(policy.appRules["com.google.android.youtube"]?.accessMode).isEqualTo(AppAccessMode.BLOCKED)
        assertThat(policy.isImmediateLocked).isTrue()

        controller.grantBonusTime(30)
        assertThat(controller.policy.value.bonusMinutesToday).isEqualTo(30)
        assertThat(controller.policy.value.isImmediateLocked).isFalse()

        val simulatedAlert = controller.simulateNextUsageMilestone()
        assertThat(simulatedAlert).isNotNull()
        assertThat(simulatedAlert!!.type).isEqualTo(AlertType.USAGE_MILESTONE)
    }
}
