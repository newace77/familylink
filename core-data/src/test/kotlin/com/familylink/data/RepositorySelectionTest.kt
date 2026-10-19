package com.familylink.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RepositorySelectionTest {

    @Test
    fun `selectRepositoryKind falls back to Local when Firebase unavailable`() {
        assertThat(
            FamilyRepositoryProvider.selectRepositoryKind(firebaseInitialized = false)
        ).isEqualTo(RepositoryKind.LOCAL)
    }

    @Test
    fun `selectRepositoryKind picks Firebase whenever initialized regardless of pairing`() {
        // 페어링 유무는 선택 기준이 아니다: 초기화만 되어 있으면 NOT_PAIRED부터 이어받는다.
        assertThat(
            FamilyRepositoryProvider.selectRepositoryKind(firebaseInitialized = true)
        ).isEqualTo(RepositoryKind.FIREBASE)
    }
}
