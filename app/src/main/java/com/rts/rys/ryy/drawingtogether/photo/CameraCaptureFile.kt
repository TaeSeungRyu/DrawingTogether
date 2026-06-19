package com.rts.rys.ryy.drawingtogether.photo

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

// 카메라 촬영 결과를 받기 위한 임시 파일 + content:// URI 생성.
// FileProvider authority는 AndroidManifest와 일치해야 함.
object CameraCaptureFile {
    fun create(context: Context): Uri {
        val capturesDir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(capturesDir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }
}
