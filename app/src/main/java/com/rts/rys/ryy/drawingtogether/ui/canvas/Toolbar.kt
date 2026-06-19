package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rts.rys.ryy.drawingtogether.drawing.model.BrushType
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolKind
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolSettings

@Composable
fun Toolbar(
    tool: ToolSettings,
    canUndo: Boolean,
    onColor: (Int) -> Unit,
    onEraser: () -> Unit,
    onBrush: (BrushType) -> Unit,
    onStrokeWidth: (Float) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var colorPickerOpen by remember { mutableStateOf(false) }
    var brushSheetOpen by remember { mutableStateOf(false) }

    Surface(modifier = modifier, tonalElevation = 2.dp) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {

            ColorPaletteRow(
                currentColor = tool.colorArgb,
                isPenSelected = tool.kind == ToolKind.Pen,
                onColor = onColor,
                onCustom = { colorPickerOpen = true },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BrushTriggerButton(
                    brush = tool.brush,
                    onClick = { brushSheetOpen = true },
                )
                EraserToggle(
                    selected = tool.kind == ToolKind.Eraser,
                    onClick = onEraser,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "굵기 ${tool.strokeWidthDp.toInt()}dp",
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = tool.strokeWidthDp,
                    onValueChange = onStrokeWidth,
                    valueRange = 1f..32f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                CuteToolButton(
                    text = "되돌리기",
                    onClick = onUndo,
                    enabled = canUndo,
                )
                Spacer(modifier = Modifier.width(8.dp))
                CuteToolButton(
                    text = "전체 지우기",
                    onClick = onClear,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }

    if (colorPickerOpen) {
        ColorPickerSheet(
            initialColor = tool.colorArgb,
            onConfirm = { argb ->
                onColor(argb)
                colorPickerOpen = false
            },
            onDismiss = { colorPickerOpen = false },
        )
    }

    if (brushSheetOpen) {
        BrushSelectorSheet(
            currentBrush = tool.brush,
            onSelect = onBrush,
            onDismiss = { brushSheetOpen = false },
        )
    }
}
