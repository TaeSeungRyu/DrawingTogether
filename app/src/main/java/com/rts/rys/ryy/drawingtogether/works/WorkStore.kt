package com.rts.rys.ryy.drawingtogether.works

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
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

    // 외부 공유용 FileProvider URI. file_paths.xml 의 `<files-path name="works" path="works/" />`
    // 와 매칭. 권한이 노출되니 ACTION_SEND 등에 그대로 첨부 가능.
    fun shareUriFor(context: Context, work: Work): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        pngFile(work.id),
    )

    // Pictures/DrawingTogether/ 하위에 PNG 로 저장. minSdk 31 이라 scoped storage 만 — 권한
    // 불필요. 같은 이름이 있으면 MediaStore 가 알아서 (1), (2) 식 suffix 처리.
    //
    // IS_PENDING 패턴 (Android 10+): insert 시 IS_PENDING=1 로 표시 → 데이터 쓴 후 IS_PENDING=0
    // 으로 update 해야 갤러리 등 다른 앱이 그 파일을 보게 된다. 빠뜨리면 MediaStore 내부에는
    // 있지만 갤러리에 안 나타남.
    // 반환값: 저장된 MediaStore URI (실패 시 null).
    // 단계별 실패 지점을 노출하기 위해 throw 로 전파. 호출자 (PreviewScreen) 가 catch 해서
    // 토스트로 표시. 진단 후 안정되면 다시 단순 nullable 로 돌릴 수 있음.
    suspend fun exportToGallery(context: Context, work: Work): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = (work.name.takeIf { it.isNotBlank() } ?: work.id) + ".png"
        val nowSec = System.currentTimeMillis() / 1000
        val insertValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/DrawingTogether",
            )
            put(MediaStore.Images.Media.DATE_ADDED, nowSec)
            put(MediaStore.Images.Media.DATE_MODIFIED, nowSec)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, insertValues)
            ?: error("insert returned null")
        try {
            val src = pngFile(work.id)
            if (!src.exists()) error("source png missing: ${src.absolutePath}")
            val bitmap = BitmapFactory.decodeFile(src.absolutePath)
                ?: error("BitmapFactory.decodeFile returned null: ${src.absolutePath}")
            val out = resolver.openOutputStream(uri) ?: error("openOutputStream returned null")
            val ok = out.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!ok) error("bitmap.compress returned false")
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
        val publishValues = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        val updated = resolver.update(uri, publishValues, null, null)
        if (updated == 0) {
            runCatching { resolver.delete(uri, null, null) }
            error("IS_PENDING publish updated 0 rows")
        }
        // 명시적 MediaScanner — 일부 갤러리(Samsung 등) 가 즉시 인덱싱 안 하는 경우 대비.
        val absolutePath = "${Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES,
        )}/DrawingTogether/$displayName"
        MediaScannerConnection.scanFile(
            context,
            arrayOf(absolutePath),
            arrayOf("image/png"),
            null,
        )
        uri
    }

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
