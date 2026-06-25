package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.rts.rys.ryy.drawingtogether.drawing.model.ShapeMode
import kotlin.math.cos
import kotlin.math.sin

// 도형 path 빌더 — DrawingCanvas의 스탬프 렌더링과 ShapeIcon에서 공통 사용.

fun buildRegularPolygonPath(left: Float, top: Float, w: Float, h: Float, sides: Int): Path {
    require(sides >= 3) { "polygon needs at least 3 sides" }
    val cx = left + w / 2f
    val cy = top + h / 2f
    val r = minOf(w, h) / 2f
    return Path().apply {
        for (i in 0 until sides) {
            val angle = i.toDouble() * 2.0 * Math.PI / sides.toDouble() - Math.PI / 2.0
            val x = (cx + r * cos(angle)).toFloat()
            val y = (cy + r * sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

fun buildStarPath(left: Float, top: Float, w: Float, h: Float): Path {
    val cx = left + w / 2f
    val cy = top + h / 2f
    val outerR = minOf(w, h) / 2f
    val innerR = outerR * 0.4f
    return Path().apply {
        for (i in 0 until 10) {
            val angle = i.toDouble() * Math.PI / 5.0 - Math.PI / 2.0
            val r = if (i % 2 == 0) outerR else innerR
            val x = (cx + r * cos(angle)).toFloat()
            val y = (cy + r * sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

fun buildHeartPath(left: Float, top: Float, w: Float, h: Float): Path {
    val cx = left + w / 2f
    return Path().apply {
        moveTo(cx, top + h)
        cubicTo(
            left - w * 0.05f, top + h * 0.55f,
            left + w * 0.10f, top - h * 0.10f,
            cx, top + h * 0.25f,
        )
        cubicTo(
            left + w * 0.90f, top - h * 0.10f,
            left + w + w * 0.05f, top + h * 0.55f,
            cx, top + h,
        )
        close()
    }
}

@Composable
fun ShapeIcon(
    shape: ShapeMode,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val cx = w / 2f
        val cy = h / 2f
        val stroke = Stroke(
            width = minOf(w, h) * 0.10f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        when (shape) {
            ShapeMode.None -> {
                val path = Path().apply {
                    moveTo(w * 0.15f, cy)
                    cubicTo(
                        w * 0.32f, h * 0.15f,
                        w * 0.68f, h * 0.85f,
                        w * 0.85f, cy,
                    )
                }
                drawPath(path, color = tint, style = stroke)
            }
            ShapeMode.Circle -> {
                drawCircle(
                    color = tint,
                    center = Offset(cx, cy),
                    radius = minOf(w, h) * 0.4f,
                    style = stroke,
                )
            }
            ShapeMode.Rect -> {
                val side = minOf(w, h) * 0.78f
                drawRect(
                    color = tint,
                    topLeft = Offset(cx - side / 2f, cy - side / 2f),
                    size = Size(side, side),
                    style = stroke,
                )
            }
            ShapeMode.Triangle -> {
                val side = minOf(w, h) * 0.85f
                drawPath(
                    buildRegularPolygonPath(cx - side / 2f, cy - side / 2f, side, side, sides = 3),
                    color = tint,
                    style = stroke,
                )
            }
            ShapeMode.Pentagon -> {
                val side = minOf(w, h) * 0.85f
                drawPath(
                    buildRegularPolygonPath(cx - side / 2f, cy - side / 2f, side, side, sides = 5),
                    color = tint,
                    style = stroke,
                )
            }
            ShapeMode.Hexagon -> {
                val side = minOf(w, h) * 0.85f
                drawPath(
                    buildRegularPolygonPath(cx - side / 2f, cy - side / 2f, side, side, sides = 6),
                    color = tint,
                    style = stroke,
                )
            }
            ShapeMode.Star -> {
                val side = minOf(w, h) * 0.85f
                drawPath(
                    buildStarPath(cx - side / 2f, cy - side / 2f, side, side),
                    color = tint,
                    style = stroke,
                )
            }
            ShapeMode.Heart -> {
                val side = minOf(w, h) * 0.78f
                drawPath(
                    buildHeartPath(cx - side / 2f, cy - side / 2f, side, side),
                    color = tint,
                    style = stroke,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShapeDropdownButton(
    shape: ShapeMode,
    onShape: (ShapeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = shape != ShapeMode.None

    Box(modifier = modifier) {
        ToolIconButton(
            label = "도형",
            selected = active,
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 활성 시 현재 도형, 비활성 시 대표 도형(사각형)으로 "도형" 의미 표시.
            ShapeIcon(
                shape = if (active) shape else ShapeMode.Rect,
                modifier = Modifier.fillMaxSize(),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ShapeMode.values().forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.displayName) },
                    leadingIcon = {
                        ShapeIcon(shape = mode, modifier = Modifier.size(22.dp))
                    },
                    onClick = {
                        onShape(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}
