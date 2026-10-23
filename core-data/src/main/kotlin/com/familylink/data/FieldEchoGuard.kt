package com.familylink.data

import org.json.JSONArray
import org.json.JSONObject

/** Echo 가드가 감시하는 동기화 필드 키 (families/{code} 문서 最상위 필드). */
const val FIELD_POLICY = "policy"
const val FIELD_SAFE_ZONES = "safeZones"
const val FIELD_DEVICE_HEALTH = "deviceHealth"

/**
 * 필드별 로컬 쓰기 Echo 가드 (Issue 5 · 순수 로직 · 단위 테스트 대상).
 *
 * 부모 앱의 정책/안심존 쓰기는 fire-and-forget이므로, 쓰기가 네트워크에서 실패하거나
 * 지연되는 동안 Firestore 리스너가 **이전 서버 스냅샷**을 내려와 로컬 StateFlow(UI)를
 * 변경 이전 상태로 되돌린다(이슈 5 재현: 안심존 삭제 무효화).
 *
 * 해결 전략:
 * - 로컬 쓰기를 큐에 넣을 때 의도값(intent payload)을 기록한다.
 * - 서버 스냅샷 적용 전에  [shouldApplyServerUpdate]로 판단한다:
 *   - 대기 중 쓰기가 있고 서버 값이 로컬 의도와 **다르면** → stale 스냅샷이므로 거부.
 *   - 서버 값이 로컬 의도와 **같으면** → 쓰기 확정(ack)이니 수용하고 대기를 해제한다.
 * - 쓰기 결과 실패 시 대기를 유지해 stale 스냅샷을 계속 거부하고, 재시도가 새 의도를 기록한다.
 * - 연속 쓰기는 최신 의도값을 기준으로 판정한다.
 */
class FieldEchoGuard {

    private val pending = mutableMapOf<String, String>()

    /** 로컬 쓰기 요청이 Firestore에 큐잉되기 직전에 호출: 최신 의도값으로 갱신. */
    fun onLocalWriteQueued(fieldKey: String, intentPayload: String) {
        pending[fieldKey] = intentPayload
    }

    /** Firestore set/delete Task 완료 시 호출. 성공이면 대기 해제, 실패면 유지. */
    fun onLocalWriteResult(fieldKey: String, success: Boolean) {
        if (success) pending.remove(fieldKey)
    }

    /**
     * 서버 스냅샷의 필드 값을 로컬 StateFlow에 적용해도 되는지 판정.
     * true면 적용, false면 stale 스냅샷이므로 무시(로컬 의도 유지).
     */
    fun shouldApplyServerUpdate(fieldKey: String, serverPayload: String): Boolean {
        val intent = pending[fieldKey] ?: return true
        if (jsonEquals(intent, serverPayload)) {
            pending.remove(fieldKey)
            return true
        }
        return false
    }

    fun hasPendingWrite(fieldKey: String): Boolean = pending.containsKey(fieldKey)

    private fun jsonEquals(a: String, b: String): Boolean {
        if (a == b) return true
        return runCatching {
            val parsedA = if (a.trimStart().startsWith("[")) JSONArray(a) else JSONObject(a)
            val parsedB = if (b.trimStart().startsWith("[")) JSONArray(b) else JSONObject(b)
            parsedA == parsedB
        }.getOrDefault(false)
    }
}
