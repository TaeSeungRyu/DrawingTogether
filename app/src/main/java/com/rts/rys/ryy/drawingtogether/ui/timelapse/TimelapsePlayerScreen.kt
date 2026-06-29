package com.rts.rys.ryy.drawingtogether.ui.timelapse

import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.rts.rys.ryy.drawingtogether.drawing.engine.CanvasState
import com.rts.rys.ryy.drawingtogether.drawing.model.BackgroundImage
import com.rts.rys.ryy.drawingtogether.drawing.model.Timelapse
import com.rts.rys.ryy.drawingtogether.drawing.model.TimelapseOp
import com.rts.rys.ryy.drawingtogether.ui.canvas.drawSticker
import com.rts.rys.ryy.drawingtogether.ui.canvas.drawStroke
import com.rts.rys.ryy.drawingtogether.works.TimelapseStore
import com.rts.rys.ryy.drawingtogether.works.TimelapseVideoExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private class LoadedTimelapse(val log: Timelapse, val backgrounds: Map<String, BackgroundImage>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelapsePlayerScreen(id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { TimelapseStore.get(context) }
    var exportProgress by remember { mutableStateOf<Float?>(null) }   // null = 비-내보내기 중

    val loaded by produceState<LoadedTimelapse?>(initialValue = null, id) {
        value = withContext(Dispatchers.IO) {
            val log = store.loadLog(id) ?: return@withContext null
            val refs = log.entries
                .mapNotNull { (it.op as? TimelapseOp.BackgroundPhoto)?.ref }
                .toSet()
            val bg = refs.mapNotNull { ref ->
                decodeBackground(store.backgroundFile(id, ref))?.let { ref to it }
            }.toMap()
            LoadedTimelapse(log, bg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("타임랩스") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    TextButton(
                        enabled = exportProgress == null,
                        onClick = {
                            scope.launch {
                                exportProgress = 0f
                                val uri = runCatching {
                                    TimelapseVideoExporter.exportToGallery(context, id) { p ->
                                        exportProgress = p
                                    }
                                }.getOrNull()
                                exportProgress = null
                                if (uri != null) {
                                    Toast.makeText(context, "갤러리에 저장했어요", Toast.LENGTH_SHORT).show()
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "video/mp4"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(share, "타임랩스 공유"))
                                } else {
                                    Toast.makeText(context, "내보내기에 실패했어요", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    ) { Text("내보내기") }
                    TextButton(
                        enabled = exportProgress == null,
                        onClick = {
                            scope.launch {
                                store.delete(id)
                                onBack()
                            }
                        },
                    ) { Text("삭제") }
                },
            )
        },
    ) { padding ->
        val data = loaded
        if (data == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("불러오는 중…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        val canvas = remember(data) { CanvasState() }
        val player = remember(data) {
            TimelapsePlayer(
                canvas = canvas,
                entries = data.log.entries,
                durationMs = data.log.durationMs,
                loadBackground = { ref -> data.backgrounds[ref] },
            )
        }
        // 첫 프레임 — 시작 상태로 rebuild.
        LaunchedEffect(player) { player.rebuildTo(0L) }
        // 재생 루프.
        LaunchedEffect(player.isPlaying) { if (player.isPlaying) player.run() }

        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                ReplayCanvas(canvas = canvas, modifier = Modifier.weight(1f).fillMaxWidth())
                PlaybackControls(player = player)
            }
            exportProgress?.let { p ->
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Text(
                            text = "내보내는 중 ${(p * 100).toInt()}%",
                            color = Color.White,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

// 읽기 전용 캔버스 — DrawingCanvas 와 같은 렌더러 재사용, 입력(pointerInput) 없음.
@Composable
private fun ReplayCanvas(canvas: CanvasState, modifier: Modifier = Modifier) {
    val bg = canvas.background
    val sizeModifier = if (bg != null) Modifier.aspectRatio(bg.aspectRatio) else Modifier.fillMaxSize()
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = sizeModifier.background(Color(canvas.backgroundColor))) {
            val density = LocalDensity.current.density
            var size by remember { androidx.compose.runtime.mutableStateOf(IntSize.Zero) }
            Canvas(modifier = Modifier.fillMaxSize().onSizeChanged { size = it }) {
                canvas.background?.bitmap?.let { image ->
                    drawImage(
                        image = image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(image.width, image.height),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width, size.height),
                    )
                }
                canvas.strokes.forEach { drawStroke(it, size, density) }
                canvas.openStrokes.values.forEach { drawStroke(it, size, density) }
                canvas.stickers.forEach { drawSticker(it, size) }
            }
        }
    }
}

@Composable
private fun PlaybackControls(player: TimelapsePlayer) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 드래그 중에는 썸네일 위치만 바꾸고(scrub), 손을 뗄 때 한 번만 rebuild — 매 프레임
        // 0부터 재구성하던 끊김 제거.
        var scrubMs by remember { mutableStateOf<Float?>(null) }
        Slider(
            value = scrubMs ?: player.positionMs.toFloat(),
            onValueChange = { scrubMs = it },
            onValueChangeFinished = {
                scrubMs?.let { player.seekTo(it.toLong()) }
                scrubMs = null
            },
            valueRange = 0f..player.durationMs.coerceAtLeast(1L).toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "${fmt((scrubMs ?: player.positionMs.toFloat()).toLong())} / ${fmt(player.durationMs)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { player.seekTo(0L) }) { Text("처음으로") }
            TextButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                Text(if (player.isPlaying) "일시정지" else "재생", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = {
                val next = TimelapsePlayer.SPEEDS[
                    (TimelapsePlayer.SPEEDS.indexOf(player.speed) + 1) % TimelapsePlayer.SPEEDS.size
                ]
                player.speed = next
            }) { Text("${player.speed.toInt()}x") }
        }
    }
}

private fun fmt(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

// 배경 PNG 파일 → BackgroundImage. 재생 시작 전 IO 에서 일괄 디코드.
private fun decodeBackground(file: File): BackgroundImage? {
    if (!file.exists()) return null
    val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
    return BackgroundImage(
        bitmap = bmp.asImageBitmap(),
        widthPx = bmp.width,
        heightPx = bmp.height,
        source = BackgroundImage.Source.Gallery,
    )
}
