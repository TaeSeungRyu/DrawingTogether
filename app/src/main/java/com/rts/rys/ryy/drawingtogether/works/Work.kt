package com.rts.rys.ryy.drawingtogether.works

// 저장된 작품 한 건의 메타데이터. 본체 PNG는 `${id}.png`에 따로.
data class Work(
    val id: String,
    val savedAtEpochMs: Long,
    val widthPx: Int,
    val heightPx: Int,
    val hasPhotoBackground: Boolean,
) {
    internal fun toMetaLine(): String =
        "$id|$savedAtEpochMs|$widthPx|$heightPx|$hasPhotoBackground"

    companion object {
        internal fun parseMetaLine(line: String): Work? {
            val parts = line.trim().split("|")
            if (parts.size != 5) return null
            return Work(
                id = parts[0],
                savedAtEpochMs = parts[1].toLongOrNull() ?: return null,
                widthPx = parts[2].toIntOrNull() ?: return null,
                heightPx = parts[3].toIntOrNull() ?: return null,
                hasPhotoBackground = parts[4].toBooleanStrictOrNull() ?: return null,
            )
        }
    }
}
