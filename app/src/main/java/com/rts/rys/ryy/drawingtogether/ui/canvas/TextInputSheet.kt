package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

// 텍스트 입력 바텀시트 — 캔버스 빈 곳을 탭하면 그 위치에 대해 열린다.
// 확인 시 (문자열, sizeFrac) 을 콜백으로 넘긴다. 입력 종료 = 굳음 — 이후 수정/이동 불가(설계 결정).
// 키보드(IME)는 imePadding 으로 시트를 밀어 올려 입력칸이 가려지지 않게 한다.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextInputSheet(
    onConfirm: (text: String, sizeFrac: Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var value by remember { mutableStateOf("") }
    var sizeFrac by remember { mutableStateOf(TextSizePreset.Medium.frac) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "텍스트 넣기",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp),
            )

            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("내용") },
                singleLine = false,
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { focusManager.clearFocus() },
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Default,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "크기", style = MaterialTheme.typography.labelLarge)
                TextSizePreset.values().forEach { preset ->
                    FilterChip(
                        selected = sizeFrac == preset.frac,
                        onClick = { sizeFrac = preset.frac },
                        label = { Text(preset.label) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("취소") }
                Spacer(modifier = Modifier.height(0.dp))
                Button(
                    onClick = { onConfirm(value, sizeFrac) },
                    enabled = value.isNotBlank(),
                ) { Text("넣기") }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    // 시트가 뜨면 입력칸에 포커스 → IME 자동 표시.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

// 글자 크기 프리셋 — 캔버스 짧은 변 대비 비율. 배치 시 고정(이후 변경 불가).
private enum class TextSizePreset(val label: String, val frac: Float) {
    Small("작게", 0.04f),
    Medium("보통", 0.06f),
    Large("크게", 0.09f),
}
