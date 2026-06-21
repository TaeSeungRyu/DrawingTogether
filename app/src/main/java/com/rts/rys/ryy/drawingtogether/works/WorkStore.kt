package com.rts.rys.ryy.drawingtogether.works

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

// 저장된 작품의 영속 저장소 + 메모리 인덱스.
// PNG 본체는 `<id>.png`, 메타데이터는 `<id>.meta` (파이프 구분 텍스트 1줄).
// 앱 내부 저장소(filesDir)이라 외부 스토리지 권한 불필요.
class WorkStore private constructor(private val worksDir: File) {

    private val _works = MutableStateFlow(loadAll())
    val works: StateFlow<List<Work>> = _works.asStateFlow()

    suspend fun save(
        bitmap: Bitmap,
        hasPhotoBackground: Boolean,
        name: String,
    ): Work = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val finalName = resolveUniqueName(name, _works.value)
        val work = Work(
            id = id,
            savedAtEpochMs = System.currentTimeMillis(),
            widthPx = bitmap.width,
            heightPx = bitmap.height,
            hasPhotoBackground = hasPhotoBackground,
            name = finalName,
        )
        pngFile(id).outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        metaFile(id).writeText(work.toMetaLine())
        _works.value = loadAll()
        work
    }

    fun pngFile(id: String): File = File(worksDir, "$id.png")

    private fun metaFile(id: String): File = File(worksDir, "$id.meta")

    private fun loadAll(): List<Work> {
        val files = worksDir.listFiles { f -> f.name.endsWith(".meta") } ?: emptyArray()
        return files
            .mapNotNull { runCatching { Work.parseMetaLine(it.readText()) }.getOrNull() }
            .sortedByDescending { it.savedAtEpochMs }
    }

    companion object {
        @Volatile private var instance: WorkStore? = null

        fun get(context: Context): WorkStore = instance ?: synchronized(this) {
            instance ?: run {
                val dir = File(context.applicationContext.filesDir, "works").apply { mkdirs() }
                WorkStore(dir).also { instance = it }
            }
        }

        // 같은 이름이 있으면 "곰돌이", "곰돌이 (2)", "곰돌이 (3)" ... 순으로 비어있는 첫 자리를 사용.
        // 이름이 빈 문자열이면 그대로 빈 문자열 반환(라벨 없는 작품 허용).
        internal fun resolveUniqueName(base: String, existing: List<Work>): String {
            val trimmed = base.trim()
            if (trimmed.isEmpty()) return ""
            val taken = existing.map { it.name }.toHashSet()
            if (trimmed !in taken) return trimmed
            var n = 2
            while ("$trimmed ($n)" in taken) n++
            return "$trimmed ($n)"
        }
    }
}
