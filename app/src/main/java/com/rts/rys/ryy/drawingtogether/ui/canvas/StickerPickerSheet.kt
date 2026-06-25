package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.rts.rys.ryy.drawingtogether.drawing.model.PeerId
import com.rts.rys.ryy.drawingtogether.drawing.model.Sticker
import com.rts.rys.ryy.drawingtogether.drawing.model.StickerId
import com.rts.rys.ryy.drawingtogether.drawing.model.StickerKey

private const val STICKER_COLUMNS = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPickerSheet(
    currentKey: StickerKey?,
    onSelect: (StickerKey) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "스티커 선택",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 16.dp),
            )

            StickerKey.values().toList().chunked(STICKER_COLUMNS).forEach { rowKeys ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowKeys.forEach { key ->
                        StickerCard(
                            key = key,
                            selected = key == currentKey,
                            onClick = {
                                onSelect(key)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(STICKER_COLUMNS - rowKeys.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StickerCard(
    key: StickerKey,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected)
        MaterialTheme.colorScheme.secondaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val border = if (selected)
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    else
        null

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = border,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StickerPreview(
                key = key,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = key.displayName,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

// 스티커 한 개를 정사각형 미리보기로 그린다. drawSticker 를 재사용 — 실제와 동일 모양.
@Composable
fun StickerPreview(key: StickerKey, modifier: Modifier = Modifier) {
    val preview = Sticker(
        id = StickerId("preview"),
        authorId = PeerId.Local,
        key = key,
        cx = 0.5f,
        cy = 0.5f,
        scale = 0.9f,
        rotationDeg = 0f,
    )
    Canvas(modifier = modifier) {
        val s = IntSize(size.width.toInt(), size.height.toInt())
        drawSticker(preview, s)
    }
}
