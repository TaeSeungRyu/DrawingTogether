package com.rts.rys.ryy.drawingtogether.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rts.rys.ryy.drawingtogether.works.Work
import com.rts.rys.ryy.drawingtogether.works.WorkStore
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// 커스텀 bottom 시트 — Material3 ModalBottomSheet 가 가로에서 sheet 를 시스템 영역 안쪽으로
// 배치해 우측 여백이 생기는 문제(파라미터로 못 푸는 동작) 때문에 Box 오버레이로 직접 구현.
// - 백드롭 탭 / 뒤로가기 / 아래로 드래그 로 닫힘. 애니메이션 없이 즉시 표시·닫힘.
// - 가로에서 화면 가로를 꽉 채움.
@Composable
fun RecentWorksModal(
    works: List<Work>,
    onWorkClick: (String) -> Unit,
    onDismiss: () -> Unit,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val cfg = LocalConfiguration.current
    val isLandscape =
        cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    // 그리드 최대 높이 — 세로 440dp, 가로는 화면 높이의 70% 정도로 제한(그 안에서 스크롤).
    val maxGridHeight = minOf(440, (cfg.screenHeightDp * 0.7f).roundToInt()).dp

    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 길게 눌러 삭제할 작품 — 확인 다이얼로그로 확정.
    var pendingDelete by remember { mutableStateOf<Work?>(null) }
    // 아래로 이만큼(px) 넘게 끌면 닫힘.
    val dismissThresholdPx = with(density) { 120.dp.toPx() }
    var dragOffsetY by remember { mutableStateOf(0f) }

    BackHandler { onDismiss() }

    Box(modifier = Modifier.fillMaxSize()) {
        // 백드롭 — 탭하면 닫힘. ripple 없이.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .offset { IntOffset(0, dragOffsetY.roundToInt()) },
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
                    )
                    .padding(horizontal = 16.dp),
            ) {
                // 드래그 핸들 — 이 영역만 세로 드래그로 닫기(그리드 스크롤과 충돌 방지).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                dragOffsetY = (dragOffsetY + delta).coerceAtLeast(0f)
                            },
                            onDragStopped = {
                                if (dragOffsetY > dismissThresholdPx) onDismiss()
                                else dragOffsetY = 0f
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                    )
                }

                Text(
                    text = "최근 작업",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 12.dp),
                )

                if (works.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "아직 저장된 작품이 없어요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (isLandscape) 5 else 3),
                        state = gridState,
                        contentPadding = PaddingValues(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxGridHeight),
                    ) {
                        items(works, key = { it.id }) { work ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                WorkThumbnail(
                                    work = work,
                                    // 작품 탭 → 바로 nav. modalOpen 은 HomeScreen 이 true 로 유지해
                                    // 뒤로가기 시 자동 재표시(스크롤 위치는 hoisted gridState).
                                    onClick = { onWorkClick(work.id) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f),
                                    cornerRadius = 12.dp,
                                    decodeMaxDim = 360,
                                    onLongClick = { pendingDelete = work },
                                )
                                if (work.name.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = work.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    pendingDelete?.let { work ->
        val label = work.name.takeIf { it.isNotBlank() } ?: "이 작품"
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("작품 삭제") },
            text = { Text("\"$label\"을(를) 삭제할까요? 되돌릴 수 없어요. (갤러리로 내보낸 사본은 남습니다.)") },
            confirmButton = {
                TextButton(onClick = {
                    val id = work.id
                    pendingDelete = null
                    scope.launch { WorkStore.get(context).delete(id) }
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("취소") }
            },
        )
    }
}
