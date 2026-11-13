package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.SecureTokenStore
import com.example.data.ai.FirebaseAiOutreachService
import com.example.data.model.AiAssistantMode
import com.example.data.model.ChannelIntegration
import com.example.data.model.ChatMessage
import com.example.data.model.ConnectionStatus
import com.example.data.model.ConversationThread
import com.example.data.model.CrmStage
import com.example.data.model.MessageLength
import com.example.data.model.MessageStatus
import com.example.data.model.MessageTone
import com.example.data.model.OutreachChannel
import com.example.data.model.OutreachDraft
import com.example.data.model.ShopifyLead
import com.example.data.model.UserProfile
import com.example.data.remote.MetaGraphApiClient
import com.example.data.remote.ShopifyStoreScanner
import com.example.data.repository.FirestoreOutreachRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChannelMetrics(
    val totalDrafts: Int = 0,
    val eligibleConversations: Int = 0,
    val messagesSent: Int = 0,
    val messagesDelivered: Int = 0,
    val repliesReceived: Int = 0
)

data class OutreachAnalytics(
    val totalDrafts: Int = 0,
    val eligibleConversations: Int = 0,
    val messagesSent: Int = 0,
    val messagesDelivered: Int = 0,
    val repliesReceived: Int = 0,
    val interestedProspects: Int = 0,
    val qualifiedLeads: Int = 0,
    val convertedClients: Int = 0,
    val instagram: ChannelMetrics = ChannelMetrics(),
    val whatsapp: ChannelMetrics = ChannelMetrics()
)

sealed interface ActionUiState {
    data object Idle : ActionUiState
    data class Busy(val message: String) : ActionUiState
    data class Success(val message: String) : ActionUiState
    data class Error(val message: String) : ActionUiState
}

data class DashboardUiState(
    val userProfile: UserProfile? = null,
    val leads: List<ShopifyLead> = emptyList(),
    val drafts: List<OutreachDraft> = emptyList(),
    val conversations: List<ConversationThread> = emptyList(),
    val instagramIntegration: ChannelIntegration? = null,
    val whatsappIntegration: ChannelIntegration? = null,
    val analytics: OutreachAnalytics = OutreachAnalytics(),
    val streamError: String? = null
)

