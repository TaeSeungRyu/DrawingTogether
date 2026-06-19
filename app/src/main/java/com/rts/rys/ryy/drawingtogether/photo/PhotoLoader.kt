package com.rts.rys.ryy.drawingtogether.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import com.rts.rys.ryy.drawingtogether.drawing.model.BackgroundImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Uri로부터 사진을 읽어 BackgroundImage로 변환.
// - 화면 크기에 맞춰 down-sample (메모리 절약)
// - EXIF orientation 적용 (휴대폰 카메라 사진이 옆으로 누워있는 문제 회피)
object PhotoLoader {

    private const val DEFAULT_MAX_DIM = 2048

    suspend fun load(
        context: Context,
        uri: Uri,
        source: BackgroundImage.Source,
        maxDim: Int = DEFAULT_MAX_DIM,
    ): BackgroundImage = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        val (origW, origH) = readBounds(resolver, uri)
        if (origW <= 0 || origH <= 0) error("Invalid image dimensions for $uri")

        val sampleSize = computeSampleSize(origW, origH, maxDim)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: error("Failed to decode $uri")

        val rotated = applyExifRotation(resolver, uri, decoded)

        BackgroundImage(
            bitmap = rotated.asImageBitmap(),
            widthPx = rotated.width,
            heightPx = rotated.height,
            source = source,
        )
    }

    private fun readBounds(
        resolver: android.content.ContentResolver,
        uri: Uri,
    ): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        }
        return opts.outWidth to opts.outHeight
    }

    private fun applyExifRotation(
        resolver: android.content.ContentResolver,
        uri: Uri,
        bitmap: Bitmap,
    ): Bitmap {
        val orientation = resolver.openInputStream(uri)?.use { ExifInterface(it) }
            ?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }

    // inSampleSize는 2의 거듭제곱이어야 BitmapFactory가 효율적.
    // 가장 큰 변(longest edge)이 maxDim 이하가 되는 최소의 2^n을 고른다.
    internal fun computeSampleSize(width: Int, height: Int, maxDim: Int): Int {
        require(maxDim > 0)
        val longest = maxOf(width, height)
        if (longest <= maxDim) return 1
        var sample = 1
        while ((longest / (sample * 2)) >= maxDim) {
            sample *= 2
        }
        return sample
    }
}
