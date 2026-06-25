package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

// 알약(pill) 모양 토널 액션 버튼. 시스템 TextButton보다 부드럽고 친근한 느낌.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuteToolButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val resolvedContainer = if (enabled) container else container.copy(alpha = 0.4f)
    val resolvedContent = if (enabled) content else content.copy(alpha = 0.4f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(percent = 50),
        color = resolvedContainer,
        contentColor = resolvedContent,
        modifier = modifier.height(40.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 18.dp).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

// 도구 줄의 표준 아이콘 버튼 — 아이콘 + 작은 라벨, selected 일 때 강조(채움+외곽선).
// 도형/안내선 드롭다운 트리거와 펜/스티커/지우개 버튼이 모두 이 형태로 통일된다.
// 도구 줄에서 Modifier.weight(1f) 로 균등 배치 → 가로 스크롤 없이 한 줄에 전부.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolIconButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val container = if (selected)
        MaterialTheme.colorScheme.secondaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val border = if (selected)
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    else
        null
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = container,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = border,
        modifier = modifier.height(54.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 2.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(modifier = Modifier.size(26.dp), contentAlignment = Alignment.Center) { icon() }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

// 지우개 글리프 — 살짝 기운 둥근 사각형 + 고무/홀더 경계선.
@Composable
fun EraserGlyph(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        rotate(degrees = -20f) {
            val bw = w * 0.70f
            val bh = h * 0.46f
            val left = (w - bw) / 2f
            val top = (h - bh) / 2f
            val r = bh * 0.30f
            drawRoundRect(
                color = tint.copy(alpha = 0.92f),
                topLeft = Offset(left, top),
                size = Size(bw, bh),
                cornerRadius = CornerRadius(r, r),
            )
            // 고무/플라스틱 홀더 경계선.
            val divY = top + bh * 0.55f
            drawLine(
                color = Color.White.copy(alpha = 0.8f),
                start = Offset(left, divY),
                end = Offset(left + bw, divY),
                strokeWidth = h * 0.04f,
            )
        }
    }
}

// 안내선 글리프 — 3×3 격자.
@Composable
fun GuideGlyph(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val pad = w * 0.12f
        val left = pad
        val top = pad
        val right = w - pad
        val bottom = h - pad
        val thin = w * 0.045f
        val color = tint.copy(alpha = 0.85f)
        drawRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            style = Stroke(width = thin),
        )
        for (i in 1..2) {
            val x = left + (right - left) * i / 3f
            drawLine(color, Offset(x, top), Offset(x, bottom), strokeWidth = thin, cap = StrokeCap.Round)
            val y = top + (bottom - top) * i / 3f
            drawLine(color, Offset(left, y), Offset(right, y), strokeWidth = thin, cap = StrokeCap.Round)
        }
    }
}

// 저장 시 사진 배경을 PNG에 합칠지 켜고 끄는 토글. 사진 추가 전에도 켤 수 있도록 항상 노출.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeBackgroundToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (checked)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (checked)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant
    val border = if (checked)
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    else
        null
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(percent = 50),
        color = container,
        contentColor = contentColor,
        border = border,
        modifier = modifier.height(40.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (checked) "배경 합치기 ON" else "배경 합치기 OFF",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

