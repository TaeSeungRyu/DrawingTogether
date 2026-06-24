package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
) {
    var expanded by remember { mutableStateOf(false) }
    val active = cross || grid != GuideGrid.None
    val container = if (active)
        MaterialTheme.colorScheme.secondaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val border = if (active)
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    else
        null

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(percent = 50),
            color = container,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = border,
            modifier = Modifier.height(40.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp).fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = "안내선", style = MaterialTheme.typography.labelLarge)
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "안내선 선택",
                    modifier = Modifier.size(18.dp),
                )
            }
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
        }
    }
}
