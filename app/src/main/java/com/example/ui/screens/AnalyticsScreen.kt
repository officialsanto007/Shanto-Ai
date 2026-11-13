package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CrmStage
import com.example.data.model.ShopifyLead
import com.example.ui.theme.InstagramAccent
import com.example.ui.theme.WhatsAppAccent
import com.example.ui.viewmodel.OutreachAnalytics

@Composable
fun AnalyticsScreen(
    analytics: OutreachAnalytics,
    leads: List<ShopifyLead>,
    onOpenLead: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Outreach Performance Analytics", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Only API-confirmed sends are counted as sent. Drafts & manual chat links are tracked separately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 8 KPI Grid (4 rows x 2 columns)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Total Drafts", analytics.totalDrafts, Modifier.weight(1f))
                    MetricCard("Eligible Convs", analytics.eligibleConversations, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("API Sent", analytics.messagesSent, Modifier.weight(1f), highlight = true)
                    MetricCard("Delivered", analytics.messagesDelivered, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Replies Received", analytics.repliesReceived, Modifier.weight(1f))
                    MetricCard("Interested", analytics.interestedProspects, Modifier.weight(1f), highlight = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Qualified Leads", analytics.qualifiedLeads, Modifier.weight(1f))
                    MetricCard("Converted Clients", analytics.convertedClients, Modifier.weight(1f), highlight = true)
                }
            }
        }

        // Channel Performance Comparison Chart (Custom Compose Canvas)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Instagram vs WhatsApp Performance", style = MaterialTheme.typography.titleMedium)

                    val maxVal = maxOf(
                        1,
                        analytics.instagram.totalDrafts,
                        analytics.whatsapp.totalDrafts,
                        analytics.instagram.messagesSent,
                        analytics.whatsapp.messagesSent,
                        analytics.instagram.repliesReceived,
                        analytics.whatsapp.repliesReceived
                    ).toFloat()

                    ChannelComparisonRow(
                        label = "Drafts Created",
                        igVal = analytics.instagram.totalDrafts,
                        waVal = analytics.whatsapp.totalDrafts,
                        maxVal = maxVal
                    )
                    ChannelComparisonRow(
                        label = "API Messages Sent",
                        igVal = analytics.instagram.messagesSent,
                        waVal = analytics.whatsapp.messagesSent,
                        maxVal = maxVal
                    )
                    ChannelComparisonRow(
                        label = "Delivered",
                        igVal = analytics.instagram.messagesDelivered,
                        waVal = analytics.whatsapp.messagesDelivered,
                        maxVal = maxVal
                    )
                    ChannelComparisonRow(
                        label = "Replies Received",
                        igVal = analytics.instagram.repliesReceived,
                        waVal = analytics.whatsapp.repliesReceived,
                        maxVal = maxVal
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text("■ Instagram", style = MaterialTheme.typography.labelSmall, color = InstagramAccent)
                        Text("■ WhatsApp", style = MaterialTheme.typography.labelSmall, color = WhatsAppAccent)
                    }
                }
            }
        }

        // CRM Pipeline Breakdown
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("CRM Pipeline Breakdown", style = MaterialTheme.typography.titleMedium)
                    CrmStage.entries.forEach { stage ->
                        val stageLeads = leads.filter { it.crmStage == stage.name }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stage.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${stageLeads.size} store(s)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (leads.isNotEmpty()) {
                        Text(
                            text = "Tap a lead in Discover to advance CRM stage or record conversions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        leads.take(3).forEach { lead ->
                            Surface(
                                onClick = { onOpenLead(lead.id) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(lead.storeName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(lead.crmStage, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: Int,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChannelComparisonRow(
    label: String,
    igVal: Int,
    waVal: Int,
    maxVal: Float
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text("IG $igVal • WA $waVal", style = MaterialTheme.typography.labelSmall)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
        ) {
            val barHeight = 6.dp.toPx()
            val gap = 4.dp.toPx()
            // IG Track & Bar
            drawRoundRect(
                color = trackColor,
                topLeft = Offset.Zero,
                size = Size(size.width, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            val igWidth = (igVal / maxVal) * size.width
            if (igWidth > 0f) {
                drawRoundRect(
                    color = InstagramAccent,
                    topLeft = Offset.Zero,
                    size = Size(igWidth.coerceAtLeast(8f), barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            }
            // WA Track & Bar
            val secondTop = barHeight + gap
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, secondTop),
                size = Size(size.width, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            val waWidth = (waVal / maxVal) * size.width
            if (waWidth > 0f) {
                drawRoundRect(
                    color = WhatsAppAccent,
                    topLeft = Offset(0f, secondTop),
                    size = Size(waWidth.coerceAtLeast(8f), barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            }
        }
    }
}
