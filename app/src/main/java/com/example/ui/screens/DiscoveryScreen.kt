package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CrmStage
import com.example.data.model.ShopifyLead
import com.example.ui.components.AuditScoreRing
import com.example.ui.components.StatusPill

@Composable
fun DiscoveryScreen(
    leads: List<ShopifyLead>,
    isBusy: Boolean,
    onScanStore: (
        websiteUrl: String,
        niche: String,
        location: String,
        manualIg: String,
        manualWa: String,
        waConsent: Boolean
    ) -> Unit,
    onOpenLead: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var websiteUrl by remember { mutableStateOf("") }
    var niche by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("Austin, TX, USA") }
    var manualIg by remember { mutableStateOf("") }
    var manualWa by remember { mutableStateOf("") }
    var waConsent by remember { mutableStateOf(false) }
    var selectedStageFilter by remember { mutableStateOf<String?>(null) }

    val filteredLeads = remember(leads, selectedStageFilter) {
        if (selectedStageFilter == null) leads
        else leads.filter { it.crmStage == selectedStageFilter }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "USA Shopify Store Scanner & AI Audit",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Fetches live storefront HTML, extracts IG/WhatsApp contacts, and runs Gemini CRO analysis.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Live public Shopify store URL shortcuts for instant real scanning
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "allbirds.com" to "Sustainable Footwear",
                            "brooklinen.com" to "Home & Bedding",
                            "gymshark.com" to "Athletic Apparel",
                            "chubbiesshorts.com" to "Menswear"
                        ).forEach { (domain, sampleNiche) ->
                            AssistChip(
                                onClick = {
                                    websiteUrl = "https://$domain"
                                    niche = sampleNiche
                                },
                                label = { Text(domain, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = websiteUrl,
                        onValueChange = { websiteUrl = it },
                        label = { Text("Shopify Store URL") },
                        placeholder = { Text("https://example-shopify-store.com") },
                        leadingIcon = { Icon(Icons.Rounded.Language, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("store_url_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = niche,
                            onValueChange = { niche = it },
                            label = { Text("Niche") },
                            placeholder = { Text("Apparel, Beauty…") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text("USA Location") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = manualIg,
                            onValueChange = { manualIg = it },
                            label = { Text("Instagram @ (optional)") },
                            placeholder = { Text("Auto-detected if public") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = manualWa,
                            onValueChange = { manualWa = it },
                            label = { Text("WhatsApp # (optional)") },
                            placeholder = { Text("+15551234567") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { waConsent = !waConsent }
                    ) {
                        Checkbox(
                            checked = waConsent,
                            onCheckedChange = { waConsent = it }
                        )
                        Text(
                            text = "Lead opted in / consented to WhatsApp outreach",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            onScanStore(websiteUrl, niche, location, manualIg, manualWa, waConsent)
                        },
                        enabled = !isBusy && websiteUrl.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("scan_store_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan Website & Generate AI Outreach")
                    }
                }
            }
        }

        // CRM Pipeline Stage Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedStageFilter == null,
                    onClick = { selectedStageFilter = null },
                    label = { Text("All (${leads.size})") }
                )
                listOf(
                    CrmStage.OUTREACH_READY,
                    CrmStage.CONTACTED,
                    CrmStage.REPLIED,
                    CrmStage.INTERESTED,
                    CrmStage.QUALIFIED,
                    CrmStage.CONVERTED
                ).forEach { stage ->
                    val count = leads.count { it.crmStage == stage.name }
                    FilterChip(
                        selected = selectedStageFilter == stage.name,
                        onClick = {
                            selectedStageFilter = if (selectedStageFilter == stage.name) null else stage.name
                        },
                        label = { Text("${stage.label} ($count)") }
                    )
                }
            }
        }

        if (filteredLeads.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_leads),
                            contentDescription = stringResource(R.string.cd_empty_leads),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Text(
                            text = if (leads.isEmpty()) "No Shopify Leads Scanned Yet" else "No Leads Match This Stage",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Tap a US Shopify domain chip above or paste any storefront URL to inspect live site structure and create personalized Instagram & WhatsApp proposals.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredLeads, key = { it.id }) { lead ->
                LeadSummaryCard(lead = lead, onClick = { onOpenLead(lead.id) })
            }
        }
    }
}

@Composable
private fun LeadSummaryCard(
    lead: ShopifyLead,
    onClick: () -> Unit
) {
    val stageLabel = runCatching { CrmStage.valueOf(lead.crmStage).label }.getOrDefault(lead.crmStage)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lead_card_${lead.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AuditScoreRing(score = lead.auditScore)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lead.storeName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = lead.websiteUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${lead.location} • ${lead.niche}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                StatusPill(
                    label = stageLabel,
                    isPositive = lead.crmStage in listOf(
                        CrmStage.INTERESTED.name,
                        CrmStage.QUALIFIED.name,
                        CrmStage.CONVERTED.name
                    ),
                    isError = lead.crmStage == CrmStage.OPTED_OUT.name
                )
            }

            if (lead.auditOpportunities.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "• ${lead.auditOpportunities.first()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (lead.instagramUsername.isNotBlank()) {
                    StatusPill(label = "@${lead.instagramUsername}", isPositive = lead.instagramApiEligible)
                }
                if (lead.whatsappNumber.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text(lead.whatsappNumber, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (lead.publicEmail.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            lead.publicEmail,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
