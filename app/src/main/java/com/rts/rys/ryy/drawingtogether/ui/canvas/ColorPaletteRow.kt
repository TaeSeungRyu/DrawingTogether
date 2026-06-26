package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

// 자주 쓰는 색 프리셋 7개 — 검정/흰색/빨강/파랑/초록/노랑/갈색. 그 외 색은 + (임시) 또는
// ✏️ (슬롯 편집). UserPaletteRepo 가 사용자 정의 슬롯 색을 prefs 에 보관.
val DefaultColorPalette: List<Int> = listOf(
    0xFF000000.toInt(), // black
    0xFFFFFFFF.toInt(), // white
    0xFFE53935.toInt(), // red
    0xFF1E88E5.toInt(), // blue
    0xFF43A047.toInt(), // green
    0xFFFDD835.toInt(), // yellow
    0xFF6D4C41.toInt(), // brown
)

@Composable
fun ColorPaletteRow(
    currentColor: Int,
    isPenSelected: Boolean,
    onColor: (Int) -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier,
    eyedropperActive: Boolean = false,
    onEyedropper: () -> Unit = {},
    presets: List<Int> = DefaultColorPalette,
    // 편집 모드 — 슬롯 탭 시 onColor 대신 onEditSlot 호출.
    editing: Boolean = false,
    onToggleEdit: () -> Unit = {},
    onEditSlot: (index: Int, currentArgb: Int) -> Unit = { _, _ -> },
    onResetPalette: () -> Unit = {},
) {
    val scrollState = rememberLazyListState()
    val fadeColor = MaterialTheme.colorScheme.surface
    LazyRow(
        state = scrollState,
        modifier = modifier.fadingEdgeHorizontal(
            leftFade = scrollState.canScrollBackward,
            rightFade = scrollState.canScrollForward,
            fadeColor = fadeColor,
        ),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(presets) { index, argb ->
            val selected = !editing && isPenSelected && argb == currentColor
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        width = if (selected || editing) 3.dp else 1.dp,
                        color = when {
                            selected -> MaterialTheme.colorScheme.primary
                            editing -> MaterialTheme.colorScheme.tertiary
                            else -> Color.LightGray
                        },
                        shape = CircleShape,
                    )
                    .clickable {
                        if (editing) onEditSlot(index, argb) else onColor(argb)
                    },
            )
        }
        if (!editing) {
            item { CustomColorButton(onClick = onCustom) }
            item { EyedropperButton(active = eyedropperActive, onClick = onEyedropper) }
        }
        item { EditToggleButton(editing = editing, onClick = onToggleEdit) }
        if (editing) {
            item { ResetButton(onClick = onResetPalette) }
        }
    }
}

@Composable
private fun CustomColorButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFFE53935),
                        Color(0xFFFDD835),
                        Color(0xFF43A047),
                        Color(0xFF00ACC1),
                        Color(0xFF1E88E5),
                        Color(0xFF8E24AA),
                        Color(0xFFE53935),
                    ),
                ),
            )
            .border(1.dp, Color.LightGray, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "사용자 정의 색상",
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

// 스포이드 토글 버튼 — 활성 시 강조 색. 글리프는 대각 스포이드(튜브 + 끝 방울)를 직접 그림
// (material-icons-extended 의존성 없이).
@Composable
private fun EyedropperButton(active: Boolean, onClick: () -> Unit) {
    val container = if (active) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.surfaceVariant
    val content = if (active) MaterialTheme.colorScheme.onTertiary
                  else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(container)
            .border(1.dp, Color.LightGray, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val s = size.minDimension
            val cap = s * 0.22f
            // 손잡이(우상단) → 끝(좌하단) 대각선 튜브.
            drawLine(
                color = content,
                start = Offset(s * 0.78f, s * 0.22f),
                end = Offset(s * 0.34f, s * 0.66f),
                strokeWidth = s * 0.18f,
                cap = StrokeCap.Round,
            )
            // 누름부(둥근 머리).
            drawCircle(
                color = content,
                radius = cap * 0.55f,
                center = Offset(s * 0.82f, s * 0.18f),
            )
            // 끝 방울(삼각 팁).
            val tip = Path().apply {
                moveTo(s * 0.34f, s * 0.62f)
                lineTo(s * 0.18f, s * 0.86f)
                lineTo(s * 0.40f, s * 0.70f)
                close()
            }
            drawPath(tip, content)
        }
    }
}

@Composable
private fun EditToggleButton(editing: Boolean, onClick: () -> Unit) {
    val container = if (editing) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.surfaceVariant
    val content = if (editing) MaterialTheme.colorScheme.onTertiary
                  else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(container)
            .border(1.dp, Color.LightGray, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (editing) Icons.Default.Check else Icons.Default.Edit,
            contentDescription = if (editing) "편집 완료" else "팔레트 편집",
            tint = content,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ResetButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.errorContainer)
            .border(1.dp, Color.LightGray, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "기본 팔레트로 되돌리기",
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(20.dp),
        )
    }
}
