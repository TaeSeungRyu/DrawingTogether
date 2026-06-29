package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 안내선(가이드라인) 토글 — 중앙 십자선(독립 on/off) + 격자(6×6 / 18×18 택1).
// ShapeDropdownButton 과 같은 Surface + DropdownMenu 패턴. 항목은 체크 표시 토글.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideDropdownButton(
    cross: Boolean,
    grid: GuideGrid,
    onToggleCross: () -> Unit,
    onSelectGrid: (GuideGrid) -> Unit,
    modifier: Modifier = Modifier,
    symmetry: SymmetryMode = SymmetryMode.Off,
    onSelectSymmetry: (SymmetryMode) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val active = cross || grid != GuideGrid.None || symmetry != SymmetryMode.Off

    Box(modifier = modifier) {
        ToolIconButton(
            label = "안내선",
            selected = active,
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            GuideGlyph(modifier = Modifier.fillMaxSize())
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            @Composable
            fun CheckMark(checked: Boolean) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenuItem(
                text = { Text("중앙 십자선") },
                leadingIcon = { CheckMark(checked = cross) },
                onClick = { onToggleCross() },
            )
            listOf(GuideGrid.Cells6, GuideGrid.Cells18).forEach { g ->
                DropdownMenuItem(
                    text = { Text(g.label) },
                    leadingIcon = { CheckMark(checked = grid == g) },
                    onClick = { onSelectGrid(g) },
                )
            }
            HorizontalDivider()
            Text(
                text = "대칭",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp),
            )
            SymmetryMode.values().forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.label) },
                    leadingIcon = { CheckMark(checked = symmetry == m) },
                    onClick = { onSelectSymmetry(m) },
                )
            }
        }
    }
}
