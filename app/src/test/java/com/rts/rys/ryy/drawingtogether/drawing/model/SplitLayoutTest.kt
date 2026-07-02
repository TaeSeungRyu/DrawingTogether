package com.rts.rys.ryy.drawingtogether.drawing.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitLayoutTest {

    @Test
    fun `every layout slot count matches its slots list`() {
        SplitLayout.entries.forEach { layout ->
            assertEquals(layout.name, layout.slotCount, layout.slots.size)
        }
    }

    // 슬롯들이 합성물(0..1 정사각)을 빈틈·겹침 없이 덮어야 한다 — 면적 합 = 1.
    @Test
    fun `slots tile the unit square (area sums to 1)`() {
        SplitLayout.entries.forEach { layout ->
            val area = layout.slots.sumOf { (it.width * it.height).toDouble() }
            assertEquals(layout.name, 1.0, area, 1e-4)
        }
    }

    @Test
    fun `slots stay within unit bounds and are non-empty`() {
        SplitLayout.entries.forEach { layout ->
            layout.slots.forEach { s ->
                assertTrue(layout.name, s.left in 0f..1f && s.right in 0f..1f)
                assertTrue(layout.name, s.top in 0f..1f && s.bottom in 0f..1f)
                assertTrue(layout.name, s.width > 0f && s.height > 0f)
            }
        }
    }

    @Test
    fun `slotAspect is width over height`() {
        // 좌우 2분할: 각 슬롯 0.5 x 1 → 0.5 (세로로 길쭉)
        assertEquals(0.5f, SplitLayout.TwoLeftRight.slotAspect(0), 1e-4f)
        // 상하 2분할: 각 슬롯 1 x 0.5 → 2.0 (가로로 넓음)
        assertEquals(2.0f, SplitLayout.TwoTopBottom.slotAspect(0), 1e-4f)
        // 2×2: 0.5 x 0.5 → 1.0 (정사각)
        assertEquals(1.0f, SplitLayout.FourGrid.slotAspect(3), 1e-4f)
    }

    @Test
    fun `layoutsFor returns only matching participant counts`() {
        assertTrue(SplitLayout.layoutsFor(2).all { it.slotCount == 2 })
        assertTrue(SplitLayout.layoutsFor(3).all { it.slotCount == 3 })
        assertTrue(SplitLayout.layoutsFor(4).all { it.slotCount == 4 })
        assertEquals(2, SplitLayout.layoutsFor(2).size)
        assertEquals(4, SplitLayout.layoutsFor(3).size)
        assertEquals(3, SplitLayout.layoutsFor(4).size)
        assertTrue(SplitLayout.layoutsFor(1).isEmpty())
        assertTrue(SplitLayout.layoutsFor(5).isEmpty())
    }

    @Test
    fun `byId round-trips the enum name`() {
        SplitLayout.entries.forEach { assertEquals(it, SplitLayout.byId(it.name)) }
        assertNull(SplitLayout.byId("nope"))
    }
}
