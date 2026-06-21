package com.rts.rys.ryy.drawingtogether.works

import java.net.URLDecoder
import java.net.URLEncoder

// 저장된 작품 한 건의 메타데이터. 본체 PNG는 `${id}.png`에 따로.
data class Work(
    val id: String,
    val savedAtEpochMs: Long,
    val widthPx: Int,
    val heightPx: Int,
    val hasPhotoBackground: Boolean,
    val name: String,
) {
    // 6번째 필드(name)는 URL-encoded — 파이프/개행 등이 들어와도 깨지지 않게.
    internal fun toMetaLine(): String {
        val encodedName = URLEncoder.encode(name, Charsets.UTF_8.name())
        return "$id|$savedAtEpochMs|$widthPx|$heightPx|$hasPhotoBackground|$encodedName"
    }

    companion object {
        internal fun parseMetaLine(line: String): Work? {
            val parts = line.trim().split("|")
            // 구버전(5필드) 메타도 읽을 수 있어야 한다. 이름은 빈 문자열로 폴백.
            if (parts.size != 5 && parts.size != 6) return null
            val name = if (parts.size == 6) {
                runCatching { URLDecoder.decode(parts[5], Charsets.UTF_8.name()) }.getOrDefault("")
            } else {
                ""
            }
            return Work(
                id = parts[0],
                savedAtEpochMs = parts[1].toLongOrNull() ?: return null,
                widthPx = parts[2].toIntOrNull() ?: return null,
                heightPx = parts[3].toIntOrNull() ?: return null,
                hasPhotoBackground = parts[4].toBooleanStrictOrNull() ?: return null,
                name = name,
            )
        }
    }
}
