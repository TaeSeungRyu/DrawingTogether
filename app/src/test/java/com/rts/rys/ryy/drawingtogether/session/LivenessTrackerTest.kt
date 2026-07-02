package com.rts.rys.ryy.drawingtogether.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivenessTrackerTest {

    @Test
    fun `newly active endpoint is registered, not immediately stale`() {
        val t = LivenessTracker(timeoutMs = 10_000)
        // 처음 보는 endpoint 는 이번 시각으로 등록되고 stale 아님.
        assertTrue(t.staleEndpoints(listOf("A"), nowMs = 1_000).isEmpty())
        // 곧바로 다시 검사해도 아직 timeout 전.
        assertTrue(t.staleEndpoints(listOf("A"), nowMs = 5_000).isEmpty())
    }

    @Test
    fun `endpoint becomes stale after timeout with no inbound`() {
        val t = LivenessTracker(timeoutMs = 10_000)
        t.staleEndpoints(listOf("A"), nowMs = 1_000)   // 등록(=1000)
        // 1000 + 10000 = 11000 까지는 OK, 그 이후 stale.
        assertTrue(t.staleEndpoints(listOf("A"), nowMs = 11_000).isEmpty())
        assertEquals(listOf("A"), t.staleEndpoints(listOf("A"), nowMs = 11_001))
    }

    @Test
    fun `onSeen refreshes liveness and prevents timeout`() {
        val t = LivenessTracker(timeoutMs = 10_000)
        t.staleEndpoints(listOf("A"), nowMs = 1_000)
        t.onSeen("A", nowMs = 9_000)                   // Pong 등 인바운드 도착
        // 9000 기준 timeout 이라 15000 은 아직 OK.
        assertTrue(t.staleEndpoints(listOf("A"), nowMs = 15_000).isEmpty())
        assertEquals(listOf("A"), t.staleEndpoints(listOf("A"), nowMs = 19_001))
    }

    @Test
    fun `endpoints no longer active are forgotten`() {
        val t = LivenessTracker(timeoutMs = 10_000)
        t.staleEndpoints(listOf("A"), nowMs = 1_000)
        // A 가 활성 목록에서 빠지면 추적에서 제거 → 다시 나타나도 옛 시각으로 stale 판정 안 됨.
        t.staleEndpoints(emptyList(), nowMs = 2_000)
        assertTrue(t.staleEndpoints(listOf("A"), nowMs = 100_000).isEmpty())
    }

    @Test
    fun `only stale endpoints are reported among multiple active`() {
        val t = LivenessTracker(timeoutMs = 10_000)
        t.staleEndpoints(listOf("A", "B"), nowMs = 1_000)  // 둘 다 등록
        t.onSeen("B", nowMs = 12_000)                       // B 만 갱신
        assertEquals(listOf("A"), t.staleEndpoints(listOf("A", "B"), nowMs = 12_500))
    }
}
