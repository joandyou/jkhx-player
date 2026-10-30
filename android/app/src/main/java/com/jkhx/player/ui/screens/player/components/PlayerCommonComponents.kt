package com.jkhx.player.ui.screens.player.components

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jkhx.player.ui.theme.ShapeTokens
import com.jkhx.player.ui.theme.tvFocusableItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
internal fun RepeatingIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    initialDelayMs: Long = 350L,
    repeatIntervalMs: Long = 80L,
    content: @Composable () -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val coroutineScope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                     keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                     keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                    currentOnClick()
                    true
                } else false
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    currentOnClick()
                    val job = coroutineScope.launch {
                        delay(initialDelayMs)
                        while (isActive) {
                            currentOnClick()
                            delay(repeatIntervalMs)
                        }
                    }
                    waitForUpOrCancellation()
                    job.cancel()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
internal fun SettingsOptionCard(
    title: String,
    subtitle: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .tvFocusableItem(cornerRadius = 12.dp, focusedScale = 1.02f),
        shape = ShapeTokens.Card,
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

