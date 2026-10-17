package com.familylink.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SyncListMergerTest {

    private data class Row(val key: String, val label: String)

    private val idOf: (Row) -> String = { it.key }

    @Test
    fun `merger dedups by id keeping incoming`() {
        val current = listOf(Row("1", "a"))
        val incoming = listOf(Row("1", "b"), Row("2", "c"))

        val merged = SyncListMerger.merge(current, incoming, idOf)

        assertThat(merged).isEqualTo(listOf(Row("1", "b"), Row("2", "c")))
    }

    @Test
    fun `merger dedups reattached listener refire`() {
        // Review Focus 3: 리스너 재부착 시 동일 문서 세트가 다시 도착해도 중복 없어야 한다.
        val first = listOf(Row("1", "a"), Row("2", "b"), Row("3", "c"))
        val refire = listOf(Row("1", "a"), Row("2", "b"), Row("3", "c"))

        val once = SyncListMerger.merge(emptyList(), first, idOf)
        val twice = SyncListMerger.merge(once, refire, idOf)

        assertThat(twice.count { it.key == "1" }).isEqualTo(1)
        assertThat(twice).hasSize(3)
    }
}
