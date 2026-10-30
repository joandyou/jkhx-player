package com.jkhx.player.ui.screens.player.panels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.jkhx.player.R
import com.jkhx.player.ui.screens.player.formatTime

@Composable
internal fun PlayerResumeDialog(
    visible: Boolean,
    resumeTargetPositionMs: Long,
    isLandscape: Boolean,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible || resumeTargetPositionMs <= 0L) return

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(if (isLandscape) 0.55f else 0.9f),
        containerColor = Color.Black.copy(alpha = 0.85f),
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.player_resume_dialog_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Text(
                text = stringResource(R.string.player_resume_dialog_msg, formatTime(resumeTargetPositionMs)),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.player_resume_continue, formatTime(resumeTargetPositionMs)),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onRestart,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White.copy(alpha = 0.8f)
                )
            ) {
                Text(
                    text = stringResource(R.string.player_resume_restart),
                    fontSize = 13.sp
                )
            }
        }
    )
}

