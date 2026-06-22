package com.rts.rys.ryy.drawingtogether.drawing.model

import kotlinx.serialization.Serializable

// 정규화 좌표. (0,0) = 캔버스 좌상단, (1,1) = 우하단.
// 두 기기 화면 크기가 다를 수 있으므로 픽셀이 아닌 비율로 저장.
@Serializable
data class Point(val x: Float, val y: Float)
