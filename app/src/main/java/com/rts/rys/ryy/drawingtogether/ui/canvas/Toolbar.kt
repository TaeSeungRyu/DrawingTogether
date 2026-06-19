package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rts.rys.ryy.drawingtogether.drawing.model.BrushShape
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolKind
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolSettings

private val brushOptions: List<Pair<BrushShape, String>> = listOf(
    BrushShape.Round to "펜",
    BrushShape.Square to "마커",
    BrushShape.Highlighter to "형광펜",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Toolbar(
    tool: ToolSettings,
    canUndo: Boolean,
    onColor: (Int) -> Unit,
    onEraser: () -> Unit,
    onBrushShape: (BrushShape) -> Unit,
    onStrokeWidth: (Float) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val penSelected = tool.kind == ToolKind.Pen

    Surface(modifier = modifier, tonalElevation = 2.dp) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(tool.colorArgb))
                        .border(
                            width = if (penSelected) 3.dp else 1.dp,
                            color = if (penSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                            shape = CircleShape,
                        )
                        .clickable { pickerOpen = true },
                )
                Spacer(modifier = Modifier.width(12.dp))
                FilterChip(
                    selected = tool.kind == ToolKind.Eraser,
                    onClick = onEraser,
                    label = { Text("지우개") },
                )
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                brushOptions.forEachIndexed { i, (shape, label) ->
                    SegmentedButton(
                        selected = tool.shape == shape,
                        onClick = { onBrushShape(shape) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = brushOptions.size),
                        label = { Text(label) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
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
                TextButton(onClick = onUndo, enabled = canUndo) { Text("되돌리기") }
                TextButton(onClick = onClear) { Text("전체 지우기") }
            }
        }
    }

    if (pickerOpen) {
        ColorPickerSheet(
            initialColor = tool.colorArgb,
            onConfirm = { argb ->
                onColor(argb)
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
}
