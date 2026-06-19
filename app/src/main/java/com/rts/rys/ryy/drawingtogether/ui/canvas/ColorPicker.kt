package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

private fun hsvColor(h: Float, s: Float, v: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    initialColor: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    val initialHsv = remember(initialColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) }
    }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var value by remember { mutableStateOf(initialHsv[2]) }
    val current = hsvColor(hue, saturation, value)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("색상 선택", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            SaturationValueBox(
                hue = hue,
                saturation = saturation,
                value = value,
                onChange = { s, v -> saturation = s; value = v },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f),
            )

            Spacer(modifier = Modifier.height(16.dp))

            HueSlider(
                hue = hue,
                onChange = { hue = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(current)
                    .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("취소") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onConfirm(current.toArgb()) }) { Text("확인") }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SaturationValueBox(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (s: Float, v: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hueOnly = remember(hue) { hsvColor(hue, 1f, 1f) }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.coerceAtLeast(1).toFloat()
                    val h = size.height.coerceAtLeast(1).toFloat()
                    fun emit(pos: Offset) {
                        val s = (pos.x / w).coerceIn(0f, 1f)
                        val v = (1f - pos.y / h).coerceIn(0f, 1f)
                        onChange(s, v)
                    }
                    emit(first.position)
                    first.consume()
                    while (true) {
                        val ev = awaitPointerEvent()
                        var pressed = false
                        ev.changes.forEach { change ->
                            if (change.pressed) {
                                pressed = true
                                if (change.positionChanged()) {
                                    emit(change.position)
                                    change.consume()
                                }
                            }
                        }
                        if (!pressed) break
                    }
                }
            },
    ) {
        // 1. 순색(hue, S=1, V=1)
        drawRect(color = hueOnly)
        // 2. 가로: 좌측 흰색 → 우측 투명. saturation 그라데이션.
        drawRect(brush = Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
        // 3. 세로: 상단 투명 → 하단 검정. value 그라데이션.
        drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

        // 인디케이터 — 흰 외곽 + 검정 내부 링으로 어느 배경에서나 보이게.
        val cx = saturation * size.width
        val cy = (1f - value) * size.height
        val r = 9.dp.toPx()
        drawCircle(color = Color.White, center = Offset(cx, cy), radius = r, style = Stroke(width = 3.dp.toPx()))
        drawCircle(color = Color.Black, center = Offset(cx, cy), radius = r, style = Stroke(width = 1.dp.toPx()))
    }
}

@Composable
private fun HueSlider(
    hue: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hueColors = remember {
        listOf(0, 60, 120, 180, 240, 300, 360).map { hsvColor(it.toFloat(), 1f, 1f) }
    }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.coerceAtLeast(1).toFloat()
                    fun emit(pos: Offset) {
                        val h = (pos.x / w * 360f).coerceIn(0f, 360f)
                        onChange(h)
                    }
                    emit(first.position)
                    first.consume()
                    while (true) {
                        val ev = awaitPointerEvent()
                        var pressed = false
                        ev.changes.forEach { change ->
                            if (change.pressed) {
                                pressed = true
                                if (change.positionChanged()) {
                                    emit(change.position)
                                    change.consume()
                                }
                            }
                        }
                        if (!pressed) break
                    }
                }
            },
    ) {
        drawRect(brush = Brush.horizontalGradient(hueColors))

        val cx = (hue / 360f) * size.width
        val cy = size.height / 2f
        val r = size.height / 2f - 2.dp.toPx()
        drawCircle(color = Color.White, center = Offset(cx, cy), radius = r, style = Stroke(width = 3.dp.toPx()))
        drawCircle(color = Color.Black, center = Offset(cx, cy), radius = r, style = Stroke(width = 1.dp.toPx()))
    }
}
