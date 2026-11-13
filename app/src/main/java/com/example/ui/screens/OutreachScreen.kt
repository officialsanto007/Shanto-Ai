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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ChannelIntegration
import com.example.data.model.ConnectionStatus
import com.example.data.model.MessageLength
import com.example.data.model.MessageStatus
import com.example.data.model.MessageTone
import com.example.data.model.OutreachChannel
import com.example.data.model.OutreachDraft
import com.example.data.model.ShopifyLead
import com.example.ui.components.ChannelBadge
import com.example.ui.components.OutreachIntentHelper
import com.example.ui.components.StatusPill
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutreachScreen(
    leads: List<ShopifyLead>,
    drafts: List<OutreachDraft>,
    instagramIntegration: ChannelIntegration?,
    whatsappIntegration: ChannelIntegration?,
    editingDraftInitial: OutreachDraft?,
    onClearInitialEditor: () -> Unit,
    onSaveDraft: (OutreachDraft) -> Unit,
    onRegenerateDraft: (OutreachDraft) -> Unit,
    onApproveDraft: (ShopifyLead, OutreachDraft) -> Unit,
    onSendInstagramApi: (ShopifyLead, OutreachDraft) -> Unit,
    onSendWhatsAppApi: (ShopifyLead, OutreachDraft, Boolean) -> Unit,
    onRecordManualOutreach: (ShopifyLead, OutreachChannel, String) -> Unit,
    onOpenConversationForLead: (String, OutreachChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedChannel by remember { mutableStateOf(OutreachChannel.INSTAGRAM) }
    var activeEditDraft by remember(editingDraftInitial) { mutableStateOf(editingDraftInitial) }
    var previewDraft by remember { mutableStateOf<OutreachDraft?>(null) }

    val channelDrafts = remember(drafts, selectedChannel) {
        drafts.filter { it.channel == selectedChannel.name }
    }
    val leadsById = remember(leads) { leads.associateBy { it.id } }

    Column(modifier = modifier.fillMaxSize()) {
        // Channel Switcher Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    OutreachChannel.entries.forEachIndexed { idx, channel ->
                        SegmentedButton(
                            selected = selectedChannel == channel,
                            onClick = { selectedChannel = channel },
                            shape = SegmentedButtonDefaults.itemShape(idx, OutreachChannel.entries.size),
                            modifier = Modifier.testTag("outreach_tab_${channel.name.lowercase()}")
                        ) {
                            Text("${channel.label} Outreach")
                        }
                    }
                }

                val isConnected = if (selectedChannel == OutreachChannel.INSTAGRAM) {
                    instagramIntegration?.connectionStatus == ConnectionStatus.CONNECTED.name
                } else {
                    whatsappIntegration?.connectionStatus == ConnectionStatus.CONNECTED.name
                }
                Text(
                    text = if (selectedChannel == OutreachChannel.INSTAGRAM) {
                        if (isConnected) "Instagram API Connected (${instagramIntegration?.accountHandleOrPhone}). Cold DMs remain manual; API replies work on eligible conversations."
                        else "Instagram API not connected. You can customize AI DMs, copy messages, and open IG profiles manually."
                    } else {
                        if (isConnected) "WhatsApp Business Cloud API Connected (${whatsappIntegration?.accountHandleOrPhone}). Enforces opt-in consent & 24h template rules."
                        else "WhatsApp Cloud API not connected. Customize messages and use Click-to-Chat (wa.me) + Copy."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (channelDrafts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No ${selectedChannel.label} Drafts Yet",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Scan a Shopify store in the Discover tab to run an AI website audit and generate tailored ${selectedChannel.label} proposals.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(channelDrafts, key = { it.id }) { draft ->
                    val lead = leadsById[draft.leadId]
                    if (lead != null) {
                        OutreachDraftCard(
                            lead = lead,
                            draft = draft,
                            channel = selectedChannel,
                            onEdit = { activeEditDraft = draft },
                            onPreview = { previewDraft = draft },
                            onApprove = { onApproveDraft(lead, draft) },
                            onRegenerate = { onRegenerateDraft(draft) },
                            onSendApi = {
                                if (selectedChannel == OutreachChannel.INSTAGRAM) {
                                    onSendInstagramApi(lead, draft)
                                } else {
                                    onSendWhatsAppApi(lead, draft, !lead.whatsappWithinWindow)
                                }
                            },
                            onOpenProfileOrChat = {
                                if (selectedChannel == OutreachChannel.INSTAGRAM) {
                                    OutreachIntentHelper.openUrl(
                                        context,
                                        lead.instagramProfileUrl.ifBlank { "https://instagram.com" }
                                    )
                                } else {
                                    OutreachIntentHelper.openWhatsAppChat(
                                        context,
                                        lead.whatsappNumber,
                                        draft.generatedMessage
                                    )
                                }
                                onRecordManualOutreach(lead, selectedChannel, "Opened ${selectedChannel.label} with copied proposal")
                            },
                            onCopy = {
                                OutreachIntentHelper.copyToClipboard(context, "${selectedChannel.label} Proposal", draft.generatedMessage)
                            },
                            onViewConversation = {
                                onOpenConversationForLead(lead.id, selectedChannel)
                            }
                        )
                    }
                }
            }
        }
    }

    // Message Customization Bottom Sheet (Module D)
    activeEditDraft?.let { draftToEdit ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                activeEditDraft = null
                onClearInitialEditor()
            },
            sheetState = sheetState
        ) {
            DraftCustomizationEditor(
                draft = draftToEdit,
                approvedTemplates = whatsappIntegration?.approvedTemplates ?: listOf("shopify_audit_intro"),
                onSave = { updated ->
                    onSaveDraft(updated)
                    activeEditDraft = null
                    onClearInitialEditor()
                },
                onRegenerate = { updated ->
                    onRegenerateDraft(updated)
                    activeEditDraft = null
                    onClearInitialEditor()
                },
                onCancel = {
                    activeEditDraft = null
                    onClearInitialEditor()
                }
            )
        }
    }

    // Message Preview Dialog
    previewDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { previewDraft = null },
            title = { Text("${draft.businessName} • ${draft.channel} Preview") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusPill(label = "Tone: ${draft.tone} • Length: ${draft.messageLength}")
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = draft.generatedMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    OutreachIntentHelper.copyToClipboard(context, "Message", draft.generatedMessage)
                    previewDraft = null
                }) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { previewDraft = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun OutreachDraftCard(
    lead: ShopifyLead,
    draft: OutreachDraft,
    channel: OutreachChannel,
    onEdit: () -> Unit,
    onPreview: () -> Unit,
    onApprove: () -> Unit,
    onRegenerate: () -> Unit,
    onSendApi: () -> Unit,
    onOpenProfileOrChat: () -> Unit,
    onCopy: () -> Unit,
    onViewConversation: () -> Unit
) {
    val statusEnum = runCatching { MessageStatus.valueOf(draft.status) }.getOrDefault(MessageStatus.DRAFT)
    val dateFormatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.US) }
    val lastContactText = lead.lastContactAt?.toDate()?.let { dateFormatter.format(it) } ?: "Not contacted yet"

    val canSendViaApi = when (channel) {
        OutreachChannel.INSTAGRAM -> lead.instagramApiEligible && lead.instagramUserId.isNotBlank() &&
            statusEnum in listOf(MessageStatus.ELIGIBLE_TO_SEND, MessageStatus.FAILED)
        OutreachChannel.WHATSAPP -> !lead.whatsappOptedOut && lead.whatsappNumber.isNotBlank() &&
            (lead.whatsappWithinWindow || lead.whatsappHasConsent) &&
            statusEnum in listOf(MessageStatus.ELIGIBLE_TO_SEND, MessageStatus.AWAITING_APPROVAL, MessageStatus.FAILED)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(lead.storeName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (channel == OutreachChannel.INSTAGRAM) {
                            if (lead.instagramUsername.isNotBlank()) "@${lead.instagramUsername} • ${lead.websiteUrl}"
                            else lead.websiteUrl
                        } else {
                            "${lead.whatsappNumber.ifBlank { "No WA #" }} • ${lead.websiteUrl}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ChannelBadge(channel = channel.name)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    label = statusEnum.label,
                    isPositive = statusEnum in listOf(MessageStatus.SENT, MessageStatus.DELIVERED, MessageStatus.READ, MessageStatus.REPLIED, MessageStatus.ELIGIBLE_TO_SEND),
                    isWarning = statusEnum == MessageStatus.AWAITING_APPROVAL || statusEnum == MessageStatus.NOT_ELIGIBLE,
                    isError = statusEnum == MessageStatus.FAILED
                )
                Text(
                    text = "Last contact: $lastContactText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (lead.auditSummary.isNotBlank()) {
                Text(
                    text = "Audit (${lead.auditScore}/100): ${lead.auditSummary}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = draft.generatedMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(12.dp)
                )
            }

            if (draft.errorMessage.isNotBlank()) {
                Text(
                    text = "API Error: ${draft.errorMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Primary Actions
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }
                OutlinedButton(onClick = onPreview) {
                    Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Preview")
                }
                OutlinedButton(onClick = onRegenerate) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("AI Rewrite")
                }
                OutlinedButton(onClick = onCopy) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy")
                }
                OutlinedButton(onClick = onViewConversation) {
                    Text("Thread")
                }
            }

            // Approval & Policy-Compliant Send Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (statusEnum == MessageStatus.DRAFT || statusEnum == MessageStatus.AWAITING_APPROVAL) {
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Approve Message")
                    }
                }
                if (canSendViaApi) {
                    Button(
                        onClick = onSendApi,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Send via API")
                    }
                } else {
                    OutlinedButton(
                        onClick = onOpenProfileOrChat,
                        enabled = !(channel == OutreachChannel.WHATSAPP && lead.whatsappOptedOut),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (channel == OutreachChannel.INSTAGRAM) "Open IG & Log" else "WhatsApp Chat")
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftCustomizationEditor(
    draft: OutreachDraft,
    approvedTemplates: List<String>,
    onSave: (OutreachDraft) -> Unit,
    onRegenerate: (OutreachDraft) -> Unit,
    onCancel: () -> Unit
) {
    var greeting by remember { mutableStateOf(draft.greeting) }
    var businessName by remember { mutableStateOf(draft.businessName) }
    var websiteName by remember { mutableStateOf(draft.websiteName) }
    var problemsText by remember { mutableStateOf(draft.websiteProblems.joinToString("\n")) }
    var improvementsText by remember { mutableStateOf(draft.recommendedImprovements.joinToString("\n")) }
    var serviceIntro by remember { mutableStateOf(draft.serviceIntroduction) }
    var cta by remember { mutableStateOf(draft.callToAction) }
    var tone by remember { mutableStateOf(draft.tone) }
    var length by remember { mutableStateOf(draft.messageLength) }
    var templateName by remember { mutableStateOf(draft.templateName) }
    var generatedBody by remember { mutableStateOf(draft.generatedMessage) }

    fun buildUpdatedDraft(): OutreachDraft = draft.copy(
        greeting = greeting,
        businessName = businessName,
        websiteName = websiteName,
        websiteProblems = problemsText.lines().map { it.trim() }.filter { it.isNotBlank() }.take(10),
        recommendedImprovements = improvementsText.lines().map { it.trim() }.filter { it.isNotBlank() }.take(10),
        serviceIntroduction = serviceIntro,
        callToAction = cta,
        tone = tone,
        messageLength = length,
        templateName = templateName,
        generatedMessage = generatedBody
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Customize ${draft.channel} Message", style = MaterialTheme.typography.headlineSmall)

        Text("Tone", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MessageTone.entries.forEach { t ->
                FilterChip(
                    selected = tone == t.name,
                    onClick = { tone = t.name },
                    label = { Text(t.label) }
                )
            }
        }

        Text("Length", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MessageLength.entries.forEach { l ->
                FilterChip(
                    selected = length == l.name,
                    onClick = { length = l.name },
                    label = { Text(l.label) }
                )
            }
        }

        if (draft.channel == OutreachChannel.WHATSAPP.name) {
            Text("Approved WhatsApp Template (for business-initiated sends)", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                approvedTemplates.forEach { tpl ->
                    FilterChip(
                        selected = templateName == tpl,
                        onClick = { templateName = tpl },
                        label = { Text(tpl) }
                    )
                }
            }
        }

        OutlinedTextField(
            value = greeting,
            onValueChange = { greeting = it },
            label = { Text("Greeting") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = businessName,
                onValueChange = { businessName = it },
                label = { Text("Business Name") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = websiteName,
                onValueChange = { websiteName = it },
                label = { Text("Website Name") },
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = problemsText,
            onValueChange = { problemsText = it },
            label = { Text("Website Problems (one per line)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = improvementsText,
            onValueChange = { improvementsText = it },
            label = { Text("Recommended Improvements (one per line)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = serviceIntro,
            onValueChange = { serviceIntro = it },
            label = { Text("Service Introduction") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = cta,
            onValueChange = { cta = it },
            label = { Text("Call to Action") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = generatedBody,
            onValueChange = { generatedBody = it },
            label = { Text("Final Message Text") },
            minLines = 5,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onRegenerate(buildUpdatedDraft()) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_regenerate_button")
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("AI Regenerate")
            }
            Button(
                onClick = { onSave(buildUpdatedDraft()) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("save_custom_draft_button")
            ) {
                Text("Save Draft")
            }
        }
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
            Text("Cancel")
        }
        Spacer(Modifier.height(12.dp))
    }
}
