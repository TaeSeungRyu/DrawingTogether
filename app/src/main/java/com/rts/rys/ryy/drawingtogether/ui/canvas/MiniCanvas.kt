package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
// - 사진 배경 무시 — stroke 만 렌더 (자기 사진은 자기만 본다는 모임 모드 정책)
// - 닉네임 라벨 상단
// - 빈 캔버스에는 placeholder 텍스트
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
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                state.strokes.forEach { drawStroke(it, canvasSize, density) }
                // 진행 중 stroke 도 표시 — 상대가 그리는 중인 게 보임.
                state.openStrokes.values.forEach { drawStroke(it, canvasSize, density) }
            }
            if (state.strokes.isEmpty() && state.openStrokes.isEmpty()) {
                Text(
                    text = "아직 그리지 않음",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
