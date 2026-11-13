package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class OutreachChannel(val label: String) {
    INSTAGRAM("Instagram"),
    WHATSAPP("WhatsApp")
}

enum class CrmStage(val label: String) {
    DISCOVERED("Discovered"),
    AUDITED("Audited"),
    OUTREACH_READY("Outreach Ready"),
    CONTACTED("Contacted"),
    REPLIED("Replied"),
    INTERESTED("Interested"),
    QUALIFIED("Qualified"),
    CONVERTED("Converted"),
    OPTED_OUT("Opted Out")
}

enum class MessageStatus(val label: String) {
    DRAFT("Draft"),
    AWAITING_APPROVAL("Awaiting Approval"),
    ELIGIBLE_TO_SEND("Eligible to Send"),
    SENT("Sent"),
    DELIVERED("Delivered"),
    READ("Read"),
    REPLIED("Replied"),
    FAILED("Failed"),
    NOT_ELIGIBLE("Not Eligible")
}

enum class MessageTone(val label: String) {
    FRIENDLY("Friendly"),
    PROFESSIONAL("Professional"),
    PREMIUM_AGENCY("Premium Agency"),
    CASUAL("Casual"),
    SHORT_DIRECT("Short & Direct")
}

enum class MessageLength(val label: String) {
    SHORT("Short"),
    MEDIUM("Medium"),
    DETAILED("Detailed")
}

enum class ConnectionStatus(val label: String) {
    CONNECTED("Connected"),
    TOKEN_EXPIRED("Token Expired"),
    DISCONNECTED("Disconnected")
}

