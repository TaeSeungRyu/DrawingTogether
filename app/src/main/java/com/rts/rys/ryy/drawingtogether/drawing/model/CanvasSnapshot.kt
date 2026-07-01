package com.rts.rys.ryy.drawingtogether.drawing.model

import kotlinx.serialization.Serializable

// "동기화" 응답으로 통째 전송되는 캔버스 내용. stroke + 스티커를 한 묶음으로 캡슐화해
// FILE 페이로드(CBOR)로 송신한다. 사진 배경은 별도 PhotoMeta/PhotoRemove 경로.
@Serializable
data class CanvasSnapshot(
    val strokes: List<Stroke>,
    val stickers: List<Sticker> = emptyList(),
    val texts: List<TextElement> = emptyList(),
    val aspect: CanvasAspect = CanvasAspect.Free,
)
