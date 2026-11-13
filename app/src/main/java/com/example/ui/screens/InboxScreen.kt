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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Webhook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ChannelIntegration
import com.example.data.model.ConversationThread
import com.example.data.model.MessageStatus
import com.example.data.model.OutreachChannel
import com.example.data.model.OutreachDraft
import com.example.data.model.ShopifyLead
import com.example.ui.components.ChannelBadge
import com.example.ui.components.StatusPill

enum class InboxFilter(val label: String) {
    ALL("All"),
    INSTAGRAM("Instagram"),
    WHATSAPP("WhatsApp"),
    DRAFTS("Drafts"),
    AWAITING_APPROVAL("Awaiting Approval"),
    SENT("Sent"),
    REPLIED("Replied"),
    FAILED("Failed"),
    FOLLOW_UP("Follow-up Needed")
}

@Composable
fun InboxScreen(
    leads: List<ShopifyLead>,
    drafts: List<OutreachDraft>,
    conversations: List<ConversationThread>,
    instagramIntegration: ChannelIntegration?,
    whatsappIntegration: ChannelIntegration?,
    onOpenConversation: (String) -> Unit,
    onSyncInstagramConversations: () -> Unit,
    onProcessWebhookPayload: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeFilter by remember { mutableStateOf(InboxFilter.ALL) }
    var showWebhookDialog by remember { mutableStateOf(false) }

    val interestedLeads = remember(leads) {
        leads.filter { it.prospectIntent == "INTERESTED" || it.needsHumanHandoff }
    }

    val filteredConversations = remember(conversations, drafts, activeFilter) {
        when (activeFilter) {
            InboxFilter.ALL -> conversations
            InboxFilter.INSTAGRAM -> conversations.filter { it.channel == OutreachChannel.INSTAGRAM.name }
            InboxFilter.WHATSAPP -> conversations.filter { it.channel == OutreachChannel.WHATSAPP.name }
            InboxFilter.DRAFTS -> {
                val leadIds = drafts.filter { it.status == MessageStatus.DRAFT.name }.map { it.leadId }.toSet()
                conversations.filter { it.leadId in leadIds }
            }
            InboxFilter.AWAITING_APPROVAL -> {
                val leadIds = drafts.filter { it.status == MessageStatus.AWAITING_APPROVAL.name }.map { it.leadId }.toSet()
                conversations.filter { it.leadId in leadIds }
            }
            InboxFilter.SENT -> conversations.filter { it.deliveryStatus in listOf("SENT", "DELIVERED", "READ") }
            InboxFilter.REPLIED -> conversations.filter { it.deliveryStatus == "REPLIED" || it.lastMessageDirection == "INBOUND" }
            InboxFilter.FAILED -> conversations.filter { it.deliveryStatus == "FAILED" }
            InboxFilter.FOLLOW_UP -> conversations.filter { it.followUpNeeded }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Unified Channel Status & Sync Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Unified Outreach Center", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "IG: ${instagramIntegration?.accountHandleOrPhone?.ifBlank { "Not connected" } ?: "Not connected"} • WA: ${whatsappIntegration?.accountHandleOrPhone?.ifBlank { "Not connected" } ?: "Not connected"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onSyncInstagramConversations,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sync IG Inbox")
                    }
                    OutlinedButton(
                        onClick = { showWebhookDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("webhook_tester_button")
                    ) {
                        Icon(Icons.Rounded.Webhook, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ingest Webhook")
                    }
                }

                // Filters
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InboxFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = activeFilter == filter,
                            onClick = { activeFilter = filter },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (interestedLeads.isNotEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${interestedLeads.size} interested prospect(s) / handoff alert(s): ${interestedLeads.joinToString { it.storeName }}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            if (filteredConversations.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("No Matching Conversations", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Send an approved outreach message, sync Instagram Messaging conversations, or ingest a Meta webhook event to test incoming prospect replies.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredConversations, key = { it.id }) { thread ->
                    ConversationRowCard(
                        thread = thread,
                        onClick = { onOpenConversation(thread.id) }
                    )
                }
            }
        }
    }

    if (showWebhookDialog) {
        WebhookIngestDialog(
            leads = leads,
            onDismiss = { showWebhookDialog = false },
            onSubmit = { payload ->
                onProcessWebhookPayload(payload)
                showWebhookDialog = false
            }
        )
    }
}

@Composable
private fun ConversationRowCard(
    thread: ConversationThread,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(thread.storeName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = thread.recipientHandleOrPhone,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ChannelBadge(channel = thread.channel)
            }

            Text(
                text = thread.lastMessagePreview.ifBlank { "Conversation initialized" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    label = thread.deliveryStatus,
                    isPositive = thread.deliveryStatus in listOf("DELIVERED", "READ", "REPLIED", "SENT"),
                    isError = thread.deliveryStatus == "FAILED"
                )
                if (thread.aiDetectedIntent.isNotBlank() && thread.aiDetectedIntent != "NONE") {
                    StatusPill(
                        label = "Intent: ${thread.aiDetectedIntent}",
                        isPositive = thread.aiDetectedIntent == "INTERESTED"
                    )
                }
                if (thread.needsHumanHandoff) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            Icons.Rounded.SupportAgent,
                            contentDescription = "Human handoff requested",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text("Handoff", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun WebhookIngestDialog(
    leads: List<ShopifyLead>,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    val firstWa = leads.firstOrNull { it.whatsappNumber.isNotBlank() }?.whatsappNumber?.filter(Char::isDigit) ?: "15551234567"
    val sampleWaJson = """
        {
          "object": "whatsapp_business_account",
          "entry": [{
            "changes": [{
              "value": {
                "contacts": [{"profile": {"name": "Shopify Store Owner"}}],
                "messages": [{
                  "from": "$firstWa",
                  "id": "wamid.sample_${System.currentTimeMillis()}",
                  "text": {"body": "Thanks for reaching out! Yes, we've been looking to improve our mobile product page conversion. Can you send over the free audit?"}
                }]
              }
            }]
          }]
        }
    """.trimIndent()

    var jsonText by remember { mutableStateOf(sampleWaJson) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Meta Webhook Event Processor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Paste an official Instagram or WhatsApp Business Cloud API webhook JSON payload to test inbound message parsing, delivery receipts, and AI Conversation Assistant replies.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    label = { Text("Webhook JSON") },
                    minLines = 6,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(jsonText) }) {
                Text("Process Event")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
