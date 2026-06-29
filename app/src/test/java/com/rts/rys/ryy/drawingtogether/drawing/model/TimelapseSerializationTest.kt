package com.rts.rys.ryy.drawingtogether.drawing.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.Assert.assertEquals
import org.junit.Test

// 타임랩스 로그의 CBOR 라운드트립 — 특히 TimelapseOp.Draw 안에 sealed DrawingEvent 가
// 다형 직렬화되는지(가장 큰 위험) 확인. TimelapseStore 가 쓰는 직렬화 경로와 동일.
@OptIn(ExperimentalSerializationApi::class)
class TimelapseSerializationTest {

    private val cbor = Cbor { ignoreUnknownKeys = true }

    @Test
    fun mixedOpsRoundtrip() {
        val tool = ToolSettings(
            kind = ToolKind.Pen,
            colorArgb = 0xFF112233.toInt(),
            strokeWidthDp = 4f,
            brush = BrushType.Neon,
            shape = ShapeMode.None,
        )
        val src = Timelapse(
            id = "tl-1",
            createdAtEpochMs = 1_700_000_000_000L,
            durationMs = 1234L,
            entries = listOf(
                TimelapseEntry(0L, TimelapseOp.BackgroundColor(0xFFFFFFFF.toInt())),
                TimelapseEntry(
                    0L,
                    TimelapseOp.Snapshot(
                        strokes = listOf(
                            Stroke(
                                id = StrokeId("pre-1"),
                                authorId = PeerId("local"),
                                tool = tool,
                                points = listOf(Point(0.7f, 0.7f), Point(0.8f, 0.8f)),
                            ),
                        ),
                        stickers = listOf(
                            Sticker(
                                id = StickerId("st-1"),
                                authorId = PeerId("local"),
                                key = StickerKey.Heart,
                                cx = 0.5f, cy = 0.5f, scale = 0.2f, rotationDeg = 0f,
                            ),
                        ),
                    ),
                ),
                TimelapseEntry(10L, TimelapseOp.BackgroundPhoto("bg-0")),
                TimelapseEntry(
                    20L,
                    TimelapseOp.Draw(
                        DrawingEvent.StrokeStart(
                            seq = 1L,
                            authorId = PeerId("local"),
                            strokeId = StrokeId("s1"),
                            tool = tool,
                            point = Point(0.1f, 0.2f),
                        ),
                    ),
                ),
                TimelapseEntry(
                    40L,
                    TimelapseOp.Draw(
                        DrawingEvent.StrokeAppend(
                            seq = 2L,
                            authorId = PeerId("local"),
                            strokeId = StrokeId("s1"),
                            points = listOf(Point(0.3f, 0.4f), Point(0.5f, 0.6f)),
                        ),
                    ),
                ),
                TimelapseEntry(
                    60L,
                    TimelapseOp.Draw(
                        DrawingEvent.StrokeEnd(seq = 3L, authorId = PeerId("local"), strokeId = StrokeId("s1")),
                    ),
                ),
                TimelapseEntry(80L, TimelapseOp.BackgroundPhoto(null)),
            ),
        )

        val back = cbor.decodeFromByteArray<Timelapse>(cbor.encodeToByteArray(src))
        assertEquals(src, back)
    }
}
