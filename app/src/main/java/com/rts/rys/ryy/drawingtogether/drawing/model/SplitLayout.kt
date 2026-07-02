package com.rts.rys.ryy.drawingtogether.drawing.model

// 나눠 그리기(Split) 레이아웃 — 하나의 합성 캔버스를 인원 수만큼 슬롯으로 나눈 정의. 순수 데이터.
// 합성물은 정사각(1:1) 기준이고, 슬롯 rect 는 그 안의 정규화 좌표(0..1).
// 각 참가자는 자기 슬롯 비율(slotAspect)로 고정된 캔버스에 그리므로, 합성 시 슬롯 rect 에
// 균일 스케일로 배치돼 왜곡이 없다.
data class SplitSlot(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

enum class SplitLayout(
    val label: String,
    val slotCount: Int,
    val slots: List<SplitSlot>,
) {
    // 2명
    TwoTopBottom("상하", 2, listOf(
        SplitSlot(0f, 0f, 1f, 0.5f),
        SplitSlot(0f, 0.5f, 1f, 1f),
    )),
    TwoLeftRight("좌우", 2, listOf(
        SplitSlot(0f, 0f, 0.5f, 1f),
        SplitSlot(0.5f, 0f, 1f, 1f),
    )),

    // 3명
    ThreeRows("상하 3단", 3, listOf(
        SplitSlot(0f, 0f, 1f, 1f / 3f),
        SplitSlot(0f, 1f / 3f, 1f, 2f / 3f),
        SplitSlot(0f, 2f / 3f, 1f, 1f),
    )),
    ThreeCols("좌우 3분할", 3, listOf(
        SplitSlot(0f, 0f, 1f / 3f, 1f),
        SplitSlot(1f / 3f, 0f, 2f / 3f, 1f),
        SplitSlot(2f / 3f, 0f, 1f, 1f),
    )),
    ThreeOneTopTwoBottom("상1·하좌우2", 3, listOf(
        SplitSlot(0f, 0f, 1f, 0.5f),
        SplitSlot(0f, 0.5f, 0.5f, 1f),
        SplitSlot(0.5f, 0.5f, 1f, 1f),
    )),
    ThreeTwoTopOneBottom("상좌우2·하1", 3, listOf(
        SplitSlot(0f, 0f, 0.5f, 0.5f),
        SplitSlot(0.5f, 0f, 1f, 0.5f),
        SplitSlot(0f, 0.5f, 1f, 1f),
    )),

    // 4명
    FourRows("상하 4단", 4, listOf(
        SplitSlot(0f, 0f, 1f, 0.25f),
        SplitSlot(0f, 0.25f, 1f, 0.5f),
        SplitSlot(0f, 0.5f, 1f, 0.75f),
        SplitSlot(0f, 0.75f, 1f, 1f),
    )),
    FourCols("좌우 4분할", 4, listOf(
        SplitSlot(0f, 0f, 0.25f, 1f),
        SplitSlot(0.25f, 0f, 0.5f, 1f),
        SplitSlot(0.5f, 0f, 0.75f, 1f),
        SplitSlot(0.75f, 0f, 1f, 1f),
    )),
    FourGrid("2×2", 4, listOf(
        SplitSlot(0f, 0f, 0.5f, 0.5f),
        SplitSlot(0.5f, 0f, 1f, 0.5f),
        SplitSlot(0f, 0.5f, 0.5f, 1f),
        SplitSlot(0.5f, 0.5f, 1f, 1f),
    ));

    // 슬롯 i 의 가로/세로 비(W/H). 합성물이 정사각이라 rect 비율이 곧 슬롯 비율.
    // 참가자 캔버스를 이 비율로 고정하면 합성 시 왜곡이 없다.
    fun slotAspect(index: Int): Float {
        val s = slots[index]
        return s.width / s.height
    }

    companion object {
        // 참가자 수에 맞는 레이아웃 목록. 호스트 페어링 화면 피커에 노출.
        fun layoutsFor(participantCount: Int): List<SplitLayout> =
            entries.filter { it.slotCount == participantCount }

        // 와이어(SplitStart.layoutId)로 주고받은 이름을 다시 enum 으로.
        fun byId(id: String): SplitLayout? = entries.firstOrNull { it.name == id }
    }
}
