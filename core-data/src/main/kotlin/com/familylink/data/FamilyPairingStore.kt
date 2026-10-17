package com.familylink.data

import android.content.Context

/**
 * 가족 코드를 기기에 영구 저장하는 최소 저장소.
 * 앱을 재시작해도 페어링 생성/연동 상태를 복원하기 위한 유일한 로컬 창고다.
 */
class FamilyPairingStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FirestoreSchema.PREFS_NAME, Context.MODE_PRIVATE)

    fun savedCode(): String? = prefs.getString(FirestoreSchema.KEY_FAMILY_CODE, null)

    fun save(code: String) {
        prefs.edit().putString(FirestoreSchema.KEY_FAMILY_CODE, code).apply()
    }

    fun clear() {
        prefs.edit().remove(FirestoreSchema.KEY_FAMILY_CODE).apply()
    }
}
