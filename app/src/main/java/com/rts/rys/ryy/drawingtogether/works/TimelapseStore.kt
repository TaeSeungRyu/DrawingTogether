package com.rts.rys.ryy.drawingtogether.works

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.rts.rys.ryy.drawingtogether.drawing.model.Timelapse
import com.rts.rys.ryy.drawingtogether.drawing.model.TimelapseEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import java.io.File
import java.util.UUID

// 갤러리 목록용 경량 메타. 전체 로그(CBOR)를 읽지 않고 빠르게 나열하기 위해 별도 meta 파일로 보관.
data class TimelapseMeta(
    val id: String,
    val createdAtEpochMs: Long,
    val durationMs: Long,
)

// 타임랩스 영속 저장소 — 작품 PNG(WorkStore) 와 독립. `filesDir/timelapses/<id>/` 디렉터리에
//   log.timelapse  (CBOR Timelapse)
//   meta           (id|createdAt|duration 파이프 텍스트 — 목록용)
//   thumb.png      (종료 상태 썸네일)
//   bg-<n>.png     (사진 배경 스냅샷, 있을 때만)
@OptIn(ExperimentalSerializationApi::class)
class TimelapseStore private constructor(private val rootDir: File) {

    private val cbor = Cbor { ignoreUnknownKeys = true }

    private val _items = MutableStateFlow(loadAll())
    val items: StateFlow<List<TimelapseMeta>> = _items.asStateFlow()

    // 인메모리 기록을 디스크로 일괄 기록. thumbnail 은 PngComposer 로 만든 종료 상태 비트맵.
    suspend fun save(
        entries: List<TimelapseEntry>,
        durationMs: Long,
        backgrounds: List<ImageBitmap>,
        thumbnail: Bitmap,
    ): TimelapseMeta = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()
        val dir = itemDir(id).apply { mkdirs() }

        val log = Timelapse(
            id = id,
            createdAtEpochMs = createdAt,
            durationMs = durationMs,
            entries = entries,
        )
        File(dir, LOG).writeBytes(cbor.encodeToByteArray(log))
        File(dir, META).writeText("$id|$createdAt|$durationMs")
        File(dir, THUMB).outputStream().use { thumbnail.compress(Bitmap.CompressFormat.PNG, 100, it) }
        backgrounds.forEachIndexed { i, bg ->
            File(dir, "bg-$i.png").outputStream().use {
                bg.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        pruneToLimit()
        _items.value = loadAll()
        TimelapseMeta(id, createdAt, durationMs)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        runCatching { itemDir(id).deleteRecursively() }
        _items.value = loadAll()
    }

    fun loadLog(id: String): Timelapse? = runCatching {
        cbor.decodeFromByteArray<Timelapse>(File(itemDir(id), LOG).readBytes())
    }.getOrNull()

    fun thumbFile(id: String): File = File(itemDir(id), THUMB)

    fun backgroundFile(id: String, ref: String): File = File(itemDir(id), "$ref.png")

    private fun itemDir(id: String): File = File(rootDir, id)

    private fun pruneToLimit() {
        val all = loadAll()
        if (all.size <= MAX_ITEMS) return
        all.drop(MAX_ITEMS).forEach { runCatching { itemDir(it.id).deleteRecursively() } }
    }

    private fun loadAll(): List<TimelapseMeta> {
        val dirs = rootDir.listFiles { f -> f.isDirectory } ?: emptyArray()
        return dirs
            .mapNotNull { dir -> runCatching { parseMeta(File(dir, META).readText()) }.getOrNull() }
            .sortedByDescending { it.createdAtEpochMs }
    }

    private fun parseMeta(line: String): TimelapseMeta {
        val p = line.split("|")
        return TimelapseMeta(p[0], p[1].toLong(), p[2].toLong())
    }

    companion object {
        const val MAX_ITEMS: Int = 50
        private const val LOG = "log.timelapse"
        private const val META = "meta"
        private const val THUMB = "thumb.png"

        @Volatile private var instance: TimelapseStore? = null

        fun get(context: Context): TimelapseStore = instance ?: synchronized(this) {
            instance ?: run {
                val dir = File(context.applicationContext.filesDir, "timelapses").apply { mkdirs() }
                TimelapseStore(dir).also { instance = it }
            }
        }
    }
}
