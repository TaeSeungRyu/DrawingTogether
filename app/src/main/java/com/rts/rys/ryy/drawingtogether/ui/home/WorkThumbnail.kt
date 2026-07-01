package com.rts.rys.ryy.drawingtogether.ui.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rts.rys.ryy.drawingtogether.photo.PhotoLoader
import com.rts.rys.ryy.drawingtogether.works.Work
import com.rts.rys.ryy.drawingtogether.works.WorkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 저장된 작품 1건의 썸네일 카드. 크기는 호출자가 modifier로 결정.
// 디코딩은 IO 디스패처에서 sample-size로 다운샘플링.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkThumbnail(
    work: Work,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    decodeMaxDim: Int = 320,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val file = remember(work.id) { WorkStore.get(context).pngFile(work.id) }
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, work.id) {
        value = withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            val sample = PhotoLoader.computeSampleSize(opts.outWidth, opts.outHeight, maxDim = decodeMaxDim)
            val final = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, final)?.asImageBitmap()
        }
    }

    Card(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}
