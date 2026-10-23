package com.familylink.data

import com.familylink.model.ParentAlert

/**
 * Issue 6: 데모 시드 알림은 재시작마다 "방금"timestamp로 재생성되어 실제 알림 위로 떠오른다.
 * 표시 계약(알림은 최신순)을 유지하면서 시드 혼입을 정리하는 순수 로직.
 */
object SeedAlertIds {
    // DemoScenarioSeed.defaultAlerts()의 결정적 id — 신규 시드 추가 시 반드시 여기에 함께 반영한다.
    const val MILESTONE_60 = "alert-milestone-60"
    const val ZONE_EXIT_ACADEMY = "alert-zone-exit-academy"
    const val MILESTONE_30 = "alert-milestone-30"

    val all = setOf(MILESTONE_60, ZONE_EXIT_ACADEMY, MILESTONE_30)
}

object AlertListOps {

    /** 데모 시드 알림을 목록에서 제거한다(파싱 실패 등 부분 목록에도 안전). */
    fun removeDemoAlerts(alerts: List<ParentAlert>): List<ParentAlert> =
        alerts.filterNot { it.alertId in SeedAlertIds.all }

    /** 표시·저장 계약: 알림 목록은 항상 최신(timestampMillis 내림차순) 정렬을 유지한다. */
    fun sortedNewestFirst(alerts: List<ParentAlert>): List<ParentAlert> =
        alerts.sortedByDescending { it.timestampMillis }
}
