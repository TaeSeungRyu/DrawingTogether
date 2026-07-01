package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.rts.rys.ryy.drawingtogether.drawing.model.CanvasAspect

private const val ASPECT_COLUMNS = 3

// 캔버스 비율 프리셋 선택 시트. 각 프리셋을 실제 비율의 작은 사각형 미리보기 + 라벨로 보여준다.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AspectRatioSheet(
    current: CanvasAspect,
    onSelect: (CanvasAspect) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "캔버스 비율",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 16.dp),
            )
            CanvasAspect.values().toList().chunked(ASPECT_COLUMNS).forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowItems.forEach { aspect ->
                        AspectCard(
                            aspect = aspect,
                            selected = aspect == current,
                            onClick = { onSelect(aspect) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(ASPECT_COLUMNS - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AspectCard(
    aspect: CanvasAspect,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = border,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 비율 미리보기 — 48dp 정사각 프레임 안에 실제 비율의 사각형을 맞춰 그린다.
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                val fill = MaterialTheme.colorScheme.primary
                val r = aspect.ratio
                val previewMod = when {
                    r == null -> Modifier.fillMaxWidth().fillMaxHeight() // 자유 = 프레임 꽉 채움
                    r >= 1f -> Modifier.fillMaxWidth().aspectRatio(r)
                    else -> Modifier.fillMaxHeight().aspectRatio(r, matchHeightConstraintsFirst = true)
                }
                Box(
                    modifier = previewMod
                        .clip(RoundedCornerShape(3.dp))
                        .background(fill.copy(alpha = if (r == null) 0.25f else 0.7f)),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = aspect.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// TopAppBar "비율" 버튼용 글리프 — 둥근 사각형 외곽선. 색은 버튼의 content 색을 따른다.
@Composable
fun AspectGlyph(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier) {
        val inset = size.minDimension * 0.18f
        val w = size.width - inset * 2
        val h = size.height - inset * 2
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(w, h),
            cornerRadius = CornerRadius(inset, inset),
            style = Stroke(width = size.minDimension * 0.09f),
        )
    }
}
