package com.rts.rys.ryy.drawingtogether.ui.canvas

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
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
    var showSaveDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }

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
                // 좁은 폭(예: 갤럭시 S21) 에서 액션이 겹치면 가로 스크롤로 풀어준다.
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MergeBackgroundToggle(
                        checked = vm.canvas.mergeBackgroundOnSave,
                        onCheckedChange = vm::setMergeBackgroundOnSave,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
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
                        onClick = {
                            nameInput = ""
                            showSaveDialog = true
                        },
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
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

    if (showSaveDialog) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        val submit = submit@{
            val raw = nameInput.trim()
            if (raw.isEmpty()) return@submit
            showSaveDialog = false
            scope.launch {
                val includeBg = vm.canvas.mergeBackgroundOnSave
                val mergedBg = vm.canvas.background != null && includeBg
                val bitmap = PngComposer.compose(vm.canvas, density, includeBg)
                WorkStore.get(context).save(bitmap, mergedBg, raw)
                Toast.makeText(context, SAVED_TOAST_TEXT, Toast.LENGTH_SHORT).show()
            }
        }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("작품 저장") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it.take(40) },
                    singleLine = true,
                    label = { Text("이름") },
                    placeholder = { Text("예) 곰돌이") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.focusRequester(focus),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { submit() },
                    enabled = nameInput.trim().isNotEmpty(),
                ) { Text("저장") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("취소") }
            },
        )
    }
}
