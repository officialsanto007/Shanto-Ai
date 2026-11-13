package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.AiAssistantMode
import com.example.data.model.ChatMessage
import com.example.data.model.ConversationThread
import com.example.ui.components.ChannelBadge
import com.example.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationDetailScreen(
    thread: ConversationThread?,
    messages: List<ChatMessage>,
    aiMode: String,
    onBack: () -> Unit,
    onGenerateAiReply: (ConversationThread) -> Unit,
    onSendReply: (ConversationThread, String, Boolean) -> Unit,
    onToggleHumanHandoff: (ConversationThread) -> Unit
) {
    var composerText by remember { mutableStateOf("") }
    var editableAiReply by remember(thread?.aiSuggestedReply) {
        mutableStateOf(thread?.aiSuggestedReply.orEmpty())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(thread?.storeName ?: "Conversation", style = MaterialTheme.typography.titleMedium)
                        if (thread != null) {
                            Text(
                                text = "${thread.channel} • ${thread.recipientHandleOrPhone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (thread != null) {
                        IconButton(onClick = { onToggleHumanHandoff(thread) }) {
                            Icon(
                                imageVector = Icons.Rounded.SupportAgent,
                                contentDescription = "Toggle Human Handoff",
                                tint = if (thread.needsHumanHandoff) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (thread == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Conversation not found.")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // AI Conversation Assistant Panel
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                    Text("AI Conversation Assistant", style = MaterialTheme.typography.titleSmall)
                                }
                                val modeLabel = runCatching { AiAssistantMode.valueOf(aiMode).label }.getOrDefault("Manual Approval")
                                StatusPill(label = modeLabel)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChannelBadge(channel = thread.channel)
                                if (thread.aiDetectedIntent.isNotBlank() && thread.aiDetectedIntent != "NONE") {
                                    StatusPill(
                                        label = "Intent: ${thread.aiDetectedIntent}",
                                        isPositive = thread.aiDetectedIntent == "INTERESTED"
                                    )
                                }
                                if (thread.needsHumanHandoff) {
                                    StatusPill(label = "Human Handoff", isWarning = true)
                                }
                            }

                            if (thread.aiSuggestedServices.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    thread.aiSuggestedServices.forEach { service ->
                                        AssistChip(onClick = {}, label = { Text(service, style = MaterialTheme.typography.labelSmall) })
                                    }
                                }
                            }

                            if (editableAiReply.isNotBlank()) {
                                OutlinedTextField(
                                    value = editableAiReply,
                                    onValueChange = { editableAiReply = it },
                                    label = { Text("Suggested Reply (Editable)") },
                                    minLines = 3,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onGenerateAiReply(thread) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Prepare AI Reply")
                                }
                                if (editableAiReply.isNotBlank()) {
                                    Button(
                                        onClick = { onSendReply(thread, editableAiReply, true) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Approve & Send")
                                    }
                                }
                            }
                        }
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    val isOutbound = msg.direction == "OUTBOUND"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isOutbound) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = if (isOutbound) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(0.84f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = msg.senderName.ifBlank { msg.direction },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(msg.body, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${msg.status}${if (msg.apiConfirmed) " • API Confirmed" else " • Local Log"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (msg.status == "FAILED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Manual Reply Composer
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = composerText,
                        onValueChange = { composerText = it },
                        placeholder = { Text("Write reply (human handoff)…") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onSendReply(thread, composerText, false)
                            composerText = ""
                        },
                        enabled = composerText.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send reply")
                    }
                }
            }
        }
    }
}
