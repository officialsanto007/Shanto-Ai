package com.example.data.ai

import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.MessageLength
import com.example.data.model.MessageTone
import com.example.data.model.OutreachChannel
import com.example.data.model.OutreachDraft
import com.example.data.model.ShopifyLead
import com.example.data.remote.ShopifyScanResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class AiWebsiteAuditResult(
    val auditScore: Long,
    val auditSummary: String,
    val opportunities: List<String>,
    val recommendedImprovements: List<String>,
    val greeting: String,
    val serviceIntroduction: String,
    val callToAction: String,
    val instagramMessage: String,
    val whatsappMessage: String
)

data class AiReplySuggestion(
    val detectedIntent: String,
    val needsHumanHandoff: Boolean,
    val suggestedServices: List<String>,
    val suggestedReply: String
)

class FirebaseAiOutreachService {

    private val auditSchema = Schema.obj(
        properties = mapOf(
            "auditScore" to Schema.integer(description = "Overall UX & conversion readiness score from 0 to 100"),
            "auditSummary" to Schema.string(description = "Concise 2-sentence website audit summary based strictly on detected signals"),
            "opportunities" to Schema.array(
                items = Schema.string(),
                description = "3 to 5 genuine website improvement opportunities grounded in the detected signals"
            ),
            "recommendedImprovements" to Schema.array(
                items = Schema.string(),
                description = "3 to 5 concrete Shopify design/CRO improvements matching the opportunities"
            ),
            "greeting" to Schema.string(description = "Natural personalized greeting"),
            "serviceIntroduction" to Schema.string(description = "Brief Shopify design & development service intro"),
            "callToAction" to Schema.string(description = "Friendly, non-pushy call to action offering a free website audit"),
            "instagramMessage" to Schema.string(description = "Complete personalized Instagram DM mentioning 3-4 specific audit opportunities"),
            "whatsappMessage" to Schema.string(description = "Complete personalized WhatsApp Business outreach message based on the same audit")
        )
    )

    private val replySchema = Schema.obj(
        properties = mapOf(
            "detectedIntent" to Schema.enumeration(
                values = listOf("INTERESTED", "QUESTION", "OBJECTION", "OPT_OUT", "NEUTRAL"),
                description = "Prospect intent classification"
            ),
            "needsHumanHandoff" to Schema.boolean(description = "True if prospect asks for pricing/call/custom scope or requests human attention"),
            "suggestedServices" to Schema.array(
                items = Schema.string(),
                description = "2 to 3 relevant Shopify services based on conversation and audit"
            ),
            "suggestedReply" to Schema.string(description = "Natural, concise, helpful reply ready for human approval")
        )
    )

