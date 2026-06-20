package com.rts.rys.ryy.drawingtogether.ui.canvas

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rts.rys.ryy.drawingtogether.drawing.model.BackgroundImage
import com.rts.rys.ryy.drawingtogether.photo.CameraCaptureFile
import com.rts.rys.ryy.drawingtogether.photo.PhotoLoader
import com.rts.rys.ryy.drawingtogether.works.PngComposer
import com.rts.rys.ryy.drawingtogether.works.WorkStore
import kotlinx.coroutines.launch

private const val ASPECT_TOAST_TEXT = "사진 비율로 화면을 맞췄어요"
private const val SAVED_TOAST_TEXT = "작품을 저장했어요"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: DrawingViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    var showSaveConfirm by remember { mutableStateOf(false) }

    // 갤러리 선택 — 권한 불필요 (Android PhotoPicker)
    val pickPhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    PhotoLoader.load(context, uri, BackgroundImage.Source.Gallery)
                }.onSuccess {
                    vm.setBackground(it)
                    Toast.makeText(context, ASPECT_TOAST_TEXT, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 카메라 촬영 — 우리 매니페스트엔 CAMERA 권한 선언 안 함 (시스템 카메라 앱이 처리)
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val capturePhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            scope.launch {
                runCatching {
                    PhotoLoader.load(context, uri, BackgroundImage.Source.Camera)
                }.onSuccess {
                    vm.setBackground(it)
                    Toast.makeText(context, ASPECT_TOAST_TEXT, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "뒤로",
                    )
                }
            },
            actions = {
                CuteToolButton(
                    text = "사진",
                    onClick = {
                        pickPhoto.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.width(6.dp))
                CuteToolButton(
                    text = "촬영",
                    onClick = {
                        val uri = CameraCaptureFile.create(context)
                        pendingCameraUri = uri
                        capturePhoto.launch(uri)
                    },
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                if (vm.canvas.background != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    CuteToolButton(
                        text = "제거",
                        onClick = { vm.setBackground(null) },
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                CuteToolButton(
                    text = "저장",
                    onClick = { showSaveConfirm = true },
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.width(8.dp))
            },
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val bg = vm.canvas.background
            val canvasModifier = if (bg != null) {
                Modifier
                    .aspectRatio(bg.aspectRatio)
                    .background(Color.White)
            } else {
                Modifier
                    .fillMaxSize()
                    .background(Color.White)
            }
            DrawingCanvas(
                state = vm.canvas,
                tool = vm.tool,
                onStrokeStart = vm::strokeStart,
                onStrokeAppend = vm::strokeAppend,
                onStrokeEnd = vm::strokeEnd,
                modifier = canvasModifier,
            )
        }

        Toolbar(
            tool = vm.tool,
            canUndo = vm.canvas.canUndo,
            onColor = vm::selectColor,
            onEraser = vm::toggleEraser,
            onBrush = vm::setBrush,
            onShape = vm::setShape,
            onStrokeWidth = vm::setStrokeWidth,
            onUndo = vm::undoLastLocal,
            onClear = vm::clearAll,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showSaveConfirm) {
        AlertDialog(
            onDismissRequest = { showSaveConfirm = false },
            title = { Text("작품 저장") },
            text = { Text("현재 캔버스를 작품으로 저장할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    showSaveConfirm = false
                    scope.launch {
                        val hasBg = vm.canvas.background != null
                        val bitmap = PngComposer.compose(vm.canvas, density)
                        WorkStore.get(context).save(bitmap, hasBg)
                        Toast.makeText(context, SAVED_TOAST_TEXT, Toast.LENGTH_SHORT).show()
                    }
                }) { Text("저장") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirm = false }) { Text("취소") }
            },
        )
    }
}
