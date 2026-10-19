package com.familylink.data

import com.familylink.model.StayPointSession
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.file.Files

class StaySessionTickerTest {

    private val childId = "child-1"

    @Test
    fun `first tick with resolved place emits a single ongoing session`() {
        val ticker = StaySessionTicker(childId)
        val emitted = ticker.onTick(
            nowMillis = 1_000L,
            latitude = 37.4994,
            longitude = 127.0334,
            placeName = "역삼초 앞 GS25 편의점",
            address = "서울 강남구 역삼로 123"
        )
        assertThat(emitted).hasSize(1)
        assertThat(emitted[0].isOngoing).isTrue()
        assertThat(emitted[0].arrivedAtMillis).isEqualTo(1_000L)
        assertThat(emitted[0].departedAtMillis).isEqualTo(0L)
        assertThat(emitted[0].placeName).isEqualTo("역삼초 앞 GS25 편의점")
        assertThat(ticker.currentPlaceName).isEqualTo("역삼초 앞 GS25 편의점")
    }

    @Test
    fun `repeated ticks at the same place emit nothing`() {
        val ticker = StaySessionTicker(childId)
        ticker.onTick(1_000L, 37.4994, 127.0334, "GS25", "주소")
        val emitted = ticker.onTick(30_000L, 37.49942, 127.03342, "GS25", "주소")
        assertThat(emitted).isEmpty()
    }

    @Test
    fun `moving beyond threshold closes previous session and opens a new ongoing one`() {
        val ticker = StaySessionTicker(childId)
        ticker.onTick(1_000L, 37.4994, 127.0334, "GS25", "역삼로 123")
        val emitted = ticker.onTick(600_000L, 37.5000, 127.0330, "역삼초등학교", "역삼로 120")
        assertThat(emitted).hasSize(2)

        val completed = emitted.first()
        assertThat(completed.placeName).isEqualTo("GS25")
        assertThat(completed.departedAtMillis).isEqualTo(600_000L)
        assertThat(completed.arrivedAtMillis).isEqualTo(1_000L)
        assertThat(completed.isOngoing).isFalse()
        assertThat(completed.dwellMinutes).isEqualTo(9)

        val ongoing = emitted.last()
        assertThat(ongoing.placeName).isEqualTo("역삼초등학교")
        assertThat(ongoing.arrivedAtMillis).isEqualTo(600_000L)
        assertThat(ongoing.isOngoing).isTrue()
    }

    @Test
    fun `same place name within threshold distance is not a move`() {
        val ticker = StaySessionTicker(childId)
        ticker.onTick(0L, 37.4994, 127.0334, "GS25", "주소")
        // ~수십 미터 이동: 같은 장소로 판정 (250m 임계)
        val emitted = ticker.onTick(60_000L, 37.5005, 127.0340, "GS25", "주소")
        assertThat(emitted).isEmpty()
    }

    @Test
    fun `ticks with unresolved place are ignored until a place resolves`() {
        val ticker = StaySessionTicker(childId)
        assertThat(ticker.onTick(0L, 0.0, 0.0, "위치 미확인", "")).isEmpty()
        assertThat(ticker.currentPlaceName).isNull()
        // 장소가 나중에 해석되면 정상 세션 개설
        val emitted = ticker.onTick(30_000L, 37.4994, 127.0334, "GS25", "주소")
        assertThat(emitted).hasSize(1)
    }

    @Test
    fun `restore resumes ongoing session without duplicating`() {
        // 서비스 재시작 시 진행 중 세션이 로컬에서 주입될 수 있다 (현재는 미사용) — API 안정성 검증
        val restored = StayPointSession(
            sessionId = "s1",
            childId = childId,
            dateKey = "2026-10-19",
            latitude = 37.4994,
            longitude = 127.0334,
            placeName = "GS25",
            address = "주소",
            arrivedAtMillis = 500L,
            departedAtMillis = 0L
        )
        val ticker = StaySessionTicker(childId, restored)
        val emitted = ticker.onTick(10_000L, 37.4994, 127.0334, "GS25", "주소")
        assertThat(emitted).isEmpty() // 같은 장소 — 새로 개설하지 않음
        val moved = ticker.onTick(60_000L, 37.5000, 127.0330, "학교", "주소")
        assertThat(moved).hasSize(2)
        assertThat(moved.first().arrivedAtMillis).isEqualTo(500L) // 복원된 세션 기준
    }
}
