package com.rts.rys.ryy.drawingtogether.works

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.rts.rys.ryy.drawingtogether.drawing.engine.CanvasState
import com.rts.rys.ryy.drawingtogether.drawing.model.BackgroundImage
import com.rts.rys.ryy.drawingtogether.drawing.model.TimelapseOp
import com.rts.rys.ryy.drawingtogether.ui.canvas.drawSticker
import com.rts.rys.ryy.drawingtogether.ui.canvas.drawStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// 타임랩스 로그 → MP4 (MediaCodec/H.264 + MediaMuxer) → 갤러리(MediaStore.Video) + 공유.
// 헤드리스로 프레임을 그려(같은 렌더러 재사용) YUV420 로 인코딩. 기기별 코덱 차이가 있어 실기기 검증 필요.
object TimelapseVideoExporter {

    private const val FPS = 30               // 인앱 재생만큼 부드럽게
    private const val MAX_DIM = 480          // 출력 최대 변(짝수 보정)
    private const val MAX_FRAMES = 1800      // 너무 긴 녹화는 프레임 간격을 늘려 영상 길이를 제한(자동 가속, ~60s@30fps)
    private const val I_FRAME_INTERVAL = 1

    // 진행률(0..1) 콜백. 반환: 갤러리 URI.
    suspend fun exportToGallery(
        context: Context,
        id: String,
        onProgress: (Float) -> Unit = {},
    ): Uri = withContext(Dispatchers.IO) {
        val store = TimelapseStore.get(context)
        val log = store.loadLog(id) ?: error("타임랩스 로그를 찾을 수 없어요")

        // 배경 ref → 디코드.
        val bgMap = log.entries
            .mapNotNull { (it.op as? TimelapseOp.BackgroundPhoto)?.ref }
            .toSet()
            .mapNotNull { ref -> decodeBg(store.backgroundFile(id, ref))?.let { ref to it } }
            .toMap()

        val (w, h) = outputSize(bgMap.values.firstOrNull())
        val tmp = File(context.cacheDir, "timelapse_$id.mp4")
        if (tmp.exists()) tmp.delete()

        encode(log.entries, log.durationMs, bgMap, w, h, tmp, onProgress)
        val uri = copyToGallery(context, tmp, id)
        tmp.delete()
        uri
    }

    private fun outputSize(bg: BackgroundImage?): Pair<Int, Int> {
        val (w, h) = if (bg != null && bg.widthPx > 0 && bg.heightPx > 0) {
            val ar = bg.widthPx.toFloat() / bg.heightPx
            if (ar >= 1f) MAX_DIM to (MAX_DIM / ar).toInt() else (MAX_DIM * ar).toInt() to MAX_DIM
        } else {
            MAX_DIM to MAX_DIM
        }
        return even(w) to even(h)
    }

    private fun even(v: Int): Int = (v.coerceAtLeast(2) / 2) * 2

