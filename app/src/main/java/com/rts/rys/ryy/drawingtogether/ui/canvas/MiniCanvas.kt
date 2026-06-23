package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.rts.rys.ryy.drawingtogether.drawing.engine.CanvasState

// Phase 4-E: 모임 모드 미니 뷰.
// - pointerInput 미부착 → 탭/드래그 무시 (read-only)
// - 그 peer 의 사진 배경 + stroke 렌더 — 자기 사진은 자기 메인 + 다른 사람들이 보는 미니뷰에
//   나타남 (정책: 발신자 peerCanvases.background 에 적용됨).
// - 닉네임 라벨 상단
// - 빈 캔버스에는 placeholder 텍스트
// - 슬롯 안에 1:1 정사각형 letterbox — 자기 캔버스와 같은 비율이라 stroke 좌표 동일 모양.
@Composable
fun MiniCanvas(
    nick: String,
    state: CanvasState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(2.dp),
    ) {
        Text(
            text = nick,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
        // 슬롯 영역을 가득 채우는 Box. 그 안에 1:1 letterbox 가 중앙 정렬.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                val density = LocalDensity.current.density
                var canvasSize by remember { mutableStateOf(IntSize.Zero) }
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { canvasSize = it },
                ) {
                    // 사진 배경 (있으면) — Fit. 1:1 letterbox 안에 사진 비율로 다시 letterbox 가능.
                    state.background?.bitmap?.let { bg ->
                        drawImage(
                            image = bg,
                            srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                            srcSize = androidx.compose.ui.unit.IntSize(bg.width, bg.height),
                            dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                            dstSize = androidx.compose.ui.unit.IntSize(
                                canvasSize.width,
                                canvasSize.height,
                            ),
                        )
                    }
                    state.strokes.forEach { drawStroke(it, canvasSize, density) }
                    state.openStrokes.values.forEach { drawStroke(it, canvasSize, density) }
                }
                if (state.background == null &&
                    state.strokes.isEmpty() &&
                    state.openStrokes.isEmpty()
                ) {
                    Text(
                        text = "아직 그리지 않음",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.wrapContentSize(),
                    )
                }
            }
        }
    }
}

// 비어있는 모임 슬롯 placeholder. 호스트 광고 직후 / 조인자가 아직 한 명만 들어온 상태 등.
// 미니 뷰 영역을 미리 잡아두기 위해 항상 표시 — 새 조인자 들어와도 자기 캔버스 크기가 바뀌지 않음.
@Composable
fun EmptyMiniSlot(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(2.dp),
    ) {
        Text(
            text = "—",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "비어있음",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
