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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolKind
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolSettings

private val palette: List<Int> = listOf(
    0xFF000000.toInt(),
    0xFFD32F2F.toInt(),
    0xFF1976D2.toInt(),
    0xFF388E3C.toInt(),
    0xFFFBC02D.toInt(),
    0xFF7B1FA2.toInt(),
)

@Composable
fun Toolbar(
    tool: ToolSettings,
    canUndo: Boolean,
    onColor: (Int) -> Unit,
    onEraser: () -> Unit,
    onStrokeWidth: (Float) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, tonalElevation = 2.dp) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                palette.forEach { argb ->
                    val selected = tool.kind == ToolKind.Pen && tool.colorArgb == argb
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(32.dp)
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
                Spacer(modifier = Modifier.width(4.dp))
                FilterChip(
                    selected = tool.kind == ToolKind.Eraser,
                    onClick = onEraser,
                    label = { Text("지우개") },
                )
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
}
