package com.familylink.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PolicyEvaluatorTest {
    private val defaultPolicy = ControlPolicy(
        childId = "child-1",
        allowedStartHour = 10,
        allowedStartMinute = 0,
        allowedEndHour = 21,
        allowedEndMinute = 0,
        dailyLimitMinutes = 120,
        bonusMinutesToday = 0,
        isImmediateLocked = false,
        alertIntervalMinutes = 30,
        appRules = mapOf(
            "com.android.dialer" to AppRule("com.android.dialer", "전화", AppAccessMode.ALWAYS_ALLOWED),
            "com.google.android.youtube" to AppRule("com.google.android.youtube", "YouTube", AppAccessMode.ALLOWED, dailyQuotaMinutes = 40),
            "com.supercell.brawlstars" to AppRule("com.supercell.brawlstars", "브롤스타즈", AppAccessMode.BLOCKED)
        )
    )

    @Test
    fun `blocks usage before 10 AM and at or after 9 PM for normal apps`() {
        val before10 = PolicyEvaluator.evaluate(defaultPolicy, "com.google.android.youtube", 9, 59, 10, emptyMap())
        assertThat(before10.isBlocked).isTrue()
        assertThat(before10.reason).isEqualTo(LockReason.OUTSIDE_ALLOWED_HOURS)

        val at10 = PolicyEvaluator.evaluate(defaultPolicy, "com.google.android.youtube", 10, 0, 10, emptyMap())
        assertThat(at10.isBlocked).isFalse()

        val at21 = PolicyEvaluator.evaluate(defaultPolicy, "com.google.android.youtube", 21, 0, 10, emptyMap())
        assertThat(at21.isBlocked).isTrue()
        assertThat(at21.reason).isEqualTo(LockReason.OUTSIDE_ALLOWED_HOURS)
    }

    @Test
    fun `supports overnight allowed window across midnight`() {
        val overnightPolicy = defaultPolicy.copy(
            allowedStartHour = 22,
            allowedStartMinute = 0,
            allowedEndHour = 6,
            allowedEndMinute = 0
        )
        val at23 = PolicyEvaluator.evaluate(overnightPolicy, "com.google.android.youtube", 23, 0, 10, emptyMap())
        assertThat(at23.isBlocked).isFalse()

        val at02 = PolicyEvaluator.evaluate(overnightPolicy, "com.google.android.youtube", 2, 0, 10, emptyMap())
        assertThat(at02.isBlocked).isFalse()

        val at12 = PolicyEvaluator.evaluate(overnightPolicy, "com.google.android.youtube", 12, 0, 10, emptyMap())
        assertThat(at12.isBlocked).isTrue()
        assertThat(at12.reason).isEqualTo(LockReason.OUTSIDE_ALLOWED_HOURS)
    }

    @Test
    fun `always allows dialer and child app itself even during instant lock or outside allowed hours`() {
        val lockedPolicy = defaultPolicy.copy(isImmediateLocked = true)
        val dialerDecision = PolicyEvaluator.evaluate(lockedPolicy, "com.android.dialer", 23, 30, 300, emptyMap())
        assertThat(dialerDecision.isBlocked).isFalse()

        val selfDecision = PolicyEvaluator.evaluate(lockedPolicy, "com.familylink.child", 23, 30, 300, emptyMap())
        assertThat(selfDecision.isBlocked).isFalse()
    }

    @Test
    fun `enforces daily total limit and respects bonus minutes`() {
        val exhausted = PolicyEvaluator.evaluate(defaultPolicy, "com.google.android.youtube", 15, 0, 120, emptyMap())
        assertThat(exhausted.isBlocked).isTrue()
        assertThat(exhausted.reason).isEqualTo(LockReason.DAILY_QUOTA_EXCEEDED)

        val withBonus = PolicyEvaluator.evaluate(defaultPolicy.copy(bonusMinutesToday = 30), "com.google.android.youtube", 15, 0, 140, emptyMap())
        assertThat(withBonus.isBlocked).isFalse()
    }

    @Test
    fun `enforces blocked app and per-app daily quota`() {
        val blockedGame = PolicyEvaluator.evaluate(defaultPolicy, "com.supercell.brawlstars", 14, 0, 20, emptyMap())
        assertThat(blockedGame.isBlocked).isTrue()
        assertThat(blockedGame.reason).isEqualTo(LockReason.APP_BLOCKED)

        val youtubeOverQuota = PolicyEvaluator.evaluate(
            defaultPolicy,
            "com.google.android.youtube",
            14,
            0,
            50,
            mapOf("com.google.android.youtube" to 40)
        )
        assertThat(youtubeOverQuota.isBlocked).isTrue()
        assertThat(youtubeOverQuota.reason).isEqualTo(LockReason.APP_QUOTA_EXCEEDED)
    }
}