enum class AiAssistantMode(val label: String) {
    MANUAL_APPROVAL("Manual Approval"),
    AUTHORIZED_AUTO_REPLY("Authorized Auto-Reply")
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val aiAssistantMode: String = AiAssistantMode.MANUAL_APPROVAL.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ShopifyLead(
    @DocumentId val id: String = "",
    val userId: String = "",
    val storeName: String = "",
    val websiteUrl: String = "",
    val niche: String = "",
    val location: String = "USA",
    val publicEmail: String = "",
    val publicPhone: String = "",
    val instagramUsername: String = "",
    val instagramProfileUrl: String = "",
    val instagramUserId: String = "",
    val whatsappNumber: String = "",
    val whatsappBusinessName: String = "",
    val whatsappHasConsent: Boolean = false,
    val whatsappOptedOut: Boolean = false,
    val whatsappWithinWindow: Boolean = false,
    val instagramApiEligible: Boolean = false,
    val crmStage: String = CrmStage.DISCOVERED.name,
    val auditScore: Long = 0L,
    val auditSummary: String = "",
    val auditOpportunities: List<String> = emptyList(),
    val auditSignals: List<String> = emptyList(),
    val instagramStatus: String = MessageStatus.DRAFT.name,
    val whatsappStatus: String = MessageStatus.DRAFT.name,
    val lastContactChannel: String = "",
    val lastContactAt: Timestamp? = null,
    val followUpNeeded: Boolean = false,
    val needsHumanHandoff: Boolean = false,
    val prospectIntent: String = "NONE",
    val manualOutreachNotes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("storeName", storeName)
        put("websiteUrl", websiteUrl)
        put("niche", niche)
        put("location", location)
        put("publicEmail", publicEmail)
        put("publicPhone", publicPhone)
        put("instagramUsername", instagramUsername)
        put("instagramProfileUrl", instagramProfileUrl)
        put("instagramUserId", instagramUserId)
        put("whatsappNumber", whatsappNumber)
        put("whatsappBusinessName", whatsappBusinessName)
        put("whatsappHasConsent", whatsappHasConsent)
        put("whatsappOptedOut", whatsappOptedOut)
        put("whatsappWithinWindow", whatsappWithinWindow)
        put("instagramApiEligible", instagramApiEligible)
        put("crmStage", crmStage)
        put("auditScore", auditScore)
        put("auditSummary", auditSummary)
        put("auditOpportunities", auditOpportunities.take(10))
        put("auditSignals", auditSignals.take(15))
        put("instagramStatus", instagramStatus)
        put("whatsappStatus", whatsappStatus)
        put("lastContactChannel", lastContactChannel)
        if (lastContactAt != null) put("lastContactAt", lastContactAt)
        put("followUpNeeded", followUpNeeded)
        put("needsHumanHandoff", needsHumanHandoff)
        put("prospectIntent", prospectIntent)
        put("manualOutreachNotes", manualOutreachNotes)
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@IgnoreExtraProperties
data class OutreachDraft(
    @DocumentId val id: String = "",
    val userId: String = "",
    val leadId: String = "",
    val channel: String = OutreachChannel.INSTAGRAM.name,
    val greeting: String = "",
    val businessName: String = "",
    val websiteName: String = "",
    val websiteProblems: List<String> = emptyList(),
    val recommendedImprovements: List<String> = emptyList(),
    val serviceIntroduction: String = "",
    val callToAction: String = "",
    val tone: String = MessageTone.FRIENDLY.name,
    val messageLength: String = MessageLength.MEDIUM.name,
    val generatedMessage: String = "",
    val templateName: String = "",
    val status: String = MessageStatus.DRAFT.name,
    val apiConfirmed: Boolean = false,
    val externalMessageId: String = "",
    val errorMessage: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "leadId" to leadId,
        "channel" to channel,
        "greeting" to greeting,
        "businessName" to businessName,
        "websiteName" to websiteName,
        "websiteProblems" to websiteProblems.take(10),
        "recommendedImprovements" to recommendedImprovements.take(10),
        "serviceIntroduction" to serviceIntroduction,
        "callToAction" to callToAction,
        "tone" to tone,
        "messageLength" to messageLength,
        "generatedMessage" to generatedMessage,
        "templateName" to templateName,
        "status" to status,
        "apiConfirmed" to apiConfirmed,
        "externalMessageId" to externalMessageId,
        "errorMessage" to errorMessage,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class ConversationThread(
    @DocumentId val id: String = "",
    val userId: String = "",
    val leadId: String = "",
    val storeName: String = "",
    val channel: String = OutreachChannel.INSTAGRAM.name,
    val recipientHandleOrPhone: String = "",
    val externalConversationId: String = "",
    val isApiEligible: Boolean = false,
    val withinServiceWindow: Boolean = false,
    val lastMessagePreview: String = "",
    val lastMessageDirection: String = "OUTBOUND",
    val deliveryStatus: String = "PENDING",
    val unreadCount: Long = 0L,
    val aiSuggestedReply: String = "",
    val aiSuggestedServices: List<String> = emptyList(),
    val aiDetectedIntent: String = "NONE",
    val needsHumanHandoff: Boolean = false,
    val followUpNeeded: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "leadId" to leadId,
        "storeName" to storeName,
        "channel" to channel,
        "recipientHandleOrPhone" to recipientHandleOrPhone,
        "externalConversationId" to externalConversationId,
        "isApiEligible" to isApiEligible,
        "withinServiceWindow" to withinServiceWindow,
        "lastMessagePreview" to lastMessagePreview.take(1000),
        "lastMessageDirection" to lastMessageDirection,
        "deliveryStatus" to deliveryStatus,
        "unreadCount" to unreadCount,
        "aiSuggestedReply" to aiSuggestedReply,
        "aiSuggestedServices" to aiSuggestedServices.take(10),
        "aiDetectedIntent" to aiDetectedIntent,
        "needsHumanHandoff" to needsHumanHandoff,
        "followUpNeeded" to followUpNeeded,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val leadId: String = "",
    val channel: String = OutreachChannel.INSTAGRAM.name,
    val direction: String = "OUTBOUND",
    val body: String = "",
    val senderName: String = "",
    val status: String = "SENT",
    val apiConfirmed: Boolean = false,
    val externalMessageId: String = "",
    val aiGenerated: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "conversationId" to conversationId,
        "leadId" to leadId,
        "channel" to channel,
        "direction" to direction,
        "body" to body,
        "senderName" to senderName,
        "status" to status,
        "apiConfirmed" to apiConfirmed,
        "externalMessageId" to externalMessageId,
        "aiGenerated" to aiGenerated,
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class ChannelIntegration(
    @DocumentId val id: String = "",
    val userId: String = "",
    val channel: String = OutreachChannel.INSTAGRAM.name,
    val connectionStatus: String = ConnectionStatus.DISCONNECTED.name,
    val accountName: String = "",
    val accountHandleOrPhone: String = "",
    val profileImageUrl: String = "",
    val businessAccountId: String = "",
    val phoneNumberId: String = "",
    val wabaId: String = "",
    val permissions: List<String> = emptyList(),
    val webhookVerified: Boolean = false,
    val approvedTemplates: List<String> = emptyList(),
    val tokenExpiresAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "channel" to channel,
        "connectionStatus" to connectionStatus,
        "accountName" to accountName,
        "accountHandleOrPhone" to accountHandleOrPhone,
        "profileImageUrl" to profileImageUrl,
        "businessAccountId" to businessAccountId,
        "phoneNumberId" to phoneNumberId,
        "wabaId" to wabaId,
        "permissions" to permissions.take(15),
        "webhookVerified" to webhookVerified,
        "approvedTemplates" to approvedTemplates.take(25),
        "tokenExpiresAtMillis" to tokenExpiresAtMillis,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
