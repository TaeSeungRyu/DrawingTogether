package com.rts.rys.ryy.drawingtogether.session

// endpoint별 마지막 인바운드 시각을 추적해 "조용히 사라진"(Bye 없이 범위 이탈·비행기 모드) 피어를
// 판정한다 — 앱 레벨 하트비트의 순수 로직. Nearby 의 onDisconnected 가 이런 경우 안 오거나 매우 늦게
// 오는 문제를 보완한다. 시계는 호출자가 nowMs 로 주입(테스트 용이). 스레드 안전 아님 — 단일 스코프에서만.
class LivenessTracker(private val timeoutMs: Long) {

    private val lastSeenMs = mutableMapOf<String, Long>()

    // 인바운드 프레임 수신 — 해당 endpoint 를 살아있음으로 갱신.
    fun onSeen(endpointId: String, nowMs: Long) {
        lastSeenMs[endpointId] = nowMs
    }

    fun clear() {
        lastSeenMs.clear()
    }

    // 현재 활성(연결된) endpoint 중 timeout 을 초과해 무응답인 것들을 반환한다.
    // 추적에 없던 활성 endpoint 는 이번 nowMs 로 등록(방금 연결 간주 — 즉시 타임아웃 방지),
    // 더 이상 활성이 아닌 추적 항목은 정리한다.
    fun staleEndpoints(activeIds: Collection<String>, nowMs: Long): List<String> {
        val active = activeIds.toSet()
        val stale = mutableListOf<String>()
        for (id in active) {
            val last = lastSeenMs[id]
            if (last == null) {
                lastSeenMs[id] = nowMs
            } else if (nowMs - last > timeoutMs) {
                stale.add(id)
            }
        }
        lastSeenMs.keys.retainAll(active)
        return stale
    }
}
