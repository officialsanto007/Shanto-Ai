package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class MetaInstagramProfile(
    val igAccountId: String,
    val username: String,
    val name: String,
    val profileImageUrl: String,
    val permissions: List<String>
)

data class MetaConversationItem(
    val conversationId: String,
    val participantId: String,
    val participantUsername: String,
    val messages: List<MetaInboundMessage>
)

data class MetaInboundMessage(
    val messageId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: String
)

data class MetaSendResult(
    val success: Boolean,
    val messageId: String = "",
    val errorMessage: String = ""
)

data class WhatsAppPhoneProfile(
    val phoneNumberId: String,
    val displayPhoneNumber: String,
    val verifiedName: String,
    val approvedTemplates: List<String>
)

data class ParsedWebhookEvent(
    val channel: String,
    val senderIdOrPhone: String,
    val senderName: String,
    val messageText: String,
    val externalMessageId: String,
    val statusUpdate: String? = null,
    val statusMessageId: String? = null
)

class MetaGraphApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchInstagramProfile(accessToken: String): Result<MetaInstagramProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val profileReq = Request.Builder()
                .url("$GRAPH_BASE/me?fields=id,name,username,profile_picture_url&access_token=$accessToken")
                .get()
                .build()

            val profileJson = client.newCall(profileReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                if (!resp.isSuccessful) {
                    val errMsg = json.optJSONObject("error")?.optString("message") ?: "HTTP ${resp.code}"
                    error("Meta Graph API error: $errMsg")
                }
                json
            }

            // Attempt to resolve linked Instagram Business Account via /me/accounts if /me is a Facebook User token
            var igId = profileJson.optString("id")
            var username = profileJson.optString("username")
            var name = profileJson.optString("name")
            var pic = profileJson.optString("profile_picture_url")

            if (username.isBlank()) {
                val pagesReq = Request.Builder()
                    .url("$GRAPH_BASE/me/accounts?fields=name,instagram_business_account{id,username,name,profile_picture_url}&access_token=$accessToken")
                    .get()
                    .build()
                client.newCall(pagesReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val pagesJson = JSONObject(resp.body?.string().orEmpty())
                        val data = pagesJson.optJSONArray("data")
                        if (data != null) {
                            for (i in 0 until data.length()) {
                                val igObj = data.optJSONObject(i)?.optJSONObject("instagram_business_account")
                                if (igObj != null) {
                                    igId = igObj.optString("id", igId)
                                    username = igObj.optString("username", username)
                                    name = igObj.optString("name", name)
                                    pic = igObj.optString("profile_picture_url", pic)
                                    break
                                }
                            }
                        }
                    }
                }
            }

            val perms = fetchGrantedPermissions(accessToken)
            MetaInstagramProfile(
                igAccountId = igId,
                username = username.ifBlank { name.lowercase().replace(" ", "_") },
                name = name.ifBlank { "Instagram Professional" },
                profileImageUrl = pic,
                permissions = perms
            )
        }
    }

    private fun fetchGrantedPermissions(accessToken: String): List<String> {
        return try {
            val req = Request.Builder()
                .url("$GRAPH_BASE/me/permissions?access_token=$accessToken")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val json = JSONObject(resp.body?.string().orEmpty())
                val arr = json.optJSONArray("data") ?: return emptyList()
                (0 until arr.length()).mapNotNull { idx ->
                    val item = arr.optJSONObject(idx)
                    if (item?.optString("status") == "granted") item.optString("permission") else null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchInstagramConversations(
        accessToken: String,
        igAccountId: String
    ): Result<List<MetaConversationItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val target = igAccountId.ifBlank { "me" }
            val url = "$GRAPH_BASE/$target/conversations?platform=instagram&fields=id,participants,messages{id,message,from,created_time}&access_token=$accessToken"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                if (!resp.isSuccessful) {
                    val err = json.optJSONObject("error")?.optString("message") ?: "HTTP ${resp.code}"
                    error("Instagram conversations fetch failed: $err")
                }
                val data = json.optJSONArray("data") ?: JSONArray()
                (0 until data.length()).map { i ->
                    val conv = data.getJSONObject(i)
                    val participants = conv.optJSONObject("participants")?.optJSONArray("data") ?: JSONArray()
                    var partId = ""
                    var partUsername = ""
                    for (p in 0 until participants.length()) {
                        val pObj = participants.getJSONObject(p)
                        val pid = pObj.optString("id")
                        if (pid != igAccountId) {
                            partId = pid
                            partUsername = pObj.optString("username", pObj.optString("name", ""))
                            break
                        }
                    }
                    val msgsArr = conv.optJSONObject("messages")?.optJSONArray("data") ?: JSONArray()
                    val msgs = (0 until msgsArr.length()).map { m ->
                        val mObj = msgsArr.getJSONObject(m)
                        val fromObj = mObj.optJSONObject("from")
                        MetaInboundMessage(
                            messageId = mObj.optString("id"),
                            senderId = fromObj?.optString("id").orEmpty(),
                            senderName = fromObj?.optString("username", fromObj.optString("name", "")).orEmpty(),
                            text = mObj.optString("message"),
                            timestamp = mObj.optString("created_time")
                        )
                    }
                    MetaConversationItem(
                        conversationId = conv.optString("id"),
                        participantId = partId,
                        participantUsername = partUsername,
                        messages = msgs
                    )
                }
            }
        }
    }

    suspend fun sendInstagramMessage(
        accessToken: String,
        igAccountId: String,
        recipientIgsid: String,
        messageText: String
    ): MetaSendResult = withContext(Dispatchers.IO) {
        try {
            if (accessToken.isBlank()) return@withContext MetaSendResult(false, errorMessage = "Instagram access token is not connected.")
            if (recipientIgsid.isBlank()) return@withContext MetaSendResult(false, errorMessage = "Recipient IGSID missing — no eligible API conversation exists.")

            val payload = JSONObject().apply {
                put("recipient", JSONObject().put("id", recipientIgsid))
                put("message", JSONObject().put("text", messageText))
            }
            val target = igAccountId.ifBlank { "me" }
            val req = Request.Builder()
                .url("$GRAPH_BASE/$target/messages?access_token=$accessToken")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                if (resp.isSuccessful) {
                    val msgId = json?.optString("message_id").orEmpty()
                    MetaSendResult(success = true, messageId = msgId)
                } else {
                    val errMsg = json?.optJSONObject("error")?.optString("message")
                        ?: "Meta API rejected message (HTTP ${resp.code})"
                    MetaSendResult(success = false, errorMessage = errMsg)
                }
            }
        } catch (e: Exception) {
            MetaSendResult(success = false, errorMessage = e.localizedMessage ?: "Network error sending Instagram message")
        }
    }

    suspend fun fetchWhatsAppBusinessProfile(
        accessToken: String,
        phoneNumberId: String,
        wabaId: String
    ): Result<WhatsAppPhoneProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val phoneReq = Request.Builder()
                .url("$GRAPH_BASE/$phoneNumberId?fields=display_phone_number,verified_name&access_token=$accessToken")
                .get()
                .build()

            val phoneJson = client.newCall(phoneReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                if (!resp.isSuccessful) {
                    val err = json.optJSONObject("error")?.optString("message") ?: "HTTP ${resp.code}"
                    error("WhatsApp Cloud API error: $err")
                }
                json
            }

            val templates = mutableListOf<String>()
            if (wabaId.isNotBlank()) {
                val tplReq = Request.Builder()
                    .url("$GRAPH_BASE/$wabaId/message_templates?fields=name,status&access_token=$accessToken")
                    .get()
                    .build()
                client.newCall(tplReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val tplJson = JSONObject(resp.body?.string().orEmpty())
                        val data = tplJson.optJSONArray("data")
                        if (data != null) {
                            for (i in 0 until data.length()) {
                                val item = data.optJSONObject(i)
                                if (item?.optString("status") == "APPROVED") {
                                    templates.add(item.optString("name"))
                                }
                            }
                        }
                    }
                }
            }

            WhatsAppPhoneProfile(
                phoneNumberId = phoneNumberId,
                displayPhoneNumber = phoneJson.optString("display_phone_number"),
                verifiedName = phoneJson.optString("verified_name", "WhatsApp Business"),
                approvedTemplates = templates
            )
        }
    }

    suspend fun sendWhatsAppTextMessage(
        accessToken: String,
        phoneNumberId: String,
        toPhone: String,
        bodyText: String
    ): MetaSendResult = withContext(Dispatchers.IO) {
        try {
            if (accessToken.isBlank() || phoneNumberId.isBlank()) {
                return@withContext MetaSendResult(false, errorMessage = "WhatsApp Business Cloud API credentials are not configured.")
            }
            val cleanPhone = toPhone.filter { it.isDigit() }
            if (cleanPhone.length < 10) {
                return@withContext MetaSendResult(false, errorMessage = "Invalid recipient WhatsApp number.")
            }
            val payload = JSONObject().apply {
                put("messaging_product", "whatsapp")
                put("recipient_type", "individual")
                put("to", cleanPhone)
                put("type", "text")
                put("text", JSONObject().put("preview_url", false).put("body", bodyText))
            }
            executeWhatsAppSend(accessToken, phoneNumberId, payload)
        } catch (e: Exception) {
            MetaSendResult(false, errorMessage = e.localizedMessage ?: "Network error sending WhatsApp message")
        }
    }

    suspend fun sendWhatsAppTemplateMessage(
        accessToken: String,
        phoneNumberId: String,
        toPhone: String,
        templateName: String,
        languageCode: String = "en_US",
        bodyParameter: String = ""
    ): MetaSendResult = withContext(Dispatchers.IO) {
        try {
            if (accessToken.isBlank() || phoneNumberId.isBlank()) {
                return@withContext MetaSendResult(false, errorMessage = "WhatsApp Business Cloud API credentials are not configured.")
            }
            val cleanPhone = toPhone.filter { it.isDigit() }
            val templateObj = JSONObject().apply {
                put("name", templateName)
                put("language", JSONObject().put("code", languageCode))
                if (bodyParameter.isNotBlank()) {
                    val param = JSONObject().put("type", "text").put("text", bodyParameter)
                    val comp = JSONObject().put("type", "body").put("parameters", JSONArray().put(param))
                    put("components", JSONArray().put(comp))
                }
            }
            val payload = JSONObject().apply {
                put("messaging_product", "whatsapp")
                put("to", cleanPhone)
                put("type", "template")
                put("template", templateObj)
            }
            executeWhatsAppSend(accessToken, phoneNumberId, payload)
        } catch (e: Exception) {
            MetaSendResult(false, errorMessage = e.localizedMessage ?: "Network error sending WhatsApp template")
        }
    }

    private fun executeWhatsAppSend(
        accessToken: String,
        phoneNumberId: String,
        payload: JSONObject
    ): MetaSendResult {
        val req = Request.Builder()
            .url("$GRAPH_BASE/$phoneNumberId/messages")
            .header("Authorization", "Bearer $accessToken")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        return client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
            if (resp.isSuccessful) {
                val msgId = json?.optJSONArray("messages")?.optJSONObject(0)?.optString("id").orEmpty()
                MetaSendResult(success = true, messageId = msgId)
            } else {
                val errMsg = json?.optJSONObject("error")?.optString("message")
                    ?: "WhatsApp Cloud API rejected message (HTTP ${resp.code})"
                MetaSendResult(success = false, errorMessage = errMsg)
            }
        }
    }

    fun parseMetaWebhookPayload(rawJson: String): Result<List<ParsedWebhookEvent>> = runCatching {
        val root = JSONObject(rawJson)
        val objType = root.optString("object")
        val entries = root.optJSONArray("entry") ?: JSONArray()
        val events = mutableListOf<ParsedWebhookEvent>()

        when (objType) {
            "instagram" -> {
                for (i in 0 until entries.length()) {
                    val messaging = entries.getJSONObject(i).optJSONArray("messaging") ?: continue
                    for (m in 0 until messaging.length()) {
                        val evt = messaging.getJSONObject(m)
                        val senderId = evt.optJSONObject("sender")?.optString("id").orEmpty()
                        val msg = evt.optJSONObject("message")
                        if (msg != null) {
                            events.add(
                                ParsedWebhookEvent(
                                    channel = "INSTAGRAM",
                                    senderIdOrPhone = senderId,
                                    senderName = senderId,
                                    messageText = msg.optString("text"),
                                    externalMessageId = msg.optString("mid")
                                )
                            )
                        }
                    }
                }
            }
            "whatsapp_business_account" -> {
                for (i in 0 until entries.length()) {
                    val changes = entries.getJSONObject(i).optJSONArray("changes") ?: continue
                    for (c in 0 until changes.length()) {
                        val value = changes.getJSONObject(c).optJSONObject("value") ?: continue
                        val contacts = value.optJSONArray("contacts")
                        val contactName = contacts?.optJSONObject(0)?.optJSONObject("profile")?.optString("name").orEmpty()

                        val messages = value.optJSONArray("messages")
                        if (messages != null) {
                            for (m in 0 until messages.length()) {
                                val msg = messages.getJSONObject(m)
                                events.add(
                                    ParsedWebhookEvent(
                                        channel = "WHATSAPP",
                                        senderIdOrPhone = msg.optString("from"),
                                        senderName = contactName.ifBlank { msg.optString("from") },
                                        messageText = msg.optJSONObject("text")?.optString("body").orEmpty(),
                                        externalMessageId = msg.optString("id")
                                    )
                                )
                            }
                        }
                        val statuses = value.optJSONArray("statuses")
                        if (statuses != null) {
                            for (s in 0 until statuses.length()) {
                                val st = statuses.getJSONObject(s)
                                events.add(
                                    ParsedWebhookEvent(
                                        channel = "WHATSAPP",
                                        senderIdOrPhone = st.optString("recipient_id"),
                                        senderName = "",
                                        messageText = "",
                                        externalMessageId = st.optString("id"),
                                        statusUpdate = st.optString("status").uppercase(),
                                        statusMessageId = st.optString("id")
                                    )
                                )
                            }
                        }
                    }
                }
            }
            else -> error("Unsupported Meta webhook object type: '$objType'")
        }
        events
    }

    companion object {
        private const val GRAPH_BASE = "https://graph.facebook.com/v21.0"
    }
}
