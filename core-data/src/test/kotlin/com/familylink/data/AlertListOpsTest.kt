package com.familylink.data

import com.familylink.model.AlertType
import com.familylink.model.ParentAlert
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Issue 6: 앱 재시작마다 데모 시드 알림 3장이 실제 Firestore 알림보다 위에 정렬되는 결함.
 * AlertListOps는 시드 제거 + 최신순 정렬을 순수 로직으로 보장한다(단위 테스트 대상).
 */
class AlertListOpsTest {

    private fun alert(id: String, minutesAgo: Long) = ParentAlert(
        alertId = id,
        type = AlertType.USAGE_MILESTONE,
        title = id,
        message = "m",
        timestampMillis = 1_700_000_000_000L - minutesAgo * 60_000L
    )

    @Test
    fun `시드 알림은 대상 id 목록으로 제거된다`() {
        val mixed = listOf(
            alert(SeedAlertIds.MILESTONE_60, 5),
            alert("milestone-child-1-60-1792683496462", 20),
            alert(SeedAlertIds.ZONE_EXIT_ACADEMY, 50)
        )

        val purged = AlertListOps.removeDemoAlerts(mixed)

        assertThat(purged.map { it.alertId }).containsExactly(
            "milestone-child-1-60-1792683496462"
        ).inOrder()
    }

    @Test
    fun `병합에 시드가 섞여도 제거 후에는 실알림만 남는다 - 시드가 실알림 위로 재부상하지 않는다`() {
        val merged = listOf(
            alert(SeedAlertIds.MILESTONE_60, 5),          // 재시작마다 "방금"으로 재생성되는 시드
            alert("real-old", 30)
        )

        val result = AlertListOps.sortedNewestFirst(AlertListOps.removeDemoAlerts(merged))

        assertThat(result.map { it.alertId }).containsExactly("real-old")
    }

    @Test
    fun `emit 이후에도 목록은 최신순 정렬이 유지된다`() {
        val current = AlertListOps.sortedNewestFirst(listOf(alert("a", 10), alert("b", 40)))
        val afterEmit = AlertListOps.sortedNewestFirst(current + alert("new", 2))

        assertThat(afterEmit[0].alertId).isEqualTo("new")
    }

    @Test
    fun `제거와 정렬은 한 번의 파이프라인으로도 동작한다`() {
        val merged = listOf(
            alert(SeedAlertIds.MILESTONE_60, 5),
            alert("real-secondary", 15),
            alert("real-primary", 3)
        )

        val pipeline = AlertListOps.sortedNewestFirst(AlertListOps.removeDemoAlerts(merged))

        assertThat(pipeline.map { it.alertId }).containsExactly("real-primary", "real-secondary").inOrder()
    }
}