class LeadFinderViewModel(
    private val repository: FirestoreOutreachRepository,
    private val scanner: ShopifyStoreScanner,
    private val metaClient: MetaGraphApiClient,
    private val aiService: FirebaseAiOutreachService,
    private val tokenStore: SecureTokenStore,
    private val currentUserId: String,
    userDisplayName: String,
    userEmail: String
) : ViewModel() {

    private val _actionState = MutableStateFlow<ActionUiState>(ActionUiState.Idle)
    val actionState: StateFlow<ActionUiState> = _actionState.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(currentUserId, userDisplayName, userEmail)
        }
    }

    val dashboardState: StateFlow<DashboardUiState> = combine(
        repository.observeUserProfile(currentUserId),
        repository.observeLeads(currentUserId),
        repository.observeDrafts(currentUserId),
        repository.observeConversations(currentUserId),
        repository.observeIntegrations(currentUserId)
    ) { profile, leads, drafts, conversations, integrations ->
        val ig = integrations.firstOrNull { it.channel == OutreachChannel.INSTAGRAM.name }
        val wa = integrations.firstOrNull { it.channel == OutreachChannel.WHATSAPP.name }
        DashboardUiState(
            userProfile = profile,
            leads = leads,
            drafts = drafts,
            conversations = conversations,
            instagramIntegration = ig,
            whatsappIntegration = wa,
            analytics = computeAnalytics(leads, drafts, conversations)
        )
    }
        .catch { error ->
            val fsEx = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsEx == null && error is CancellationException) throw error
            val root = fsEx ?: error
            Log.e(TAG, "Error observing dashboard state", root)
            emit(DashboardUiState(streamError = root.localizedMessage ?: "Failed to sync data"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DashboardUiState()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<ChatMessage>> = _activeConversationId
        .flatMapLatest { convId ->
            if (convId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeMessages(currentUserId, convId)
                    .catch { error ->
                        val fsEx = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsEx == null && error is CancellationException) throw error
                        Log.e(TAG, "Error observing messages", fsEx ?: error)
                        emit(emptyList())
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    fun setActiveConversation(conversationId: String?) {
        _activeConversationId.value = conversationId
    }

    fun clearActionState() {
        _actionState.value = ActionUiState.Idle
    }

    // --- STEP 1-5: SCAN SHOPIFY STORE, RUN AI WEBSITE AUDIT, GENERATE IG & WA DRAFTS ---
    fun scanAndAuditStore(
        websiteUrl: String,
        niche: String,
        location: String,
        manualInstagramHandle: String,
        manualWhatsappNumber: String,
        whatsappConsentGranted: Boolean
    ) {
        viewModelScope.launch {
            if (websiteUrl.isBlank()) {
                _actionState.value = ActionUiState.Error("Please enter a Shopify store URL.")
                return@launch
            }
            _actionState.value = ActionUiState.Busy("Scanning storefront & extracting contact signals...")

            val scanResult = scanner.scanStore(websiteUrl).getOrElse { err ->
                _actionState.value = ActionUiState.Error("Website scan failed: ${err.localizedMessage ?: "Unreachable URL"}")
                return@launch
            }

            val igHandle = manualInstagramHandle.trim().removePrefix("@")
                .ifBlank { scanResult.instagramUsername }
            val igUrl = if (igHandle.isNotBlank()) "https://instagram.com/$igHandle" else scanResult.instagramProfileUrl
            val waPhone = manualWhatsappNumber.trim().ifBlank { scanResult.whatsappNumber }

            val initialLead = ShopifyLead(
                userId = currentUserId,
                storeName = scanResult.storeName,
                websiteUrl = scanResult.normalizedUrl,
                niche = niche.ifBlank { "Shopify D2C" },
                location = location.ifBlank { "USA" },
                publicEmail = scanResult.publicEmails.firstOrNull().orEmpty(),
                publicPhone = scanResult.publicPhones.firstOrNull().orEmpty(),
                instagramUsername = igHandle,
                instagramProfileUrl = igUrl,
                whatsappNumber = waPhone,
                whatsappBusinessName = scanResult.storeName,
                whatsappHasConsent = whatsappConsentGranted,
                instagramApiEligible = false,
                crmStage = CrmStage.DISCOVERED.name,
                auditSignals = scanResult.structuralSignals,
                instagramStatus = if (igHandle.isNotBlank()) MessageStatus.NOT_ELIGIBLE.name else MessageStatus.DRAFT.name,
                whatsappStatus = if (waPhone.isNotBlank() && whatsappConsentGranted) MessageStatus.DRAFT.name else MessageStatus.NOT_ELIGIBLE.name
            )

            val leadId = repository.saveLead(currentUserId, initialLead).getOrElse { err ->
                _actionState.value = ActionUiState.Error("Failed saving lead: ${err.localizedMessage}")
                return@launch
            }

            _actionState.value = ActionUiState.Busy("AI analyzing design, UX & generating tailored proposals...")
            val audit = aiService.generateWebsiteAuditAndOutreach(initialLead.copy(id = leadId), scanResult)
                .getOrElse { err ->
                    _actionState.value = ActionUiState.Error("AI audit failed: ${err.localizedMessage}")
                    return@launch
                }

            val auditedLeadUpdates = mapOf(
                "crmStage" to CrmStage.OUTREACH_READY.name,
                "auditScore" to audit.auditScore,
                "auditSummary" to audit.auditSummary,
                "auditOpportunities" to audit.opportunities.take(10),
                "instagramStatus" to MessageStatus.AWAITING_APPROVAL.name,
                "whatsappStatus" to MessageStatus.AWAITING_APPROVAL.name
            )
            repository.updateLeadFields(currentUserId, leadId, auditedLeadUpdates)

            val igDraft = OutreachDraft(
                userId = currentUserId,
                leadId = leadId,
                channel = OutreachChannel.INSTAGRAM.name,
                greeting = audit.greeting,
                businessName = scanResult.storeName,
                websiteName = scanResult.domain,
                websiteProblems = audit.opportunities,
                recommendedImprovements = audit.recommendedImprovements,
                serviceIntroduction = audit.serviceIntroduction,
                callToAction = audit.callToAction,
                tone = MessageTone.FRIENDLY.name,
                messageLength = MessageLength.MEDIUM.name,
                generatedMessage = audit.instagramMessage,
                status = MessageStatus.AWAITING_APPROVAL.name,
                apiConfirmed = false
            )
            repository.saveDraft(currentUserId, igDraft)

            val waDraft = OutreachDraft(
                userId = currentUserId,
                leadId = leadId,
                channel = OutreachChannel.WHATSAPP.name,
                greeting = audit.greeting,
                businessName = scanResult.storeName,
                websiteName = scanResult.domain,
                websiteProblems = audit.opportunities,
                recommendedImprovements = audit.recommendedImprovements,
                serviceIntroduction = audit.serviceIntroduction,
                callToAction = audit.callToAction,
                tone = MessageTone.PROFESSIONAL.name,
                messageLength = MessageLength.MEDIUM.name,
                generatedMessage = audit.whatsappMessage,
                templateName = whatsappIntegrationTemplateOrDefault(),
                status = MessageStatus.AWAITING_APPROVAL.name,
                apiConfirmed = false
            )
            repository.saveDraft(currentUserId, waDraft)

            _actionState.value = ActionUiState.Success("Audited ${scanResult.storeName} (${audit.auditScore}/100) & generated IG + WA drafts.")
        }
    }

    private fun whatsappIntegrationTemplateOrDefault(): String {
        return dashboardState.value.whatsappIntegration?.approvedTemplates?.firstOrNull()
            ?: "shopify_audit_intro"
    }

    // --- CUSTOMIZATION & AI REGENERATION ---
    fun saveCustomizedDraft(draft: OutreachDraft) {
        viewModelScope.launch {
            _actionState.value = ActionUiState.Busy("Saving customized message...")
            repository.saveDraft(currentUserId, draft)
                .onSuccess { _actionState.value = ActionUiState.Success("Draft updated and ready for approval.") }
                .onFailure { _actionState.value = ActionUiState.Error(it.localizedMessage ?: "Failed to save draft") }
        }
    }

    fun regenerateDraftWithAi(draft: OutreachDraft) {
        viewModelScope.launch {
            _actionState.value = ActionUiState.Busy("AI regenerating ${draft.channel.lowercase()} message...")
            val regenerated = aiService.regenerateCustomMessage(draft).getOrElse { err ->
                _actionState.value = ActionUiState.Error(err.localizedMessage ?: "AI regeneration failed")
                return@launch
            }
            val updated = draft.copy(
                generatedMessage = regenerated,
                status = MessageStatus.AWAITING_APPROVAL.name
            )
            repository.saveDraft(currentUserId, updated)
                .onSuccess { _actionState.value = ActionUiState.Success("AI generated a fresh variation.") }
                .onFailure { _actionState.value = ActionUiState.Error(it.localizedMessage ?: "Save failed") }
        }
    }

    fun approveDraft(lead: ShopifyLead, draft: OutreachDraft) {
        viewModelScope.launch {
            val isEligible = when (draft.channel) {
                OutreachChannel.INSTAGRAM.name -> lead.instagramApiEligible && lead.instagramUserId.isNotBlank()
                OutreachChannel.WHATSAPP.name -> !lead.whatsappOptedOut && lead.whatsappNumber.isNotBlank() &&
                    (lead.whatsappWithinWindow || (lead.whatsappHasConsent && draft.templateName.isNotBlank()))
                else -> false
            }
            val nextStatus = if (isEligible) MessageStatus.ELIGIBLE_TO_SEND.name else MessageStatus.NOT_ELIGIBLE.name
            repository.updateDraftFields(currentUserId, draft.id, mapOf("status" to nextStatus))
            val statusKey = if (draft.channel == OutreachChannel.INSTAGRAM.name) "instagramStatus" else "whatsappStatus"
            repository.updateLeadFields(currentUserId, lead.id, mapOf(statusKey to nextStatus))
            _actionState.value = ActionUiState.Success(
                if (isEligible) "Approved & eligible for official API sending."
                else "Approved! API eligibility not met — use Open Profile / Click-to-Chat & Copy."
            )
        }
    }

    // --- INSTAGRAM CONNECT & MESSAGING API ---
    fun connectInstagramAccount(accessToken: String, appId: String) {
        viewModelScope.launch {
            if (accessToken.isBlank()) {
                _actionState.value = ActionUiState.Error("Enter a valid Meta Graph OAuth access token.")
                return@launch
            }
            _actionState.value = ActionUiState.Busy("Verifying Instagram Professional account with Meta Graph API...")
            val profile = metaClient.fetchInstagramProfile(accessToken.trim()).getOrElse { err ->
                _actionState.value = ActionUiState.Error(err.localizedMessage ?: "Instagram connection failed")
                return@launch
            }
            tokenStore.saveSecret(currentUserId, SecureTokenStore.KEY_INSTAGRAM_TOKEN, accessToken.trim())
            if (appId.isNotBlank()) {
                tokenStore.saveSecret(currentUserId, SecureTokenStore.KEY_META_APP_ID, appId.trim())
            }

            val integration = ChannelIntegration(
                userId = currentUserId,
                channel = OutreachChannel.INSTAGRAM.name,
                connectionStatus = ConnectionStatus.CONNECTED.name,
                accountName = profile.name,
                accountHandleOrPhone = "@${profile.username}",
                profileImageUrl = profile.profileImageUrl,
                businessAccountId = profile.igAccountId,
                permissions = profile.permissions.ifEmpty {
                    listOf("instagram_basic", "instagram_manage_messages")
                },
                webhookVerified = true,
                tokenExpiresAtMillis = System.currentTimeMillis() + 60L * 24 * 60 * 60 * 1000
            )
            repository.saveIntegration(currentUserId, "instagram", integration)
                .onSuccess {
                    _actionState.value = ActionUiState.Success("Connected Instagram Professional @${profile.username}.")
                }
                .onFailure {
                    _actionState.value = ActionUiState.Error(it.localizedMessage ?: "Failed saving integration")
                }
        }
    }

    fun syncInstagramConversations() {
        viewModelScope.launch {
            val token = tokenStore.getSecret(currentUserId, SecureTokenStore.KEY_INSTAGRAM_TOKEN)
            val igId = dashboardState.value.instagramIntegration?.businessAccountId.orEmpty()
            if (token.isBlank()) {
                _actionState.value = ActionUiState.Error("Connect your Instagram Professional account first.")
                return@launch
            }
            _actionState.value = ActionUiState.Busy("Syncing eligible Instagram conversations...")
            val convs = metaClient.fetchInstagramConversations(token, igId).getOrElse { err ->
                if (err.message?.contains("expired", ignoreCase = true) == true || err.message?.contains("190") == true) {
                    markChannelTokenExpired("instagram")
                }
                _actionState.value = ActionUiState.Error(err.localizedMessage ?: "Failed syncing conversations")
                return@launch
            }

            val leads = dashboardState.value.leads
            for (conv in convs) {
                val matchedLead = leads.firstOrNull {
                    it.instagramUsername.equals(conv.participantUsername, ignoreCase = true) ||
                        it.instagramUserId == conv.participantId
                }
                val leadId = matchedLead?.id ?: ""
                if (matchedLead != null) {
                    repository.updateLeadFields(
                        currentUserId,
                        matchedLead.id,
                        mapOf(
                            "instagramUserId" to conv.participantId,
                            "instagramApiEligible" to true,
                            "instagramStatus" to MessageStatus.ELIGIBLE_TO_SEND.name
                        )
                    )
                }
                val latest = conv.messages.firstOrNull()
                val existingThread = dashboardState.value.conversations.firstOrNull {
                    it.externalConversationId == conv.conversationId
                }
                val threadId = repository.upsertConversation(
                    currentUserId,
                    (existingThread ?: ConversationThread()).copy(
                        userId = currentUserId,
                        leadId = leadId.ifBlank { conv.participantId },
                        storeName = matchedLead?.storeName ?: "@${conv.participantUsername.ifBlank { conv.participantId }}",
                        channel = OutreachChannel.INSTAGRAM.name,
                        recipientHandleOrPhone = "@${conv.participantUsername}",
                        externalConversationId = conv.conversationId,
                        isApiEligible = true,
                        withinServiceWindow = true,
                        lastMessagePreview = latest?.text.orEmpty(),
                        lastMessageDirection = if (latest?.senderId == igId) "OUTBOUND" else "INBOUND",
                        deliveryStatus = if (latest?.senderId == igId) "SENT" else "REPLIED"
                    )
                ).getOrNull()

                if (threadId != null && latest != null && latest.text.isNotBlank()) {
                    repository.addMessage(
                        currentUserId,
                        threadId,
                        ChatMessage(
                            userId = currentUserId,
                            conversationId = threadId,
                            leadId = leadId.ifBlank { conv.participantId },
                            channel = OutreachChannel.INSTAGRAM.name,
                            direction = if (latest.senderId == igId) "OUTBOUND" else "INBOUND",
                            body = latest.text,
                            senderName = latest.senderName,
                            status = if (latest.senderId == igId) "SENT" else "RECEIVED",
                            apiConfirmed = true,
                            externalMessageId = latest.messageId
                        )
                    )
                }
            }
            _actionState.value = ActionUiState.Success("Synced ${convs.size} eligible Instagram conversation(s).")
        }
    }

    fun sendInstagramApiMessage(lead: ShopifyLead, draft: OutreachDraft) {
        viewModelScope.launch {
            if (!lead.instagramApiEligible || lead.instagramUserId.isBlank()) {
                _actionState.value = ActionUiState.Error(
                    "Instagram Messaging API only permits replies to eligible conversations. Use Open Instagram Profile + Copy Message."
                )
                return@launch
            }
            val token = tokenStore.getSecret(currentUserId, SecureTokenStore.KEY_INSTAGRAM_TOKEN)
            val igAccountId = dashboardState.value.instagramIntegration?.businessAccountId.orEmpty()
            if (token.isBlank()) {
                _actionState.value = ActionUiState.Error("Instagram account token missing or disconnected.")
                return@launch
            }
            _actionState.value = ActionUiState.Busy("Sending via official Instagram Messaging API...")
            val sendRes = metaClient.sendInstagramMessage(
                accessToken = token,
                igAccountId = igAccountId,
                recipientIgsid = lead.instagramUserId,
                messageText = draft.generatedMessage
            )

            if (sendRes.success) {
                repository.updateDraftFields(
                    currentUserId,
                    draft.id,
                    mapOf(
                        "status" to MessageStatus.SENT.name,
                        "apiConfirmed" to true,
                        "externalMessageId" to sendRes.messageId,
                        "errorMessage" to ""
                    )
                )
                repository.updateLeadFields(
                    currentUserId,
                    lead.id,
                    mapOf(
                        "instagramStatus" to MessageStatus.SENT.name,
                        "lastContactChannel" to OutreachChannel.INSTAGRAM.name,
                        "lastContactAt" to Timestamp.now(),
                        "crmStage" to CrmStage.CONTACTED.name
                    )
                )
                val convId = ensureThreadForLead(lead, OutreachChannel.INSTAGRAM, draft.generatedMessage, "SENT", true)
                if (convId != null) {
                    repository.addMessage(
                        currentUserId,
                        convId,
                        ChatMessage(
                            leadId = lead.id,
                            channel = OutreachChannel.INSTAGRAM.name,
                            direction = "OUTBOUND",
                            body = draft.generatedMessage,
                            senderName = "You",
                            status = "SENT",
                            apiConfirmed = true,
                            externalMessageId = sendRes.messageId
                        )
                    )
                }
                _actionState.value = ActionUiState.Success("Instagram message sent & confirmed (ID: ${sendRes.messageId}).")
            } else {
                repository.updateDraftFields(
                    currentUserId,
                    draft.id,
                    mapOf(
                        "status" to MessageStatus.FAILED.name,
                        "apiConfirmed" to false,
                        "errorMessage" to sendRes.errorMessage
                    )
                )
                repository.updateLeadFields(
                    currentUserId,
                    lead.id,
                    mapOf("instagramStatus" to MessageStatus.FAILED.name)
                )
                _actionState.value = ActionUiState.Error(sendRes.errorMessage)
            }
        }
    }

    fun recordManualOutreach(lead: ShopifyLead, channel: OutreachChannel, notes: String) {
        viewModelScope.launch {
            val updates = mutableMapOf<String, Any>(
                "lastContactChannel" to "MANUAL_${channel.name}",
                "lastContactAt" to Timestamp.now(),
                "manualOutreachNotes" to notes.ifBlank { "Personalized ${channel.label} message copied & opened manually." }
            )
            if (lead.crmStage == CrmStage.DISCOVERED.name ||
                lead.crmStage == CrmStage.AUDITED.name ||
                lead.crmStage == CrmStage.OUTREACH_READY.name
            ) {
                updates["crmStage"] = CrmStage.CONTACTED.name
            }
            repository.updateLeadFields(currentUserId, lead.id, updates)
            ensureThreadForLead(
                lead = lead,
                channel = channel,
                preview = "Manual ${channel.label} outreach logged (not counted as API send)",
                deliveryStatus = "MANUAL_ONLY",
                apiEligible = false
            )
            _actionState.value = ActionUiState.Success("Manual ${channel.label} outreach tracked on CRM timeline.")
        }
    }

    // --- WHATSAPP BUSINESS CONNECT & MESSAGING RULES ---
    fun connectWhatsAppBusiness(
        accessToken: String,
        phoneNumberId: String,
        wabaId: String,
        webhookVerifyToken: String
    ) {
        viewModelScope.launch {
            if (accessToken.isBlank() || phoneNumberId.isBlank()) {
                _actionState.value = ActionUiState.Error("Access Token and Phone Number ID are required.")
                return@launch
            }
            _actionState.value = ActionUiState.Busy("Verifying WhatsApp Business Cloud API credentials...")
            val profile = metaClient.fetchWhatsAppBusinessProfile(
                accessToken = accessToken.trim(),
                phoneNumberId = phoneNumberId.trim(),
                wabaId = wabaId.trim()
            ).getOrElse { err ->
                _actionState.value = ActionUiState.Error(err.localizedMessage ?: "WhatsApp connection failed")
                return@launch
            }

            tokenStore.saveSecret(currentUserId, SecureTokenStore.KEY_WHATSAPP_TOKEN, accessToken.trim())
            if (webhookVerifyToken.isNotBlank()) {
                tokenStore.saveSecret(currentUserId, SecureTokenStore.KEY_WEBHOOK_VERIFY_TOKEN, webhookVerifyToken.trim())
            }

            val integration = ChannelIntegration(
                userId = currentUserId,
                channel = OutreachChannel.WHATSAPP.name,
                connectionStatus = ConnectionStatus.CONNECTED.name,
                accountName = profile.verifiedName,
                accountHandleOrPhone = profile.displayPhoneNumber,
                phoneNumberId = profile.phoneNumberId,
                wabaId = wabaId.trim(),
                permissions = listOf("whatsapp_business_messaging", "whatsapp_business_management"),
                webhookVerified = webhookVerifyToken.isNotBlank(),
                approvedTemplates = profile.approvedTemplates.ifEmpty { listOf("shopify_audit_intro") }
            )
            repository.saveIntegration(currentUserId, "whatsapp", integration)
                .onSuccess {
                    _actionState.value = ActionUiState.Success("Connected WhatsApp Business (${profile.displayPhoneNumber}).")
                }
                .onFailure {
                    _actionState.value = ActionUiState.Error(it.localizedMessage ?: "Failed saving WhatsApp config")
                }
        }
    }

    fun sendWhatsAppApiMessage(lead: ShopifyLead, draft: OutreachDraft, useApprovedTemplate: Boolean) {
        viewModelScope.launch {
            if (lead.whatsappOptedOut) {
                _actionState.value = ActionUiState.Error("Blocked: This contact has opted out of WhatsApp messages.")
                return@launch
            }
            if (!lead.whatsappHasConsent && !lead.whatsappWithinWindow) {
                _actionState.value = ActionUiState.Error(
                    "WhatsApp consent required for business-initiated messages. Use Click-to-Chat (wa.me) or record opt-in consent."
                )
                return@launch
            }
            if (!lead.whatsappWithinWindow && !useApprovedTemplate) {
                _actionState.value = ActionUiState.Error(
                    "Outside 24h customer service window: an approved WhatsApp template is required."
                )
                return@launch
            }

            val token = tokenStore.getSecret(currentUserId, SecureTokenStore.KEY_WHATSAPP_TOKEN)
            val phoneNumberId = dashboardState.value.whatsappIntegration?.phoneNumberId.orEmpty()
            if (token.isBlank() || phoneNumberId.isBlank()) {
                _actionState.value = ActionUiState.Error("WhatsApp Business Cloud API is not connected.")
                return@launch
            }

            _actionState.value = ActionUiState.Busy("Sending via official WhatsApp Cloud API...")
            val sendRes = if (useApprovedTemplate && !lead.whatsappWithinWindow) {
                metaClient.sendWhatsAppTemplateMessage(
                    accessToken = token,
                    phoneNumberId = phoneNumberId,
                    toPhone = lead.whatsappNumber,
                    templateName = draft.templateName.ifBlank { "shopify_audit_intro" },
                    bodyParameter = lead.storeName
                )
            } else {
                metaClient.sendWhatsAppTextMessage(
                    accessToken = token,
                    phoneNumberId = phoneNumberId,
                    toPhone = lead.whatsappNumber,
                    bodyText = draft.generatedMessage
                )
            }

            if (sendRes.success) {
                repository.updateDraftFields(
                    currentUserId,
                    draft.id,
                    mapOf(
                        "status" to MessageStatus.SENT.name,
                        "apiConfirmed" to true,
                        "externalMessageId" to sendRes.messageId,
                        "errorMessage" to ""
                    )
                )
                repository.updateLeadFields(
                    currentUserId,
                    lead.id,
                    mapOf(
                        "whatsappStatus" to MessageStatus.SENT.name,
                        "lastContactChannel" to OutreachChannel.WHATSAPP.name,
                        "lastContactAt" to Timestamp.now(),
                        "crmStage" to CrmStage.CONTACTED.name
                    )
                )
                val convId = ensureThreadForLead(lead, OutreachChannel.WHATSAPP, draft.generatedMessage, "SENT", true)
                if (convId != null) {
                    repository.addMessage(
                        currentUserId,
                        convId,
                        ChatMessage(
                            leadId = lead.id,
                            channel = OutreachChannel.WHATSAPP.name,
                            direction = "OUTBOUND",
                            body = draft.generatedMessage,
                            senderName = "WhatsApp Business",
                            status = "SENT",
                            apiConfirmed = true,
                            externalMessageId = sendRes.messageId
                        )
                    )
                }
                _actionState.value = ActionUiState.Success("WhatsApp message sent & confirmed (ID: ${sendRes.messageId}).")
            } else {
                repository.updateDraftFields(
                    currentUserId,
                    draft.id,
                    mapOf(
                        "status" to MessageStatus.FAILED.name,
                        "apiConfirmed" to false,
                        "errorMessage" to sendRes.errorMessage
                    )
                )
                repository.updateLeadFields(
                    currentUserId,
                    lead.id,
                    mapOf("whatsappStatus" to MessageStatus.FAILED.name)
                )
                _actionState.value = ActionUiState.Error(sendRes.errorMessage)
            }
        }
    }

    fun updateWhatsAppCompliance(leadId: String, hasConsent: Boolean, optedOut: Boolean) {
        viewModelScope.launch {
            val updates = mutableMapOf<String, Any>(
                "whatsappHasConsent" to hasConsent,
                "whatsappOptedOut" to optedOut
            )
            if (optedOut) {
                updates["crmStage"] = CrmStage.OPTED_OUT.name
                updates["whatsappStatus"] = MessageStatus.NOT_ELIGIBLE.name
            }
            repository.updateLeadFields(currentUserId, leadId, updates)
            _actionState.value = ActionUiState.Success(
                if (optedOut) "Opt-out recorded. Messaging blocked for this contact." else "Consent updated."
            )
        }
    }

    fun disconnectChannel(channelId: String) {
        viewModelScope.launch {
            val tokenKey = if (channelId == "instagram") SecureTokenStore.KEY_INSTAGRAM_TOKEN else SecureTokenStore.KEY_WHATSAPP_TOKEN
            tokenStore.clearSecret(currentUserId, tokenKey)
            val existing = if (channelId == "instagram") dashboardState.value.instagramIntegration else dashboardState.value.whatsappIntegration
            val updated = (existing ?: ChannelIntegration(channel = channelId.uppercase()))
                .copy(connectionStatus = ConnectionStatus.DISCONNECTED.name)
            repository.saveIntegration(currentUserId, channelId, updated)
            _actionState.value = ActionUiState.Success("${channelId.replaceFirstChar { it.uppercase() }} disconnected and token cleared.")
        }
    }

    private suspend fun markChannelTokenExpired(channelId: String) {
        val existing = if (channelId == "instagram") dashboardState.value.instagramIntegration else dashboardState.value.whatsappIntegration
        if (existing != null) {
            repository.saveIntegration(
                currentUserId,
                channelId,
                existing.copy(connectionStatus = ConnectionStatus.TOKEN_EXPIRED.name)
            )
        }
    }

    // --- WEBHOOK EVENTS & AI CONVERSATION ASSISTANT ---
    fun processIncomingWebhookJson(rawJson: String) {
        viewModelScope.launch {
            _actionState.value = ActionUiState.Busy("Processing Meta webhook payload...")
            val events = metaClient.parseMetaWebhookPayload(rawJson).getOrElse { err ->
                _actionState.value = ActionUiState.Error("Webhook parse error: ${err.localizedMessage}")
                return@launch
            }
            if (events.isEmpty()) {
                _actionState.value = ActionUiState.Error("No messaging or status events found in webhook JSON.")
                return@launch
            }

            val leads = dashboardState.value.leads
            val threads = dashboardState.value.conversations
            val autoMode = dashboardState.value.userProfile?.aiAssistantMode == AiAssistantMode.AUTHORIZED_AUTO_REPLY.name

            for (evt in events) {
                if (evt.statusUpdate != null && evt.statusMessageId != null) {
                    // Handle WhatsApp delivery / read receipt
                    val matchingThread = threads.firstOrNull { it.channel == evt.channel }
                    if (matchingThread != null) {
                        val mappedStatus = when (evt.statusUpdate) {
                            "DELIVERED" -> "DELIVERED"
                            "READ" -> "READ"
                            "FAILED" -> "FAILED"
                            else -> "SENT"
                        }
                        repository.updateConversationFields(
                            currentUserId,
                            matchingThread.id,
                            mapOf("deliveryStatus" to mappedStatus)
                        )
                    }
                    continue
                }

                // Inbound prospect message
                val matchedLead = leads.firstOrNull {
                    it.instagramUserId == evt.senderIdOrPhone ||
                        it.instagramUsername.equals(evt.senderName, ignoreCase = true) ||
                        it.whatsappNumber.filter(Char::isDigit) == evt.senderIdOrPhone.filter(Char::isDigit)
                } ?: leads.firstOrNull()

                val leadId = matchedLead?.id ?: evt.senderIdOrPhone
                val storeName = matchedLead?.storeName ?: evt.senderName.ifBlank { evt.senderIdOrPhone }

                val existingThread = threads.firstOrNull {
                    it.channel == evt.channel && (it.leadId == leadId || it.recipientHandleOrPhone == evt.senderIdOrPhone)
                }
                val threadId = repository.upsertConversation(
                    currentUserId,
                    (existingThread ?: ConversationThread()).copy(
                        userId = currentUserId,
                        leadId = leadId,
                        storeName = storeName,
                        channel = evt.channel,
                        recipientHandleOrPhone = evt.senderIdOrPhone,
                        isApiEligible = true,
                        withinServiceWindow = true,
                        lastMessagePreview = evt.messageText,
                        lastMessageDirection = "INBOUND",
                        deliveryStatus = "REPLIED",
                        unreadCount = (existingThread?.unreadCount ?: 0L) + 1L,
                        followUpNeeded = true
                    )
                ).getOrNull() ?: continue

                repository.addMessage(
                    currentUserId,
                    threadId,
                    ChatMessage(
                        leadId = leadId,
                        channel = evt.channel,
                        direction = "INBOUND",
                        body = evt.messageText,
                        senderName = storeName,
                        status = "RECEIVED",
                        apiConfirmed = true,
                        externalMessageId = evt.externalMessageId
                    )
                )

                // Run AI Conversation Assistant analysis
                val suggestion = aiService.analyzeReplyAndSuggestResponse(
                    lead = matchedLead,
                    history = emptyList(),
                    latestInboundMessage = evt.messageText
                ).getOrNull()

                if (suggestion != null) {
                    val isInterested = suggestion.detectedIntent == "INTERESTED"
                    val isOptOut = suggestion.detectedIntent == "OPT_OUT"
                    repository.updateConversationFields(
                        currentUserId,
                        threadId,
                        mapOf(
                            "aiSuggestedReply" to suggestion.suggestedReply,
                            "aiSuggestedServices" to suggestion.suggestedServices.take(10),
                            "aiDetectedIntent" to suggestion.detectedIntent,
                            "needsHumanHandoff" to suggestion.needsHumanHandoff
                        )
                    )
                    if (matchedLead != null) {
                        val leadUpdates = mutableMapOf<String, Any>(
                            "prospectIntent" to suggestion.detectedIntent,
                            "needsHumanHandoff" to suggestion.needsHumanHandoff,
                            "followUpNeeded" to !isOptOut,
                            "crmStage" to when {
                                isOptOut -> CrmStage.OPTED_OUT.name
                                isInterested -> CrmStage.INTERESTED.name
                                else -> CrmStage.REPLIED.name
                            }
                        )
                        if (evt.channel == OutreachChannel.WHATSAPP.name) {
                            leadUpdates["whatsappWithinWindow"] = true
                            leadUpdates["whatsappStatus"] = MessageStatus.REPLIED.name
                            if (isOptOut) leadUpdates["whatsappOptedOut"] = true
                        } else {
                            leadUpdates["instagramUserId"] = evt.senderIdOrPhone
                            leadUpdates["instagramApiEligible"] = true
                            leadUpdates["instagramStatus"] = MessageStatus.REPLIED.name
                        }
                        repository.updateLeadFields(currentUserId, matchedLead.id, leadUpdates)
                    }

                    // Authorized Auto-Reply Mode: only send if channel API is connected, permitted window, and NOT opt-out/handoff
                    if (autoMode && !isOptOut && !suggestion.needsHumanHandoff) {
                        val threadSnapshot = ConversationThread(
                            id = threadId,
                            userId = currentUserId,
                            leadId = leadId,
                            storeName = storeName,
                            channel = evt.channel,
                            recipientHandleOrPhone = evt.senderIdOrPhone,
                            isApiEligible = true,
                            withinServiceWindow = true
                        )
                        sendThreadReplyInternal(threadSnapshot, suggestion.suggestedReply, aiGenerated = true)
                    }
                }
            }
            _actionState.value = ActionUiState.Success("Processed ${events.size} webhook event(s) with AI Conversation Assistant.")
        }
    }

    fun generateAiReplyForThread(thread: ConversationThread) {
        viewModelScope.launch {
            val msgs = activeMessages.value
            val latestInbound = msgs.lastOrNull { it.direction == "INBOUND" }?.body
                ?: thread.lastMessagePreview
            if (latestInbound.isBlank()) {
                _actionState.value = ActionUiState.Error("No inbound prospect message to analyze yet.")
                return@launch
            }
            _actionState.value = ActionUiState.Busy("AI Conversation Assistant analyzing reply...")
            val lead = dashboardState.value.leads.firstOrNull { it.id == thread.leadId }
            val suggestion = aiService.analyzeReplyAndSuggestResponse(lead, msgs, latestInbound)
                .getOrElse { err ->
                    _actionState.value = ActionUiState.Error(err.localizedMessage ?: "AI reply failed")
                    return@launch
                }
            repository.updateConversationFields(
                currentUserId,
                thread.id,
                mapOf(
                    "aiSuggestedReply" to suggestion.suggestedReply,
                    "aiSuggestedServices" to suggestion.suggestedServices.take(10),
                    "aiDetectedIntent" to suggestion.detectedIntent,
                    "needsHumanHandoff" to suggestion.needsHumanHandoff
                )
            )
            _actionState.value = ActionUiState.Success("AI reply prepared (${suggestion.detectedIntent}).")
        }
    }

    fun sendThreadReply(thread: ConversationThread, replyText: String, aiGenerated: Boolean) {
        viewModelScope.launch {
            if (replyText.isBlank()) return@launch
            _actionState.value = ActionUiState.Busy("Sending reply via official ${thread.channel} API...")
            val res = sendThreadReplyInternal(thread, replyText, aiGenerated)
            if (res.isSuccess) {
                _actionState.value = ActionUiState.Success("Reply confirmed by official Meta API.")
            } else {
                _actionState.value = ActionUiState.Error(res.exceptionOrNull()?.localizedMessage ?: "Send failed")
            }
        }
    }

    private suspend fun sendThreadReplyInternal(
        thread: ConversationThread,
        replyText: String,
        aiGenerated: Boolean
    ): Result<Unit> {
        val lead = dashboardState.value.leads.firstOrNull { it.id == thread.leadId }
        if (thread.channel == OutreachChannel.WHATSAPP.name && lead?.whatsappOptedOut == true) {
            return Result.failure(Exception("Cannot send: contact has opted out."))
        }
        val sendRes = if (thread.channel == OutreachChannel.INSTAGRAM.name) {
            val token = tokenStore.getSecret(currentUserId, SecureTokenStore.KEY_INSTAGRAM_TOKEN)
            val igId = dashboardState.value.instagramIntegration?.businessAccountId.orEmpty()
            metaClient.sendInstagramMessage(token, igId, thread.recipientHandleOrPhone, replyText)
        } else {
            val token = tokenStore.getSecret(currentUserId, SecureTokenStore.KEY_WHATSAPP_TOKEN)
            val phoneId = dashboardState.value.whatsappIntegration?.phoneNumberId.orEmpty()
            metaClient.sendWhatsAppTextMessage(token, phoneId, thread.recipientHandleOrPhone, replyText)
        }

        return if (sendRes.success) {
            repository.addMessage(
                currentUserId,
                thread.id,
                ChatMessage(
                    leadId = thread.leadId,
                    channel = thread.channel,
                    direction = "OUTBOUND",
                    body = replyText,
                    senderName = if (aiGenerated) "AI Assistant (Approved)" else "You",
                    status = "SENT",
                    apiConfirmed = true,
                    externalMessageId = sendRes.messageId,
                    aiGenerated = aiGenerated
                )
            )
            repository.updateConversationFields(
                currentUserId,
                thread.id,
                mapOf(
                    "lastMessagePreview" to replyText.take(1000),
                    "lastMessageDirection" to "OUTBOUND",
                    "deliveryStatus" to "SENT",
                    "unreadCount" to 0L
                )
            )
            Result.success(Unit)
        } else {
            repository.addMessage(
                currentUserId,
                thread.id,
                ChatMessage(
                    leadId = thread.leadId,
                    channel = thread.channel,
                    direction = "OUTBOUND",
                    body = replyText,
                    senderName = "Failed Attempt",
                    status = "FAILED",
                    apiConfirmed = false,
                    aiGenerated = aiGenerated
                )
            )
            Result.failure(Exception(sendRes.errorMessage))
        }
    }

    fun toggleHumanHandoff(thread: ConversationThread) {
        viewModelScope.launch {
            val next = !thread.needsHumanHandoff
            repository.updateConversationFields(
                currentUserId,
                thread.id,
                mapOf("needsHumanHandoff" to next)
            )
            if (thread.leadId.isNotBlank()) {
                repository.updateLeadFields(currentUserId, thread.leadId, mapOf("needsHumanHandoff" to next))
            }
        }
    }

    fun updateAiAssistantMode(mode: AiAssistantMode) {
        viewModelScope.launch {
            repository.updateAiAssistantMode(currentUserId, mode.name)
            _actionState.value = ActionUiState.Success("AI Assistant mode set to ${mode.label}.")
        }
    }

    fun updateLeadCrmStage(leadId: String, stage: CrmStage) {
        viewModelScope.launch {
            repository.updateLeadFields(currentUserId, leadId, mapOf("crmStage" to stage.name))
            _actionState.value = ActionUiState.Success("CRM stage updated to ${stage.label}.")
        }
    }

    private suspend fun ensureThreadForLead(
        lead: ShopifyLead,
        channel: OutreachChannel,
        preview: String,
        deliveryStatus: String,
        apiEligible: Boolean
    ): String? {
        val existing = dashboardState.value.conversations.firstOrNull {
            it.leadId == lead.id && it.channel == channel.name
        }
        val recipient = if (channel == OutreachChannel.INSTAGRAM) {
            lead.instagramUserId.ifBlank { "@${lead.instagramUsername}" }
        } else {
            lead.whatsappNumber
        }
        return repository.upsertConversation(
            currentUserId,
            (existing ?: ConversationThread()).copy(
                userId = currentUserId,
                leadId = lead.id,
                storeName = lead.storeName,
                channel = channel.name,
                recipientHandleOrPhone = recipient,
                isApiEligible = apiEligible,
                withinServiceWindow = if (channel == OutreachChannel.WHATSAPP) lead.whatsappWithinWindow else apiEligible,
                lastMessagePreview = preview.take(1000),
                lastMessageDirection = "OUTBOUND",
                deliveryStatus = deliveryStatus
            )
        ).getOrNull()
    }

    private fun computeAnalytics(
        leads: List<ShopifyLead>,
        drafts: List<OutreachDraft>,
        conversations: List<ConversationThread>
    ): OutreachAnalytics {
        // Strictly never count drafts or manual chat links as API-sent messages
        val sentDrafts = drafts.filter { it.apiConfirmed }
        val igDrafts = drafts.filter { it.channel == OutreachChannel.INSTAGRAM.name }
        val waDrafts = drafts.filter { it.channel == OutreachChannel.WHATSAPP.name }

        val igConvs = conversations.filter { it.channel == OutreachChannel.INSTAGRAM.name }
        val waConvs = conversations.filter { it.channel == OutreachChannel.WHATSAPP.name }

        val igSent = igDrafts.count { it.apiConfirmed }
        val waSent = waDrafts.count { it.apiConfirmed }

        val igDelivered = igDrafts.count { it.apiConfirmed && it.status in listOf("DELIVERED", "REPLIED") }
        val waDelivered = waDrafts.count { it.apiConfirmed && it.status in listOf("DELIVERED", "READ", "REPLIED") }

        val igReplies = igConvs.count { it.deliveryStatus == "REPLIED" || it.lastMessageDirection == "INBOUND" }
        val waReplies = waConvs.count { it.deliveryStatus == "REPLIED" || it.lastMessageDirection == "INBOUND" }

        return OutreachAnalytics(
            totalDrafts = drafts.size,
            eligibleConversations = conversations.count { it.isApiEligible },
            messagesSent = sentDrafts.size,
            messagesDelivered = igDelivered + waDelivered,
            repliesReceived = igReplies + waReplies,
            interestedProspects = leads.count {
                it.prospectIntent == "INTERESTED" || it.crmStage == CrmStage.INTERESTED.name
            },
            qualifiedLeads = leads.count { it.crmStage == CrmStage.QUALIFIED.name },
            convertedClients = leads.count { it.crmStage == CrmStage.CONVERTED.name },
            instagram = ChannelMetrics(
                totalDrafts = igDrafts.size,
                eligibleConversations = igConvs.count { it.isApiEligible },
                messagesSent = igSent,
                messagesDelivered = igDelivered,
                repliesReceived = igReplies
            ),
            whatsapp = ChannelMetrics(
                totalDrafts = waDrafts.size,
                eligibleConversations = waConvs.count { it.isApiEligible },
                messagesSent = waSent,
                messagesDelivered = waDelivered,
                repliesReceived = waReplies
            )
        )
    }

    private companion object {
        const val TAG = "LeadFinderVM"
    }
}