    private fun encode(
        entries: List<com.rts.rys.ryy.drawingtogether.drawing.model.TimelapseEntry>,
        durationMs: Long,
        bgMap: Map<String, BackgroundImage>,
        w: Int,
        h: Int,
        outFile: File,
        onProgress: (Float) -> Unit,
    ) {
        val mime = MediaFormat.MIMETYPE_VIDEO_AVC
        val format = MediaFormat.createVideoFormat(mime, w, h).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, (w * h * FPS * 0.2f).toInt().coerceAtLeast(1_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }
        val codec = MediaCodec.createEncoderByType(mime)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        val info = MediaCodec.BufferInfo()

        // 콘텐츠 시계(frameStepMs)는 길면 가속, 영상 fps 는 고정 → 너무 긴 녹화도 영상 길이 제한.
        val totalMs = durationMs.coerceAtLeast(1L)
        val minStep = 1000L / FPS
        val frameStepMs = maxOf(minStep, (totalMs + MAX_FRAMES - 1) / MAX_FRAMES)
        val contentFrames = (totalMs / frameStepMs).toInt() + 1
        val tailFrames = FPS // 마지막 화면 ~1초 유지(마지막 획이 곧장 끝나 안 보이던 문제)
        val frameCount = contentFrames + tailFrames

        val canvas = CanvasState()
        var entryIndex = 0
        val density = w / 400f

        fun drain(endOfStream: Boolean) {
            while (true) {
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!endOfStream) break // 더 넣을 때까지 대기
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    check(!muxerStarted)
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (outIndex >= 0) {
                    val encoded = codec.getOutputBuffer(outIndex)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && muxerStarted) {
                        encoded.position(info.offset)
                        encoded.limit(info.offset + info.size)
                        muxer.writeSampleData(trackIndex, encoded, info)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }

        try {
            for (f in 0 until frameCount) {
                // durationMs 로 clamp — 마지막 프레임이 끝(마지막 StrokeEnd)까지 확실히 적용.
                // tail 구간에선 totalMs 에 머물러 완성 화면을 유지.
                val contentMs = minOf(f.toLong() * frameStepMs, totalMs)
                while (entryIndex < entries.size && entries[entryIndex].atMs <= contentMs) {
                    applyOp(canvas, entries[entryIndex].op, bgMap)
                    entryIndex++
                }
                val bitmap = renderFrame(canvas, w, h, density)
                val ptsUs = f.toLong() * 1_000_000L / FPS

                var queued = false
                while (!queued) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val image = codec.getInputImage(inIndex)
                        if (image != null) fillImageYuv(image, bitmap, w, h)
                        // getInputBuffer 와 혼용하면 일부 기기서 예외 → 명목 YUV420 크기로 큐잉.
                        codec.queueInputBuffer(inIndex, 0, w * h * 3 / 2, ptsUs, 0)
                        queued = true
                    } else {
                        drain(false)
                    }
                }
                drain(false)
                onProgress((f + 1).toFloat() / frameCount)
            }
            // EOS
            var sent = false
            while (!sent) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    codec.queueInputBuffer(
                        inIndex, 0, 0, frameCount.toLong() * 1_000_000L / FPS,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                    )
                    sent = true
                } else {
                    drain(false)
                }
            }
            drain(true)
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { if (muxerStarted) muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun applyOp(
        canvas: CanvasState,
        op: TimelapseOp,
        bgMap: Map<String, BackgroundImage>,
    ) {
        when (op) {
            is TimelapseOp.Draw -> canvas.apply(op.event)
            is TimelapseOp.BackgroundColor -> canvas.setBackgroundColor(op.argb)
            is TimelapseOp.BackgroundPhoto -> canvas.setBackground(op.ref?.let(bgMap::get))
            is TimelapseOp.Snapshot -> canvas.applySnapshot(op.strokes, op.stickers)
        }
    }

    private fun renderFrame(state: CanvasState, w: Int, h: Int, density: Float): Bitmap {
        val image = ImageBitmap(w, h)
        val canvas = Canvas(image)
        val size = IntSize(w, h)
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
            drawRect(color = Color(state.backgroundColor))
            state.background?.bitmap?.let { bg ->
                drawImage(
                    image = bg,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bg.width, bg.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = size,
                )
            }
            state.strokes.forEach { drawStroke(it, size, density) }
            // 진행 중 stroke 도 그려야 "그려지는 과정"이 보인다(이게 빠지면 획이 끝날 때 통째로 팝업).
            state.openStrokes.values.forEach { drawStroke(it, size, density) }
            state.stickers.forEach { drawSticker(it, size) }
        }
        return image.asAndroidBitmap()
    }

    // ARGB Bitmap → YUV420(Image planes). rowStride/pixelStride 를 따르므로 planar/semiplanar 모두 대응.
    private fun fillImageYuv(image: Image, bmp: Bitmap, w: Int, h: Int) {
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val yP = image.planes[0]
        val uP = image.planes[1]
        val vP = image.planes[2]
        val yBuf = yP.buffer; val uBuf = uP.buffer; val vBuf = vP.buffer
        val yRow = yP.rowStride; val yPix = yP.pixelStride
        val uRow = uP.rowStride; val uPix = uP.pixelStride
        val vRow = vP.rowStride; val vPix = vP.pixelStride
        for (j in 0 until h) {
            for (i in 0 until w) {
                val c = px[j * w + i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val yVal = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
                yBuf.put(j * yRow + i * yPix, yVal.toByte())
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = (-0.169f * r - 0.331f * g + 0.5f * b + 128f).toInt().coerceIn(0, 255)
                    val v = (0.5f * r - 0.419f * g - 0.081f * b + 128f).toInt().coerceIn(0, 255)
                    val cj = j / 2; val ci = i / 2
                    uBuf.put(cj * uRow + ci * uPix, u.toByte())
                    vBuf.put(cj * vRow + ci * vPix, v.toByte())
                }
            }
        }
    }

    private fun decodeBg(file: File): BackgroundImage? {
        if (!file.exists()) return null
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return BackgroundImage(bmp.asImageBitmap(), bmp.width, bmp.height, BackgroundImage.Source.Gallery)
    }

    private fun copyToGallery(context: Context, src: File, id: String): Uri {
        val resolver = context.contentResolver
        val displayName = "timelapse_${id.take(8)}_${src.lastModified()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MOVIES}/DrawingTogether",
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert 실패")
        try {
            resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
                ?: error("openOutputStream 실패")
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        return uri
    }
}
