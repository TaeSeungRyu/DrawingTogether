package com.rts.rys.ryy.drawingtogether.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// "같이 그리기" 바텀시트 — 협업 모드 4종(함께/모임/교실/나눠 그리기) 선택.
// RecentWorksModal 과 동일 커스텀 오버레이 패턴(가로 풀폭 + 백드롭/뒤로가기/아래 드래그 닫기).
// 카드 색·문구는 기존 홈 버튼과 동일. 카드 탭 → 해당 모드 콜백 + 시트 닫기.
@Composable
fun CollabModeSheet(
    onDuoMode: () -> Unit,
    onPartyMode: () -> Unit,
    onClassroomMode: () -> Unit,
    onSplitMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current
    val maxCardsHeight = (cfg.screenHeightDp * 0.7f).roundToInt().dp
    val dismissThresholdPx = with(density) { 120.dp.toPx() }
    var dragOffsetY by remember { mutableStateOf(0f) }
    // 진입 시 아래→위 슬라이드 + 백드롭 페이드. (mount 직후 shown=true 로 전환해 애니메이션 트리거.)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    BackHandler { onDismiss() }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = shown, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDismiss() },
            )
        }

        AnimatedVisibility(
            visible = shown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
        Surface(
            modifier = Modifier
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
                // 드래그 핸들 — 이 영역만 세로 드래그로 닫기.
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
                    text = "같이 그릴 방식을 골라요",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 12.dp),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxCardsHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ModeCard(
                        title = "함께 모드",
                        subtitle = "둘이서 한 캔버스에 같이 그리기",
                        container = MaterialTheme.colorScheme.secondary,
                        content = MaterialTheme.colorScheme.onSecondary,
                    ) { onDuoMode(); onDismiss() }
                    ModeCard(
                        title = "모임 모드",
                        subtitle = "최대 4명, 각자 캔버스 + 미니 뷰",
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) { onPartyMode(); onDismiss() }
                    ModeCard(
                        title = "교실 모드",
                        subtitle = "최대 10명, 방장 중심 (교사–학생)",
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    ) { onClassroomMode(); onDismiss() }
                    ModeCard(
                        title = "나눠 그리기",
                        subtitle = "2~4명, 레이아웃 나눠 각자 구역 그리기",
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) { onSplitMode(); onDismiss() }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        }
    }
}

@Composable
private fun ColumnScope.ModeCard(
    title: String,
    subtitle: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(72.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
