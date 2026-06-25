package com.rts.rys.ryy.drawingtogether.drawing.model

import kotlinx.serialization.Serializable

// 자체 벡터 스티커 세트. 픽셀이 아니라 key 로만 보관·전송하고, 렌더러(StickerRenderer)가
// key → 벡터로 치환한다. 색은 key 마다 고정(Candy Pop 테마) — Sticker 에 color 필드 없음.
// 양 단말에 동일 enum 이 번들돼 있어 key 만 전송하면 각자 같은 모양으로 렌더된다.
@Serializable
enum class StickerKey(val displayName: String) {
    Heart("하트"),
    Star("별"),
    Smile("스마일"),
    Flower("꽃"),
    Cloud("구름"),
    Sun("해"),
    Moon("달"),
    Rainbow("무지개"),
    Drop("물방울"),
    Lightning("번개"),
    Diamond("보석"),
    Sparkle("반짝이"),
}
