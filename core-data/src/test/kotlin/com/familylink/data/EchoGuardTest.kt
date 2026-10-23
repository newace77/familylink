package com.familylink.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Issue 5: 안심존 삭제가 Firestore 반영 실패 시 서버 스냅샷이 로컬 UI를 되돌리는 현상.
 * FieldEchoGuard는 필드별 로컬 쓰기 의도(intent)를 추적해,
 * 대기 중(pending) 쓰기의 의도값과 다른 서버 스냅샷 적용을 거부한다.
 */
class EchoGuardTest {

    private val guard = FieldEchoGuard()

    @Test
    fun `pending 중 서버 스냅샷이 로컬 의도와 다르면 거부한다`() {
        guard.onLocalWriteQueued(FIELD_SAFE_ZONES, """[{"zoneId":"a"},{"zoneId":"b"}]""")
        // 서버는 아직 삭제 이전(3존) 값을 내려준다
        val accepted = guard.shouldApplyServerUpdate(FIELD_SAFE_ZONES, """[{"zoneId":"a"},{"zoneId":"b"},{"zoneId":"c"}]""")
        assertThat(accepted).isFalse()
    }

    @Test
    fun `서버가 의도값과 동일하면 허용하고 pending을 해제한다`() {
        guard.onLocalWriteQueued(FIELD_SAFE_ZONES, """[{"zoneId":"a"},{"zoneId":"b"}]""")
        val accepted = guard.shouldApplyServerUpdate(FIELD_SAFE_ZONES, """[{"zoneId":"a"},{"zoneId":"b"}]""")
        assertThat(accepted).isTrue()
        // ack 성공 이후에는 이후 서버 스냅샷을 그대로 수용해야 한다
        val after = guard.shouldApplyServerUpdate(FIELD_SAFE_ZONES, """[{"zoneId":"a"},{"zoneId":"b"},{"zoneId":"c"}]""")
        assertThat(after).isTrue()
    }

    @Test
    fun `pending이 없으면 서버 업데이트를 항상 허용한다`() {
        val accepted = guard.shouldApplyServerUpdate(FIELD_POLICY, """{"dailyLimitMinutes":120}""")
        assertThat(accepted).isTrue()
    }

    @Test
    fun `쓰기 실패 시 pending을 유지해 stale 스냅샷을 계속 거부한다`() {
        guard.onLocalWriteQueued(FIELD_SAFE_ZONES, """[{"zoneId":"a"}]""")
        guard.onLocalWriteResult(FIELD_SAFE_ZONES, success = false)
        val accepted = guard.shouldApplyServerUpdate(FIELD_SAFE_ZONES, """[{"zoneId":"a"},{"zoneId":"b"}]""")
        assertThat(accepted).isFalse()
    }

    @Test
    fun `연속 쓰기 시 최신 의도값을 기준으로 판정한다`() {
        guard.onLocalWriteQueued(FIELD_SAFE_ZONES, """[{"zoneId":"a"}]""")
        guard.onLocalWriteQueued(FIELD_SAFE_ZONES, """[{"zoneId":"a"},{"zoneId":"b"}]""")

        // 이전 의도(a) 스냅샷 → 거부, 최신 의도(a+b) → 허용
        assertThat(guard.shouldApplyServerUpdate(FIELD_SAFE_ZONES, """[{"zoneId":"a"}]""")).isFalse()
        assertThat(guard.shouldApplyServerUpdate(FIELD_SAFE_ZONES, """[{"zoneId":"a"},{"zoneId":"b"}]""")).isTrue()
    }
}
