package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rts.rys.ryy.drawingtogether.drawing.model.BrushType

// 알약(pill) 모양 토널 액션 버튼. 시스템 TextButton보다 부드럽고 친근한 느낌.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuteToolButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val resolvedContainer = if (enabled) container else container.copy(alpha = 0.4f)
    val resolvedContent = if (enabled) content else content.copy(alpha = 0.4f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(percent = 50),
        color = resolvedContainer,
        contentColor = resolvedContent,
        modifier = modifier.height(40.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 18.dp).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// 지우개 토글 — selected일 때 tertiary 컨테이너 + 외곽선.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EraserToggle(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected)
        MaterialTheme.colorScheme.tertiaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val border = if (selected)
        BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
    else
        null
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = container,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = border,
        modifier = modifier.height(40.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "지우개", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// 저장 시 사진 배경을 PNG에 합칠지 켜고 끄는 토글. 사진 추가 전에도 켤 수 있도록 항상 노출.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeBackgroundToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (checked)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (checked)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant
    val border = if (checked)
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    else
        null
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(percent = 50),
        color = container,
        contentColor = contentColor,
        border = border,
        modifier = modifier.height(40.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (checked) "배경 합치기 ON" else "배경 합치기 OFF",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

// 도구바에서 현재 붓 보여주는 트리거. 탭하면 BrushSelectorSheet가 열림.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrushTriggerButton(
    brush: BrushType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.height(40.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrushPreview(
                brush = brush,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp).height(22.dp),
            )
            Text(text = brush.displayName, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "붓 선택",
                modifier = Modifier.height(18.dp),
            )
        }
    }
}
