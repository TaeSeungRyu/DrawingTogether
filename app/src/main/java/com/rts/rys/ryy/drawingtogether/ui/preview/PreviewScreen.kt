package com.rts.rys.ryy.drawingtogether.ui.preview

import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.rts.rys.ryy.drawingtogether.works.WorkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    workId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { WorkStore.get(context) }
    val file = remember(workId) { store.pngFile(workId) }
    val works by store.works.collectAsState()
    val work = works.firstOrNull { it.id == workId }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, workId) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = work?.name?.takeIf { it.isNotBlank() } ?: "저장된 작품",
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "뒤로",
                    )
                }
            },
            actions = {
                if (work != null) {
                    TextButton(onClick = {
                        scope.launch {
                            val result = runCatching { store.exportToGallery(context, work) }
                            val msg = result.fold(
                                onSuccess = { "갤러리에 저장했어요" },
                                onFailure = { "저장 실패: ${it.message}" },
                            )
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("갤러리로 보내기") }
                    TextButton(onClick = {
                        val uri = store.shareUriFor(context, work)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "작품 공유"))
                    }) { Text("공유") }
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("삭제", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val current = bitmap
            when {
                current != null -> Image(
                    bitmap = current,
                    contentDescription = "저장된 작품",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                !file.exists() -> Text(
                    text = "작품을 찾을 수 없어요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> CircularProgressIndicator()
            }
        }
    }

    if (showDeleteConfirm && work != null) {
        val label = work.name.takeIf { it.isNotBlank() } ?: "이 작품"
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("작품 삭제") },
            text = { Text("\"$label\"을(를) 삭제할까요? 되돌릴 수 없어요. (갤러리로 내보낸 사본은 남습니다.)") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        store.delete(workId)
                        onBack()
                    }
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("취소") }
            },
        )
    }
}
