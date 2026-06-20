package com.rts.rys.ryy.drawingtogether.ui.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rts.rys.ryy.drawingtogether.photo.PhotoLoader
import com.rts.rys.ryy.drawingtogether.works.Work
import com.rts.rys.ryy.drawingtogether.works.WorkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 홈 화면에 노출할 최근 작품 개수. 그 이상은 향후 갤러리 화면(Phase 4)에서 "전체 보기"로.
private const val RECENT_LIMIT = 10

// 작품이 있을 때만 렌더 — 빈 상태 자리표시는 첫 사용 UX에 잡음.
@Composable
fun RecentWorksRow(
    works: List<Work>,
    onWorkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (works.isEmpty()) return
    val recent = remember(works) { works.take(RECENT_LIMIT) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "최근 작품",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 1.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(recent, key = { it.id }) { work ->
                WorkThumbnail(work = work, onClick = { onWorkClick(work.id) })
            }
        }
    }
}

@Composable
private fun WorkThumbnail(work: Work, onClick: () -> Unit) {
    val context = LocalContext.current
    val file = remember(work.id) { WorkStore.get(context).pngFile(work.id) }
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, work.id) {
        value = withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            val sample = PhotoLoader.computeSampleSize(opts.outWidth, opts.outHeight, maxDim = 192)
            val final = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, final)?.asImageBitmap()
        }
    }

    Card(
        onClick = onClick,
        modifier = Modifier.size(width = 48.dp, height = 48.dp),
        shape = RoundedCornerShape(8.dp),
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
