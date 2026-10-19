package com.familylink.data

import com.familylink.model.StayPointClusterer
import com.familylink.model.StayPointSession
import java.util.Calendar

/**
 * 자녀 기기의 주기적 위치 틱을 [StayPointSession] 체류 세션으로 변환하는 순수 로직.
 * Android 의존성이 없으므로 단위 테스트가 가능하다.
 *
 * - "위치 미확인" 같은 미해석 장소는 세션을 만들지 않는다.
 * - 같은 장소(장소명 동일 + 250m 이내)는 세션을 추가로 만들지 않는다.
 * - 장소가 바뀌면: 이전 세션을 [departedAtMillis=now]로 마감해 방출 + 새 진행 중 세션 방출.
 * - 진행 중 세션은 `isOngoing == true` (departedAtMillis == 0L).
 */
class StaySessionTicker(
    private val childId: String,
    restoredOngoingSession: StayPointSession? = null
) {

    companion object {
        /** 장소 전환 판정 임계(m). PlaceNameResolver 랜드마크 반경과 동일한 기준. */
        const val MOVE_THRESHOLD_METERS = 250.0
        /** unresolved 장소를 표현하는 데모/레거시 문구가 아닌 기본값. */
        const val UNRESOLVED_PLACE = "위치 미확인"
    }

    var currentSession: StayPointSession? = restoredOngoingSession
        private set

    val currentPlaceName: String?
        get() = currentSession?.placeName

    /**
     * @return 이번 틱으로 기록해야 하는 세션 목록 (0개 = 변화 없음,
     *          1개 = 신규 진행 중, 2개 = [마감 세션, 신규 진행 중])
     */
    fun onTick(
        nowMillis: Long,
        latitude: Double,
        longitude: Double,
        placeName: String,
        address: String
    ): List<StayPointSession> {
        val resolved = placeName.isNotBlank() && placeName != UNRESOLVED_PLACE && latitude != 0.0
        if (!resolved) return emptyList()

        val current = currentSession
        if (current == null) {
            val fresh = newSession(nowMillis, latitude, longitude, placeName, address)
            currentSession = fresh
            return listOf(fresh)
        }

        val samePlace = current.placeName == placeName &&
            StayPointClusterer.distanceMeters(current.latitude, current.longitude, latitude, longitude) <= MOVE_THRESHOLD_METERS
        if (samePlace) return emptyList()

        val completed = current.copy(
            departedAtMillis = nowMillis,
            appUsages = current.appUsages
        )
        val fresh = newSession(nowMillis, latitude, longitude, placeName, address)
        currentSession = fresh
        return listOf(completed, fresh)
    }

    /** 서비스 종료 등으로 마지막 체류를 즉시 마감할 때 사용. */
    fun finalize(nowMillis: Long): StayPointSession? {
        val current = currentSession?.takeIf { it.isOngoing } ?: return null
        currentSession = current.copy(departedAtMillis = nowMillis)
        return currentSession
    }

    private fun newSession(
        nowMillis: Long,
        latitude: Double,
        longitude: Double,
        placeName: String,
        address: String
    ): StayPointSession = StayPointSession(
        sessionId = "stay-$nowMillis-${latitude.toShortString()}-${longitude.toShortString()}",
        childId = childId,
        dateKey = nowMillis.toDateKey(),
        latitude = latitude,
        longitude = longitude,
        placeName = placeName,
        address = address,
        arrivedAtMillis = nowMillis,
        departedAtMillis = 0L
    )

    private fun Double.toShortString(): String = "%.5f".format(this)
    private fun Long.toDateKey(): String {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = this@toDateKey
        }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }
}
