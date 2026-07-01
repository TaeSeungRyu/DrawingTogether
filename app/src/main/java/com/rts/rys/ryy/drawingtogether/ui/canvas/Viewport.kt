package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

// 캔버스 줌 상한. 1f = 원본, 5f = 5배 확대까지.
const val MAX_CANVAS_ZOOM = 5f

// 뷰포트 = scale(배율) + offset(콘텐츠 좌상단 px 이동량).
//   콘텐츠→화면: screen = content*scale + offset
//   화면→콘텐츠: content = (screen - offset)/scale
// 줌은 로컬 표시 전용 — 그리기 데이터(정규화 0..1)는 항상 콘텐츠 좌표 기준으로 저장한다.

// 화면 px 좌표를 콘텐츠 px 좌표로 역변환. 포인터 입력을 정규화하기 전에 적용.
fun Offset.screenToContent(scale: Float, offset: Offset): Offset =
    Offset((x - offset.x) / scale, (y - offset.y) / scale)

// offset 클램프 — 확대된 콘텐츠가 뷰를 항상 채우도록(가장자리 밖으로 안 밀림).
// scale<=1 이면 이동 의미 없음 → (0,0).
fun clampOffset(offset: Offset, scale: Float, size: IntSize): Offset {
    if (scale <= 1f || size.width <= 0 || size.height <= 0) return Offset.Zero
    val minX = size.width - size.width * scale // 음수 (좌로 밀 수 있는 한계)
    val minY = size.height - size.height * scale
    return Offset(
        offset.x.coerceIn(minX, 0f),
        offset.y.coerceIn(minY, 0f),
    )
}

// centroid(두 손가락 중심) 고정 확대 + pan. 반환 (newScale, newOffset) — clamp 적용됨.
// centroid 아래에 있던 콘텐츠 점이 확대 후에도 같은 화면 위치에 오도록 offset 을 보정한다.
fun zoomAround(
    scale: Float,
    offset: Offset,
    centroid: Offset,
    zoomFactor: Float,
    pan: Offset,
    size: IntSize,
    maxScale: Float = MAX_CANVAS_ZOOM,
): Pair<Float, Offset> {
    val newScale = (scale * zoomFactor).coerceIn(1f, maxScale)
    val effFactor = newScale / scale
    val newOffset = Offset(
        centroid.x - (centroid.x - offset.x) * effFactor + pan.x,
        centroid.y - (centroid.y - offset.y) * effFactor + pan.y,
    )
    return newScale to clampOffset(newOffset, newScale, size)
}
