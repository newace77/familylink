package com.familylink.data

/**
 * Firestore 문서 경로/필드 이름의 단일 진실 원천.
 * Android(FirebaseFamilySyncRepository)와 웹 대시보드(app.js)가 동일 문자열을 사용해야 한다.
 *
 * 트리: families/{familyCode}/policy|deviceHealth|safeZones 문서 + alerts|webLogs|staySessions 컬렉션
 * 모든 문서는 {"data": "<ModelJson 직렬화 JSON 문자열>"} 형태로 저장한다.
 */
object FirestoreSchema {
    const val FAMILIES = "families"
    const val POLICY = "policy"
    const val DEVICE_HEALTH = "deviceHealth"
    const val SAFE_ZONES = "safeZones"
    const val PAIRED = "paired"
    const val CHILD_LINKED = "childLinked"
    const val ALERTS = "alerts"
    const val WEB_LOGS = "webLogs"
    const val STAY_SESSIONS = "staySessions"
    const val DATA = "data"
    const val TIMESTAMP_MILLIS = "timestampMillis"
    const val PREFS_NAME = "familylink_pairing"
    const val KEY_FAMILY_CODE = "family_code"
}
