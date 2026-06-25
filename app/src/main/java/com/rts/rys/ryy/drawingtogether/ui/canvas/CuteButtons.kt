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

// 상단 바 액션 버튼 — 아이콘 + 작은 라벨, 색상 컨테이너. 사진/촬영/제거/저장에 사용.
// TopAppBar(56dp) 안에 들어가도록 48dp 높이.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopActionButton(
    label: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = container,
        contentColor = content,
        modifier = modifier.height(48.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() }
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

// 사진(갤러리) 글리프 — 액자 + 해 + 산.
@Composable
fun PhotoGlyph(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val s = minOf(w, h)
        val sw = s * 0.08f
        val l = w * 0.14f; val t = h * 0.16f; val r = w * 0.86f; val b = h * 0.84f
        drawRoundRect(
            color = tint,
            topLeft = Offset(l, t),
            size = Size(r - l, b - t),
            cornerRadius = CornerRadius(s * 0.12f, s * 0.12f),
            style = Stroke(width = sw),
        )
        drawCircle(tint, radius = s * 0.09f, center = Offset(l + (r - l) * 0.30f, t + (b - t) * 0.30f), style = Stroke(sw * 0.8f))
        // 산 (삼각형 외곽선).
        val peak = Offset((l + r) / 2f, t + (b - t) * 0.42f)
        val baseL = Offset(l + (r - l) * 0.12f, b - sw)
        val baseR = Offset(r - (r - l) * 0.12f, b - sw)
        drawLine(tint, baseL, peak, sw, cap = StrokeCap.Round)
        drawLine(tint, peak, baseR, sw, cap = StrokeCap.Round)
    }
}

// 촬영(카메라) 글리프 — 뷰파인더 돌출 + 본체 + 렌즈.
@Composable
fun CameraGlyph(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val s = minOf(w, h)
        val sw = s * 0.08f
        val bumpW = w * 0.24f; val bumpH = h * 0.12f
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.22f, h * 0.14f),
            size = Size(bumpW, bumpH),
            cornerRadius = CornerRadius(sw, sw),
            style = Stroke(width = sw * 0.8f),
        )
        val bodyT = h * 0.24f
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.12f, bodyT),
            size = Size(w * 0.76f, h * 0.84f - bodyT),
            cornerRadius = CornerRadius(s * 0.10f, s * 0.10f),
            style = Stroke(width = sw),
        )
        drawCircle(tint, radius = s * 0.16f, center = Offset(w / 2f, (bodyT + h * 0.84f) / 2f), style = Stroke(sw))
    }
}

// 제거(휴지통) 글리프.
@Composable
fun TrashGlyph(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val sw = minOf(w, h) * 0.08f
        val lidY = h * 0.28f
        drawLine(tint, Offset(w * 0.18f, lidY), Offset(w * 0.82f, lidY), sw, cap = StrokeCap.Round)
        // 손잡이.
        drawLine(tint, Offset(w * 0.42f, lidY), Offset(w * 0.42f, lidY - h * 0.08f), sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.58f, lidY), Offset(w * 0.58f, lidY - h * 0.08f), sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.42f, lidY - h * 0.08f), Offset(w * 0.58f, lidY - h * 0.08f), sw, cap = StrokeCap.Round)
        // 몸통 (사다리꼴).
        val bodyTop = lidY + h * 0.06f; val b = h * 0.82f
        drawLine(tint, Offset(w * 0.26f, bodyTop), Offset(w * 0.30f, b), sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.74f, bodyTop), Offset(w * 0.70f, b), sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.30f, b), Offset(w * 0.70f, b), sw, cap = StrokeCap.Round)
        // 내부 세로선.
        drawLine(tint, Offset(w * 0.42f, bodyTop + h * 0.05f), Offset(w * 0.42f, b - h * 0.05f), sw * 0.7f, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.58f, bodyTop + h * 0.05f), Offset(w * 0.58f, b - h * 0.05f), sw * 0.7f, cap = StrokeCap.Round)
    }
}

// 저장 글리프 — 트레이로 내려받는 화살표.
@Composable
fun SaveGlyph(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val sw = minOf(w, h) * 0.09f
        val cx = w / 2f
        drawLine(tint, Offset(cx, h * 0.16f), Offset(cx, h * 0.58f), sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(cx - w * 0.16f, h * 0.42f), Offset(cx, h * 0.60f), sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(cx + w * 0.16f, h * 0.42f), Offset(cx, h * 0.60f), sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.22f, h * 0.82f), Offset(w * 0.78f, h * 0.82f), sw, cap = StrokeCap.Round)
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

