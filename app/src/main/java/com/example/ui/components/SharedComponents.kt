package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.OutreachChannel
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.InstagramAccent
import com.example.ui.theme.InstagramContainerLight
import com.example.ui.theme.WhatsAppAccent
import com.example.ui.theme.WhatsAppContainerLight
import com.example.ui.viewmodel.ActionUiState

@Composable
fun ChannelBadge(channel: String, modifier: Modifier = Modifier) {
    val isIg = channel == OutreachChannel.INSTAGRAM.name
    val bg = if (isIg) InstagramContainerLight else WhatsAppContainerLight
    val fg = if (isIg) InstagramAccent else WhatsAppAccent
    val label = if (isIg) "Instagram" else "WhatsApp"

    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun StatusPill(label: String, isPositive: Boolean = false, isWarning: Boolean = false, isError: Boolean = false) {
    val container = when {
        isError -> MaterialTheme.colorScheme.errorContainer
        isWarning -> MaterialTheme.colorScheme.tertiaryContainer
        isPositive -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        isError -> MaterialTheme.colorScheme.error
        isWarning -> AmberWarning
        isPositive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(100.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun AuditScoreRing(score: Long, modifier: Modifier = Modifier) {
    val clamped = score.coerceIn(0L, 100L)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = when {
        clamped >= 75 -> MaterialTheme.colorScheme.primary
        clamped >= 50 -> AmberWarning
        else -> MaterialTheme.colorScheme.error
    }

    Box(
        modifier = modifier.size(54.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = (clamped / 100f) * 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = clamped.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ActionFeedbackBanner(
    state: ActionUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state is ActionUiState.Idle) return

    val (bg, fg) = when (state) {
        is ActionUiState.Busy -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        is ActionUiState.Success -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        is ActionUiState.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
        ActionUiState.Idle -> return
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (state) {
                is ActionUiState.Busy -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = fg
                )
                is ActionUiState.Success -> Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(20.dp)
                )
                is ActionUiState.Error -> Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(20.dp)
                )
                ActionUiState.Idle -> Unit
            }

            val msg = when (state) {
                is ActionUiState.Busy -> state.message
                is ActionUiState.Success -> state.message
                is ActionUiState.Error -> state.message
                ActionUiState.Idle -> ""
            }
            Text(
                text = msg,
                style = MaterialTheme.typography.bodyMedium,
                color = fg,
                modifier = Modifier.weight(1f)
            )
            if (state !is ActionUiState.Busy) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss notification",
                        tint = fg,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

object OutreachIntentHelper {
    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun openUrl(context: Context, url: String) {
        if (url.isBlank()) return
        val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
        }.onFailure {
            Toast.makeText(context, "Unable to open URL", Toast.LENGTH_SHORT).show()
        }
    }

    fun openWhatsAppChat(context: Context, phone: String, message: String) {
        val digits = phone.filter { it.isDigit() }
        if (digits.isBlank()) {
            Toast.makeText(context, "No WhatsApp number available", Toast.LENGTH_SHORT).show()
            return
        }
        val encoded = Uri.encode(message)
        val uri = Uri.parse("https://wa.me/$digits?text=$encoded")
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }.onFailure {
            Toast.makeText(context, "Unable to open WhatsApp chat", Toast.LENGTH_SHORT).show()
        }
    }
}