    private val auditModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = auditSchema
            }
        )
    }

    private val customizerModel by lazy {
        Firebase.ai.generativeModel(modelName = MODEL_NAME)
    }

    private val replyModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = replySchema
            }
        )
    }

    suspend fun generateWebsiteAuditAndOutreach(
        lead: ShopifyLead,
        scan: ShopifyScanResult
    ): Result<AiWebsiteAuditResult> {
        return try {
            val prompt = buildString {
                appendLine("You are an expert Shopify store UX, Conversion Rate Optimization (CRO), and e-commerce design strategist.")
                appendLine("Analyze the target Shopify store using ONLY the factual signals extracted below.")
                appendLine("CRITICAL RULE: Never invent or claim a website problem exists unless supported by the detected signals below. If a feature is already detected, do not claim it is missing.")
                appendLine()
                appendLine("Store Name: ${lead.storeName}")
                appendLine("Website URL: ${lead.websiteUrl}")
                appendLine("Niche: ${lead.niche.ifBlank { "E-commerce Retail" }}")
                appendLine("Location: ${lead.location}")
                appendLine("Detected Website Signals:")
                scan.structuralSignals.forEach { appendLine("- $it") }
                appendLine()
                appendLine("Evaluate across: Homepage design, Hero section, Header & navigation, Product cards, Product images, Product page layout, Collection pages, Mobile responsiveness, Brand identity, Typography, Color combinations, Structure, UX, Conversion optimization, and Missing professional features.")
                appendLine("Identify 3 to 5 genuine improvement opportunities grounded in the signals above.")
                appendLine("Then generate:")
                appendLine("1) A personalized Instagram DM that addresses the business naturally, mentions specific findings, explains how improvements elevate professionalism, briefly introduces Shopify design & development services, offers a free website audit, and includes a natural CTA.")
                appendLine("2) A personalized WhatsApp message built from the same audit.")
                appendLine("Keep both messages warm, concise, human-like, and never aggressive or spammy.")
            }

            val response = withTimeout(AI_TIMEOUT_MS) {
                auditModel.generateContent(prompt)
            }
            val rawJson = response.text ?: error("Gemini returned an empty audit response.")
            val json = JSONObject(rawJson)

            val oppArr = json.getJSONArray("opportunities")
            val opportunities = (0 until oppArr.length()).map { oppArr.getString(it) }
            val recArr = json.getJSONArray("recommendedImprovements")
            val recommendations = (0 until recArr.length()).map { recArr.getString(it) }

            Result.success(
                AiWebsiteAuditResult(
                    auditScore = json.optLong("auditScore", 65L).coerceIn(0L, 100L),
                    auditSummary = json.getString("auditSummary"),
                    opportunities = opportunities,
                    recommendedImprovements = recommendations,
                    greeting = json.getString("greeting"),
                    serviceIntroduction = json.getString("serviceIntroduction"),
                    callToAction = json.getString("callToAction"),
                    instagramMessage = json.getString("instagramMessage"),
                    whatsappMessage = json.getString("whatsappMessage")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Website audit timed out", e)
            Result.failure(Exception("AI audit timed out after 60s. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Website audit failed", e)
            Result.failure(e)
        }
    }

    suspend fun regenerateCustomMessage(draft: OutreachDraft): Result<String> {
        return try {
            val toneEnum = runCatching { MessageTone.valueOf(draft.tone) }.getOrDefault(MessageTone.FRIENDLY)
            val lengthEnum = runCatching { MessageLength.valueOf(draft.messageLength) }.getOrDefault(MessageLength.MEDIUM)
            val channelEnum = runCatching { OutreachChannel.valueOf(draft.channel) }.getOrDefault(OutreachChannel.INSTAGRAM)

            val prompt = buildString {
                appendLine("Rewrite a personalized ${channelEnum.label} outreach message for a Shopify store owner.")
                appendLine("Tone: ${toneEnum.label}")
                appendLine("Length target: ${lengthEnum.label} (${when (lengthEnum) {
                    MessageLength.SHORT -> "2-4 concise sentences"
                    MessageLength.MEDIUM -> "4-7 well-spaced sentences with clean bullet points"
                    MessageLength.DETAILED -> "structured proposal with clear bulleted improvements"
                }})")
                appendLine("Greeting: ${draft.greeting}")
                appendLine("Business Name: ${draft.businessName}")
                appendLine("Website Name: ${draft.websiteName}")
                appendLine("Detected Problems: ${draft.websiteProblems.joinToString("; ")}")
                appendLine("Recommended Improvements: ${draft.recommendedImprovements.joinToString("; ")}")
                appendLine("Service Introduction: ${draft.serviceIntroduction}")
                appendLine("Call to Action: ${draft.callToAction}")
                appendLine("Return ONLY the final ready-to-send message text without quotes or markdown wrappers.")
            }

            val response = withTimeout(AI_TIMEOUT_MS) {
                customizerModel.generateContent(prompt)
            }
            val text = response.text?.trim()
            if (text.isNullOrBlank()) {
                Result.failure(Exception("AI returned an empty regenerated message."))
            } else {
                Result.success(text)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Message regeneration timed out", e)
            Result.failure(Exception("Regeneration timed out after 60s. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Message regeneration failed", e)
            Result.failure(e)
        }
    }

    suspend fun analyzeReplyAndSuggestResponse(
        lead: ShopifyLead?,
        history: List<ChatMessage>,
        latestInboundMessage: String
    ): Result<AiReplySuggestion> {
        return try {
            val prompt = buildString {
                appendLine("You are an AI Conversation Assistant for a Shopify store design & CRO agency.")
                appendLine("Store Name: ${lead?.storeName ?: "Prospect Store"}")
                appendLine("Website: ${lead?.websiteUrl ?: ""}")
                if (lead != null && lead.auditOpportunities.isNotEmpty()) {
                    appendLine("Original Website Audit Opportunities:")
                    lead.auditOpportunities.forEach { appendLine("- $it") }
                }
                appendLine()
                appendLine("Recent Conversation History:")
                history.takeLast(8).forEach { msg ->
                    appendLine("${msg.direction}: ${msg.body}")
                }
                appendLine("Latest Inbound Prospect Message: \"$latestInboundMessage\"")
                appendLine()
                appendLine("Classify the prospect's intent (INTERESTED, QUESTION, OBJECTION, OPT_OUT, or NEUTRAL).")
                appendLine("If they say STOP / unsubscribe / not interested / opt out, classify OPT_OUT and craft a polite opt-out confirmation.")
                appendLine("Otherwise suggest 2-3 relevant Shopify services and write a warm, concise reply.")
            }

            val response = withTimeout(AI_TIMEOUT_MS) {
                replyModel.generateContent(prompt)
            }
            val rawJson = response.text ?: error("Empty AI conversation reply.")
            val json = JSONObject(rawJson)
            val servicesArr = json.getJSONArray("suggestedServices")
            val services = (0 until servicesArr.length()).map { servicesArr.getString(it) }

            Result.success(
                AiReplySuggestion(
                    detectedIntent = json.getString("detectedIntent"),
                    needsHumanHandoff = json.getBoolean("needsHumanHandoff"),
                    suggestedServices = services,
                    suggestedReply = json.getString("suggestedReply")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Reply assistant timed out", e)
            Result.failure(Exception("AI assistant timed out after 60s."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Reply assistant failed", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "FirebaseAiOutreach"
        const val MODEL_NAME = "gemini-3.8-flash"
        const val AI_TIMEOUT_MS = 60_000L
    }
}
