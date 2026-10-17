package com.familylink.data

/**
 * 컬렉션 리스너 결과와 옵티머스틱 로컬 상태의 dedup 병합.
 * incoming(리스너가 준 최신 스냅샷)을 우선하고, id가 겹치면 current 쪽을 버린다.
 * 정렬은 리스너 쿼리(orderBy·limit)가 담당하므로 이 곳은 dedup만 한다.
 */
object SyncListMerger {
    fun <T> merge(current: List<T>, incoming: List<T>, idOf: (T) -> String): List<T> {
        val incomingIds = incoming.mapTo(mutableSetOf(), idOf)
        return current.filter { idOf(it) !in incomingIds } + incoming
    }
}
