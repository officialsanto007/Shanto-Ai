package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.ApkExportHelper
import com.example.data.model.AiAssistantMode
import com.example.data.model.ChannelIntegration
import com.example.data.model.ConnectionStatus
import com.example.ui.components.ChannelBadge
import com.example.ui.components.OutreachIntentHelper
import com.example.ui.components.StatusPill
import kotlinx.coroutines.launch

@Composable
fun ChannelsScreen(
    instagramIntegration: ChannelIntegration?,
    whatsappIntegration: ChannelIntegration?,
    aiMode: String,
    userEmail: String,
    oauthReceivedToken: String,
    onConnectInstagram: (accessToken: String, appId: String) -> Unit,
    onSyncInstagram: () -> Unit,
    onConnectWhatsApp: (accessToken: String, phoneNumberId: String, wabaId: String, verifyToken: String) -> Unit,
    onDisconnectChannel: (String) -> Unit,
    onUpdateAiMode: (AiAssistantMode) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val buildMetadata = remember(context) { ApkExportHelper.readAppBuildMetadata(context) }

    var apkExportBusy by remember { mutableStateOf(false) }
    var apkExportStatus by remember { mutableStateOf<String?>(null) }

    val saveApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ApkExportHelper.APK_MIME_TYPE)
    ) { targetUri ->
        if (targetUri != null) {
            scope.launch {
                apkExportBusy = true
                apkExportStatus = null
                val result = ApkExportHelper.exportApkToUri(context, targetUri)
                apkExportBusy = false
                apkExportStatus = result.fold(
                    onSuccess = { bytes ->
                        "Saved APK copy (${ApkExportHelper.formatFileSizeMb(bytes)})."
                    },
                    onFailure = { error ->
                        "APK export failed: ${error.localizedMessage ?: "Unknown error"}"
                    }
                )
            }
        }
    }

    var metaAppId by remember { mutableStateOf("") }
    var igAccessToken by remember(oauthReceivedToken) { mutableStateOf(oauthReceivedToken) }

    var waAccessToken by remember { mutableStateOf("") }
    var waPhoneNumberId by remember { mutableStateOf(whatsappIntegration?.phoneNumberId.orEmpty()) }
    var waWabaId by remember { mutableStateOf(whatsappIntegration?.wabaId.orEmpty()) }
    var waVerifyToken by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // SECTION A: INSTAGRAM PROFESSIONAL / BUSINESS ACCOUNT
        item {
            val igConnected = instagramIntegration?.connectionStatus == ConnectionStatus.CONNECTED.name
            val igExpired = instagramIntegration?.connectionStatus == ConnectionStatus.TOKEN_EXPIRED.name

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChannelBadge(channel = "INSTAGRAM")
                        StatusPill(
                            label = instagramIntegration?.connectionStatus ?: ConnectionStatus.DISCONNECTED.name,
                            isPositive = igConnected,
                            isWarning = igExpired
                        )
                    }

                    Text("Instagram Professional & Messaging API", style = MaterialTheme.typography.titleMedium)

                    if (igConnected || igExpired) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!instagramIntegration?.profileImageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = instagramIntegration?.profileImageUrl,
                                    contentDescription = "Instagram profile image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                )
                            }
                            Column {
                                Text(
                                    text = instagramIntegration?.accountName.orEmpty(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = instagramIntegration?.accountHandleOrPhone.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (!instagramIntegration?.permissions.isNullOrEmpty()) {
                                    Text(
                                        text = "Permissions: ${instagramIntegration?.permissions?.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = metaAppId,
                        onValueChange = { metaAppId = it },
                        label = { Text("Meta App ID (for OAuth browser flow)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (metaAppId.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                val scopes = "instagram_basic,instagram_manage_messages,pages_show_list,pages_messaging"
                                val redirect = Uri.encode("leadfinder://oauth/meta")
                                val oauthUrl = "https://www.facebook.com/v21.0/dialog/oauth?client_id=${metaAppId.trim()}&redirect_uri=$redirect&response_type=token&scope=$scopes"
                                OutreachIntentHelper.openUrl(context, oauthUrl)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Launch Meta OAuth Login")
                        }
                    }

                    OutlinedTextField(
                        value = igAccessToken,
                        onValueChange = { igAccessToken = it },
                        label = { Text("Instagram / Meta Graph OAuth Token") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ig_token_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onConnectInstagram(igAccessToken, metaAppId) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("connect_ig_button")
                        ) {
                            Text(if (igExpired) "Reconnect Token" else "Verify & Connect")
                        }
                        if (igConnected) {
                            OutlinedButton(onClick = onSyncInstagram) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Sync Conversations")
                            }
                            OutlinedButton(onClick = { onDisconnectChannel("instagram") }) {
                                Icon(Icons.Rounded.LinkOff, contentDescription = "Disconnect Instagram")
                            }
                        }
                    }
                }
            }
        }

        // SECTION F: WHATSAPP BUSINESS PLATFORM / CLOUD API
        item {
            val waConnected = whatsappIntegration?.connectionStatus == ConnectionStatus.CONNECTED.name
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChannelBadge(channel = "WHATSAPP")
                        StatusPill(
                            label = whatsappIntegration?.connectionStatus ?: ConnectionStatus.DISCONNECTED.name,
                            isPositive = waConnected
                        )
                    }

                    Text("WhatsApp Business Cloud API", style = MaterialTheme.typography.titleMedium)

                    if (waConnected) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Rounded.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "${whatsappIntegration?.accountName} (${whatsappIntegration?.accountHandleOrPhone})",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                                Text(
                                    text = "Approved Templates: ${whatsappIntegration?.approvedTemplates?.joinToString(", ").orEmpty()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = waPhoneNumberId,
                            onValueChange = { waPhoneNumberId = it },
                            label = { Text("Phone Number ID") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = waWabaId,
                            onValueChange = { waWabaId = it },
                            label = { Text("WABA ID") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = waAccessToken,
                        onValueChange = { waAccessToken = it },
                        label = { Text("WhatsApp Cloud API Access Token") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = waVerifyToken,
                        onValueChange = { waVerifyToken = it },
                        label = { Text("Webhook Verify Token (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onConnectWhatsApp(waAccessToken, waPhoneNumberId, waWabaId, waVerifyToken)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("connect_wa_button")
                        ) {
                            Text("Verify & Connect WhatsApp")
                        }
                        if (waConnected) {
                            OutlinedButton(onClick = { onDisconnectChannel("whatsapp") }) {
                                Icon(Icons.Rounded.LinkOff, contentDescription = "Disconnect WhatsApp")
                            }
                        }
                    }
                }
            }
        }

        // SECTION J: AI CONVERSATION ASSISTANT MODE
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("AI Conversation Assistant Mode", style = MaterialTheme.typography.titleMedium)
                    AiAssistantMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = aiMode == mode.name,
                                onClick = { onUpdateAiMode(mode) }
                            )
                            Column {
                                Text(mode.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = if (mode == AiAssistantMode.MANUAL_APPROVAL) {
                                        "AI drafts replies for your review & approval before sending."
                                    } else {
                                        "Automatically replies inside permitted API windows unless human handoff or opt-out is detected."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // APP BUILD & APK EXPORT
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                modifier = Modifier.testTag("apk_export_card")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Android,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.apk_section_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        StatusPill(
                            label = "v${buildMetadata.versionName} (${buildMetadata.buildType})",
                            isPositive = true
                        )
                    }

                    Text(
                        text = stringResource(R.string.apk_section_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Package: ${buildMetadata.applicationId}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Version Code: ${buildMetadata.versionCode} • APK Size: ${ApkExportHelper.formatFileSizeMb(buildMetadata.apkSizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { saveApkLauncher.launch(buildMetadata.suggestedFileName) },
                            enabled = !apkExportBusy,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_apk_button")
                        ) {
                            if (apkExportBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.apk_save_copy))
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    apkExportBusy = true
                                    apkExportStatus = null
                                    val result = ApkExportHelper.prepareShareApkIntent(context)
                                    apkExportBusy = false
                                    result.fold(
                                        onSuccess = { shareIntent ->
                                            context.startActivity(shareIntent)
                                            apkExportStatus = "Opened share sheet for ${buildMetadata.suggestedFileName}."
                                        },
                                        onFailure = { error ->
                                            apkExportStatus = "Share failed: ${error.localizedMessage ?: "Unknown error"}"
                                        }
                                    )
                                }
                            },
                            enabled = !apkExportBusy,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_apk_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.apk_share_file))
                        }
                    }

                    apkExportStatus?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("apk_export_status")
                        )
                    }

                    Text(
                        text = stringResource(R.string.apk_export_tip),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ACCOUNT & SIGN OUT
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Signed in with Google", style = MaterialTheme.typography.titleSmall)
                        Text(userEmail.ifBlank { "Authenticated Account" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = onSignOut, modifier = Modifier.testTag("sign_out_button")) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sign Out")
                    }
                }
            }
        }
    }
}
