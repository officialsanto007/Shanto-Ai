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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CrmStage
import com.example.data.model.OutreachChannel
import com.example.data.model.OutreachDraft
import com.example.data.model.ShopifyLead
import com.example.ui.components.AuditScoreRing
import com.example.ui.components.OutreachIntentHelper
import com.example.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadDetailScreen(
    lead: ShopifyLead?,
    drafts: List<OutreachDraft>,
    onBack: () -> Unit,
    onUpdateCrmStage: (String, CrmStage) -> Unit,
    onUpdateWhatsAppCompliance: (String, Boolean, Boolean) -> Unit,
    onOpenOutreachEditor: (OutreachDraft) -> Unit,
    onLogManualOutreach: (ShopifyLead, OutreachChannel, String) -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(lead?.storeName ?: "Lead Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("lead_detail_back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (lead == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Lead not found.")
            }
            return@Scaffold
        }

        val igDraft = drafts.firstOrNull { it.leadId == lead.id && it.channel == OutreachChannel.INSTAGRAM.name }
        val waDraft = drafts.firstOrNull { it.leadId == lead.id && it.channel == OutreachChannel.WHATSAPP.name }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Store Header & Audit Summary
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            AuditScoreRing(score = lead.auditScore)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(lead.storeName, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = lead.websiteUrl,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${lead.location} • ${lead.niche}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { OutreachIntentHelper.openUrl(context, lead.websiteUrl) }) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open storefront")
                            }
                        }

                        if (lead.auditSummary.isNotBlank()) {
                            Text(lead.auditSummary, style = MaterialTheme.typography.bodyMedium)
                        }

                        Text("CRM Pipeline Stage", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CrmStage.entries.forEach { stage ->
                                FilterChip(
                                    selected = lead.crmStage == stage.name,
                                    onClick = { onUpdateCrmStage(lead.id, stage) },
                                    label = { Text(stage.label) }
                                )
                            }
                        }
                    }
                }
            }

            // Public Contact & Social Channels
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Extracted Business Contacts", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Email: ${lead.publicEmail.ifBlank { "Not listed on homepage" }}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Phone: ${lead.publicPhone.ifBlank { "Not listed on homepage" }}",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        // Instagram Contact Option
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (lead.instagramUsername.isNotBlank()) "@${lead.instagramUsername}" else "Instagram Handle Not Detected",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    StatusPill(
                                        label = if (lead.instagramApiEligible) "API Eligible" else "Manual / Profile",
                                        isPositive = lead.instagramApiEligible
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (igDraft != null) {
                                        Button(
                                            onClick = { onOpenOutreachEditor(igDraft) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.EditNote, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Customize DM")
                                        }
                                    }
                                    if (lead.instagramProfileUrl.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                OutreachIntentHelper.openUrl(context, lead.instagramProfileUrl)
                                                onLogManualOutreach(lead, OutreachChannel.INSTAGRAM, "Opened IG profile")
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Open IG")
                                        }
                                    }
                                }
                            }
                        }

                        // WhatsApp Contact Option
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = lead.whatsappNumber.ifBlank { "WhatsApp Number Not Listed" },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (lead.whatsappBusinessName.isNotBlank()) {
                                            Text(
                                                text = lead.whatsappBusinessName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    StatusPill(
                                        label = when {
                                            lead.whatsappOptedOut -> "Opted Out"
                                            lead.whatsappHasConsent -> "Consent Recorded"
                                            else -> "No Opt-In"
                                        },
                                        isPositive = lead.whatsappHasConsent && !lead.whatsappOptedOut,
                                        isError = lead.whatsappOptedOut
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("WhatsApp Consent Recorded", style = MaterialTheme.typography.bodySmall)
                                    Switch(
                                        checked = lead.whatsappHasConsent,
                                        onCheckedChange = { onUpdateWhatsAppCompliance(lead.id, it, lead.whatsappOptedOut) }
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Opted Out (Do Not Message)", style = MaterialTheme.typography.bodySmall)
                                    Switch(
                                        checked = lead.whatsappOptedOut,
                                        onCheckedChange = { onUpdateWhatsAppCompliance(lead.id, lead.whatsappHasConsent, it) }
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (waDraft != null) {
                                        Button(
                                            onClick = { onOpenOutreachEditor(waDraft) },
                                            enabled = !lead.whatsappOptedOut,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.EditNote, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Edit WA")
                                        }
                                    }
                                    if (lead.whatsappNumber.isNotBlank() && !lead.whatsappOptedOut) {
                                        OutlinedButton(
                                            onClick = {
                                                OutreachIntentHelper.openWhatsAppChat(
                                                    context,
                                                    lead.whatsappNumber,
                                                    waDraft?.generatedMessage.orEmpty()
                                                )
                                                onLogManualOutreach(lead, OutreachChannel.WHATSAPP, "Opened wa.me click-to-chat")
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Open Chat")
                                        }
                                    }
                                }
                                if (waDraft != null) {
                                    OutlinedButton(
                                        onClick = {
                                            OutreachIntentHelper.copyToClipboard(context, "WhatsApp Message", waDraft.generatedMessage)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Copy WhatsApp Message")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Detected Opportunities & Raw Website Signals
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("AI Website Audit Opportunities (3–5)", style = MaterialTheme.typography.titleMedium)
                        lead.auditOpportunities.forEachIndexed { index, item ->
                            Text(
                                text = "${index + 1}. $item",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (lead.auditSignals.isNotEmpty()) {
                            Text(
                                text = "Live HTML Signals Inspected",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            lead.auditSignals.forEach { sig ->
                                Text(
                                    text = "• $sig",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
