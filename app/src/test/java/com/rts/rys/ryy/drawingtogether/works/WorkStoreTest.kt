package com.rts.rys.ryy.drawingtogether.works

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkStoreTest {

    // #14 — 대응 meta 가 없는 png 만 orphan 으로 골라낸다.
    @Test
    fun `orphanPngIds picks png without matching meta`() {
        val files = listOf(
            "a.png", "a.meta",   // 정상 쌍
            "b.png",             // orphan (meta 없음)
            "c.meta",            // meta 만 (png 없음 — orphan 아님)
        )
        assertEquals(listOf("b"), WorkStore.orphanPngIds(files))
    }

    @Test
    fun `orphanPngIds returns empty when all png have meta`() {
        val files = listOf("x.png", "x.meta", "y.png", "y.meta")
        assertTrue(WorkStore.orphanPngIds(files).isEmpty())
    }

    @Test
    fun `orphanPngIds ignores unrelated files`() {
        val files = listOf("z.png", "z.meta", "readme.txt", "thumb")
        assertTrue(WorkStore.orphanPngIds(files).isEmpty())
    }
}
