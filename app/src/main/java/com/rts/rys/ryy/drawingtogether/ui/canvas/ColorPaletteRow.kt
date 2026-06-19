package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 자주 쓰는 12색 프리셋. 무지개 + 흑/회/갈색 구성.
val DefaultColorPalette: List<Int> = listOf(
    0xFF000000.toInt(), // black
    0xFFE53935.toInt(), // red
    0xFFFB8C00.toInt(), // orange
    0xFFFDD835.toInt(), // yellow
    0xFF43A047.toInt(), // green
    0xFF00897B.toInt(), // teal
    0xFF1E88E5.toInt(), // blue
    0xFF5E35B1.toInt(), // deep purple
    0xFF8E24AA.toInt(), // purple
    0xFFEC407A.toInt(), // pink
    0xFF6D4C41.toInt(), // brown
    0xFF757575.toInt(), // gray
)

@Composable
fun ColorPaletteRow(
    currentColor: Int,
    isPenSelected: Boolean,
    onColor: (Int) -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier,
    presets: List<Int> = DefaultColorPalette,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(presets) { argb ->
            val selected = isPenSelected && argb == currentColor
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.LightGray,
                        shape = CircleShape,
                    )
                    .clickable { onColor(argb) },
            )
        }
        item {
            CustomColorButton(onClick = onCustom)
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
