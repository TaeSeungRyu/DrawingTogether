package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rts.rys.ryy.drawingtogether.drawing.model.BrushType
import com.rts.rys.ryy.drawingtogether.drawing.model.ShapeMode
import com.rts.rys.ryy.drawingtogether.drawing.model.StickerKey
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolKind
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolSettings

// ColorPickerSheet 의 두 가지 진입 케이스 — 임시 색(+ 버튼) vs 팔레트 슬롯 편집.
private sealed class PickerTarget {
    data object None : PickerTarget()
    data object Temp : PickerTarget()
    data class Slot(val index: Int, val current: Int) : PickerTarget()
}

@Composable
fun Toolbar(
    tool: ToolSettings,
    canUndo: Boolean,
    onColor: (Int) -> Unit,
    onEraser: () -> Unit,
    onEyedropper: () -> Unit,
    onBrush: (BrushType) -> Unit,
    onShape: (ShapeMode) -> Unit,
    onToggleFill: () -> Unit,
    onSticker: (StickerKey) -> Unit,
    onPen: () -> Unit,
    onStrokeWidth: (Float) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    guideCross: Boolean = false,
    guideGrid: GuideGrid = GuideGrid.None,
    onToggleGuideCross: () -> Unit = {},
    onSelectGuideGrid: (GuideGrid) -> Unit = {},
    smoothing: Smoothing = Smoothing.Off,
    onCycleSmoothing: () -> Unit = {},
    modifier: Modifier = Modifier,
    // null 이면 버튼 숨김 — 싱글 모드 또는 미연결 상태.
    onSync: (() -> Unit)? = null,
    // 모임 모드 호스트가 새 조인자를 받기 위해 광고를 다시 켤 때. 호스트일 때만 노출.
    onOpenRoom: (() -> Unit)? = null,
    // 가로 모드 우측 패널 — 세로 높이를 꽉 채우고 도구들을 균등 분산(SpaceEvenly).
    // 세로 모드(false)는 콘텐츠 높이 wrap + 필요 시 스크롤.
    fillHeight: Boolean = false,
) {
    val context = LocalContext.current
    val paletteRepo = remember { UserPaletteRepo.get(context) }
    val palette by paletteRepo.palette.collectAsState()
    val recent by paletteRepo.recent.collectAsState()

    // ColorPickerSheet 는 두 가지 케이스에서 띄움 — 임시 색 (+ 버튼) 또는 슬롯 편집.
    // 어느 케이스인지 onConfirm 분기에 사용.
    var pickerTarget by remember { mutableStateOf<PickerTarget>(PickerTarget.None) }
    var paletteEditing by remember { mutableStateOf(false) }
    var brushSheetOpen by remember { mutableStateOf(false) }
    var stickerSheetOpen by remember { mutableStateOf(false) }

    Surface(modifier = modifier, tonalElevation = 2.dp) {
        // 가로(fillHeight): 패널 높이 꽉 채우고 도구 균등 분산. 세로: wrap + 스크롤.
        Column(
            modifier = Modifier
                .then(
                    if (fillHeight) Modifier.fillMaxHeight()
                    else Modifier.verticalScroll(rememberScrollState()),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = if (fillHeight) Arrangement.SpaceEvenly
            else Arrangement.spacedBy(10.dp),
        ) {

            ColorPaletteRow(
                currentColor = tool.colorArgb,
                isPenSelected = tool.kind == ToolKind.Pen,
                onColor = onColor,
                onCustom = { pickerTarget = PickerTarget.Temp },
                eyedropperActive = tool.kind == ToolKind.Eyedropper,
                onEyedropper = onEyedropper,
                recents = recent,
                presets = palette,
                editing = paletteEditing,
                onToggleEdit = { paletteEditing = !paletteEditing },
                onEditSlot = { index, current ->
                    pickerTarget = PickerTarget.Slot(index, current)
                },
                onResetPalette = { paletteRepo.resetToDefault() },
                modifier = Modifier.fillMaxWidth(),
            )

            // 도구 줄 — 라벨 달린 아이콘 5개를 균등(weight) 배치. 가로 스크롤 없이 한 줄 고정.
            // 펜(자유)/도형/스티커는 서로 배타적, 안내선은 독립 오버레이 토글.
            val penFreehand = tool.kind == ToolKind.Pen && tool.shape == ShapeMode.None
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 붓: 선택 안 됐으면 펜 자유 모드로, 이미 선택돼 있으면 붓 종류 시트 열기.
                ToolIconButton(
                    label = "붓",
                    selected = penFreehand,
                    onClick = { if (penFreehand) brushSheetOpen = true else onPen() },
                    modifier = Modifier.weight(1f),
                ) {
                    BrushPreview(
                        brush = tool.brush,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().height(20.dp),
                    )
                }
                ShapeDropdownButton(
                    shape = tool.shape,
                    onShape = onShape,
                    fill = tool.fill,
                    onToggleFill = onToggleFill,
                    modifier = Modifier.weight(1f),
                )
                ToolIconButton(
                    label = "스티커",
                    selected = tool.kind == ToolKind.Sticker,
                    onClick = { stickerSheetOpen = true },
                    modifier = Modifier.weight(1f),
                ) {
                    StickerPreview(
                        key = tool.stickerKey ?: StickerKey.Heart,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                ToolIconButton(
                    label = "지우개",
                    selected = tool.kind == ToolKind.Eraser,
                    onClick = onEraser,
                    modifier = Modifier.weight(1f),
                ) {
                    EraserGlyph(modifier = Modifier.fillMaxSize())
                }
                GuideDropdownButton(
                    cross = guideCross,
                    grid = guideGrid,
                    onToggleCross = onToggleGuideCross,
                    onSelectGrid = onSelectGuideGrid,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "굵기 ${tool.strokeWidthDp.toInt()}dp",
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = tool.strokeWidthDp,
                    onValueChange = onStrokeWidth,
                    valueRange = 1f..32f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
                SmoothingChip(smoothing = smoothing, onClick = onCycleSmoothing)
            }

            // 호스트는 동기화·방 열기·되돌리기·전체 지우기 4개라 좁은 폭에서 한 줄에 안 들어감.
            // 줄바꿈은 도구바 높이를 키워 캔버스를 먹으므로, 한 줄 유지 + 가로 스크롤(+페이드).
            // 다 들어오면 우측 정렬(Box CenterEnd), 넘치면 스크롤 → 캔버스 높이는 그대로.
            val actionsScroll = rememberScrollState()
            val actionsFadeColor = MaterialTheme.colorScheme.surface
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .horizontalScroll(actionsScroll)
                        .fadingEdgeHorizontal(
                            leftFade = actionsScroll.canScrollBackward,
                            rightFade = actionsScroll.canScrollForward,
                            fadeColor = actionsFadeColor,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (onSync != null) {
                        CuteToolButton(
                            text = "동기화",
                            onClick = onSync,
                            container = MaterialTheme.colorScheme.tertiary,
                            content = MaterialTheme.colorScheme.onTertiary,
                        )
                    }
                    if (onOpenRoom != null) {
                        CuteToolButton(
                            text = "방 열기",
                            onClick = onOpenRoom,
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    CuteToolButton(
                        text = "되돌리기",
                        onClick = onUndo,
                        enabled = canUndo,
                    )
                    CuteToolButton(
                        text = "전체 지우기",
                        onClick = onClear,
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }

    val target = pickerTarget
    if (target != PickerTarget.None) {
        val initial = when (target) {
            is PickerTarget.Slot -> target.current
            PickerTarget.Temp -> tool.colorArgb
            PickerTarget.None -> tool.colorArgb
        }
        ColorPickerSheet(
            initialColor = initial,
            onConfirm = { argb ->
                when (val t = target) {
                    is PickerTarget.Slot -> paletteRepo.updateSlot(t.index, argb)
                    PickerTarget.Temp -> onColor(argb)
                    PickerTarget.None -> Unit
                }
                pickerTarget = PickerTarget.None
            },
            onDismiss = { pickerTarget = PickerTarget.None },
        )
    }

    if (brushSheetOpen) {
        BrushSelectorSheet(
            currentBrush = tool.brush,
            onSelect = onBrush,
            onDismiss = { brushSheetOpen = false },
        )
    }

    if (stickerSheetOpen) {
        StickerPickerSheet(
            currentKey = tool.stickerKey,
            onSelect = onSticker,
            onDismiss = { stickerSheetOpen = false },
        )
    }
}

// 손떨림 보정 토글 칩 — 탭하면 끔 → 약 → 강 순환. 활성(약/강)일 때 강조 색.
@Composable
private fun SmoothingChip(smoothing: Smoothing, onClick: () -> Unit) {
    val active = smoothing != Smoothing.Off
    val container = if (active)
        MaterialTheme.colorScheme.secondaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val content = if (active)
        MaterialTheme.colorScheme.onSecondaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = container,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = "보정 ${smoothing.label}",
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
