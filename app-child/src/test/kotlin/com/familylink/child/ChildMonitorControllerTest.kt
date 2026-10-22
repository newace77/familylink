package com.familylink.child

import com.familylink.data.LocalFamilySyncRepository
import com.familylink.model.AlertType
import com.familylink.model.ContentType
import com.familylink.model.LockReason
import com.familylink.model.WebContentLog
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChildMonitorControllerTest {

    @Test
    fun `blocks restricted game and outside allowed hours, unlocks with parent PIN`() {
        val repository = LocalFamilySyncRepository(storageFile = null)
        val controller = ChildMonitorController(repository)

        // Blocked game at 2:00 PM
        val gameDecision = controller.onForegroundWindowEvaluated(
            packageName = "com.supercell.brawlstars",
            className = "com.supercell.brawlstars.GameActivity",
            visibleTexts = listOf("브롤스타즈"),
            urlCandidate = null,
            titleCandidate = null,
            isUrlBarFocused = false,
            currentHour = 14,
            currentMinute = 0,
            nowMillis = 10_000L
        )
        assertThat(gameDecision.isBlocked).isTrue()
        assertThat(gameDecision.reason).isEqualTo(LockReason.APP_BLOCKED)
        assertThat(controller.activeLockState.value.isBlocked).isTrue()

        // Wrong PIN fails unlock
        val wrongPin = controller.verifyParentPinAndUnlock("9999")
        assertThat(wrongPin).isFalse()
        assertThat(controller.activeLockState.value.isBlocked).isTrue()

        // Correct PIN ("0000") unlocks and grants 15m emergency grace or clears lock
        val rightPin = controller.verifyParentPinAndUnlock("0000")
        assertThat(rightPin).isTrue()
        assertThat(controller.activeLockState.value.isBlocked).isFalse()
    }

    @Test
    fun `records Chrome URLs and triggers tamper protection when Settings accessibility is opened`() {
        val repository = LocalFamilySyncRepository(storageFile = null)
        val controller = ChildMonitorController(repository)

        // Visit Chrome web page during allowed hours
        val chromeDecision = controller.onForegroundWindowEvaluated(
            packageName = "com.android.chrome",
            className = "org.chromium.chrome.browser.ChromeTabbedActivity",
            visibleTexts = listOf("국립중앙과학관 온라인 전시관", "science.go.kr/exhibit"),
            urlCandidate = "science.go.kr/exhibit",
            titleCandidate = "국립중앙과학관 온라인 전시관",
            isUrlBarFocused = false,
            currentHour = 15,
            currentMinute = 20,
            nowMillis = 50_000L
        )
        assertThat(chromeDecision.isBlocked).isFalse()
        assertThat(repository.webLogs.value.first().urlOrQuery).isEqualTo("https://science.go.kr/exhibit")

        // Child attempts to disable Accessibility in Android Settings
        val tamperDecision = controller.onForegroundWindowEvaluated(
            packageName = "com.android.settings",
            className = "com.android.settings.SubSettings",
            visibleTexts = listOf("접근성", "FamilyLink Plus", "사용 안함"),
            urlCandidate = null,
            titleCandidate = null,
            isUrlBarFocused = false,
            currentHour = 15,
            currentMinute = 21,
            nowMillis = 60_000L
        )
        assertThat(tamperDecision.isBlocked).isTrue()
        assertThat(tamperDecision.reason).isEqualTo(LockReason.TAMPER_PROTECTION)
        assertThat(repository.alerts.value.first().type).isEqualTo(AlertType.TAMPER_ATTEMPT)
    }

    @Test
    fun `seeds last web log from repository so a restarted service does not duplicate the open page`() {
        val repository = LocalFamilySyncRepository(storageFile = null)
        val seedLog = WebContentLog(
            id = "seed-1",
            timestampMillis = 100_000L,
            sourcePackage = "com.android.chrome",
            browserOrAppName = "Chrome",
            contentType = ContentType.WEB_PAGE,
            title = "NAVER",
            urlOrQuery = "https://m.naver.com/home"
        )
        repository.recordWebContentLog(seedLog)
        val countAfterSeed = repository.webLogs.value.size

        // 새 컨트롤러 = 접근성 서비스/프로세스 재시작 상황.
        // 같은 페이지가 다시 평가되더라도 새 기록을 만들지 않아야 한다.
        val controller = ChildMonitorController(repository)
        controller.onForegroundWindowEvaluated(
            packageName = "com.android.chrome",
            className = "org.chromium.chrome.browser.ChromeTabbedActivity",
            visibleTexts = listOf("NAVER"),
            urlCandidate = "m.naver.com/home",
            titleCandidate = "NAVER",
            isUrlBarFocused = false,
            currentHour = 15,
            currentMinute = 20,
            nowMillis = 500_000L
        )

        assertThat(repository.webLogs.value.size).isEqualTo(countAfterSeed)
        assertThat(repository.webLogs.value.first().id).isEqualTo(seedLog.id)
    }
}
