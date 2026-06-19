package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DrawingScreen(
    modifier: Modifier = Modifier,
    vm: DrawingViewModel = viewModel(),
) {
    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            color = Color.White,
        ) {
            DrawingCanvas(
                state = vm.canvas,
                tool = vm.tool,
                onStrokeStart = vm::strokeStart,
                onStrokeAppend = vm::strokeAppend,
                onStrokeEnd = vm::strokeEnd,
            )
        }
        Toolbar(
            tool = vm.tool,
            canUndo = vm.canvas.canUndo,
            onColor = vm::selectColor,
            onEraser = vm::selectEraser,
            onBrushShape = vm::setBrushShape,
            onStrokeWidth = vm::setStrokeWidth,
            onUndo = vm::undoLastLocal,
            onClear = vm::clearAll,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
