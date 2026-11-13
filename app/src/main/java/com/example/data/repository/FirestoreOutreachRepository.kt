package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.ChannelIntegration
import com.example.data.model.ChatMessage
import com.example.data.model.ConversationThread
import com.example.data.model.OutreachDraft
import com.example.data.model.ShopifyLead
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreOutreachRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    suspend fun ensureUserProfile(uid: String, displayName: String, email: String): Result<Unit> = runCatching {
        val docRef = userDoc(uid)
        val existing = docRef.get().await()
        if (!existing.exists()) {
            val payload = mapOf(
                "userId" to uid,
                "displayName" to displayName.ifBlank { "Shopify Agency Pro" },
                "email" to email,
                "aiAssistantMode" to "MANUAL_APPROVAL",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid")
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> {
        val docRef = userDoc(uid)
        return docRef.snapshots()
            .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                logFlowError(error, OperationType.GET, docRef.path, uid)
                throw error
            }
    }

    suspend fun updateAiAssistantMode(uid: String, mode: String): Result<Unit> = runCatching {
        val docRef = userDoc(uid)
        docRef.update(
            mapOf(
                "aiAssistantMode" to mode,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
    }

    // --- LEADS ---
    fun observeLeads(uid: String): Flow<List<ShopifyLead>> {
        val path = "users/$uid/leads"
        return userDoc(uid).collection("leads")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ShopifyLead::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt ?: it.createdAt }
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun saveLead(uid: String, lead: ShopifyLead): Result<String> = runCatching {
        val collection = userDoc(uid).collection("leads")
        val docRef = if (lead.id.isNotBlank()) collection.document(lead.id) else collection.document()
        if (lead.id.isNotBlank() && lead.createdAt != null) {
            docRef.set(lead.copy(userId = uid, updatedAt = null)).await()
        } else {
            docRef.set(lead.copy(userId = uid).toCreateMap()).await()
        }
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid/leads")
    }

    suspend fun updateLeadFields(uid: String, leadId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        require(leadId.isNotBlank()) { "Lead ID required" }
        val docRef = userDoc(uid).collection("leads").document(leadId)
        val payload = updates.toMutableMap().apply {
            put("updatedAt", FieldValue.serverTimestamp())
        }
        docRef.update(payload).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/$uid/leads/$leadId")
    }

    suspend fun deleteLead(uid: String, leadId: String): Result<Unit> = runCatching {
        val docRef = userDoc(uid).collection("leads").document(leadId)
        docRef.delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "users/$uid/leads/$leadId")
    }

    // --- OUTREACH DRAFTS ---
    fun observeDrafts(uid: String): Flow<List<OutreachDraft>> {
        val path = "users/$uid/outreachDrafts"
        return userDoc(uid).collection("outreachDrafts")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(OutreachDraft::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt ?: it.createdAt }
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun saveDraft(uid: String, draft: OutreachDraft): Result<String> = runCatching {
        val collection = userDoc(uid).collection("outreachDrafts")
        val docRef = if (draft.id.isNotBlank()) collection.document(draft.id) else collection.document()
        if (draft.id.isNotBlank() && draft.createdAt != null) {
            docRef.set(draft.copy(userId = uid, updatedAt = null)).await()
        } else {
            docRef.set(draft.copy(userId = uid).toCreateMap()).await()
        }
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid/outreachDrafts")
    }

    suspend fun updateDraftFields(uid: String, draftId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        val docRef = userDoc(uid).collection("outreachDrafts").document(draftId)
        val payload = updates.toMutableMap().apply {
            put("updatedAt", FieldValue.serverTimestamp())
        }
        docRef.update(payload).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/$uid/outreachDrafts/$draftId")
    }

    // --- CONVERSATIONS & MESSAGES ---
    fun observeConversations(uid: String): Flow<List<ConversationThread>> {
        val path = "users/$uid/conversations"
        return userDoc(uid).collection("conversations")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ConversationThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt ?: it.createdAt }
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    fun observeMessages(uid: String, conversationId: String): Flow<List<ChatMessage>> {
        val path = "users/$uid/conversations/$conversationId/messages"
        return userDoc(uid).collection("conversations").document(conversationId)
            .collection("messages")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt }
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun upsertConversation(uid: String, thread: ConversationThread): Result<String> = runCatching {
        val collection = userDoc(uid).collection("conversations")
        val docRef = if (thread.id.isNotBlank()) collection.document(thread.id) else collection.document()
        if (thread.id.isNotBlank() && thread.createdAt != null) {
            docRef.set(thread.copy(userId = uid, updatedAt = null)).await()
        } else {
            docRef.set(thread.copy(userId = uid).toCreateMap()).await()
        }
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid/conversations")
    }

    suspend fun updateConversationFields(
        uid: String,
        conversationId: String,
        updates: Map<String, Any>
    ): Result<Unit> = runCatching {
        val docRef = userDoc(uid).collection("conversations").document(conversationId)
        val payload = updates.toMutableMap().apply {
            put("updatedAt", FieldValue.serverTimestamp())
        }
        docRef.update(payload).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/$uid/conversations/$conversationId")
    }

    suspend fun addMessage(uid: String, conversationId: String, message: ChatMessage): Result<String> = runCatching {
        val msgRef = userDoc(uid).collection("conversations")
            .document(conversationId)
            .collection("messages")
            .document()
        msgRef.set(message.copy(userId = uid, conversationId = conversationId).toCreateMap()).await()
        msgRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "users/$uid/conversations/$conversationId/messages")
    }

    // --- CHANNEL INTEGRATIONS ---
    fun observeIntegrations(uid: String): Flow<List<ChannelIntegration>> {
        val path = "users/$uid/integrations"
        return userDoc(uid).collection("integrations")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ChannelIntegration::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun saveIntegration(uid: String, channelId: String, integration: ChannelIntegration): Result<Unit> = runCatching {
        val docRef = userDoc(uid).collection("integrations").document(channelId)
        val existing = docRef.get().await()
        if (existing.exists()) {
            val updateMap = mapOf(
                "connectionStatus" to integration.connectionStatus,
                "accountName" to integration.accountName,
                "accountHandleOrPhone" to integration.accountHandleOrPhone,
                "profileImageUrl" to integration.profileImageUrl,
                "businessAccountId" to integration.businessAccountId,
                "phoneNumberId" to integration.phoneNumberId,
                "wabaId" to integration.wabaId,
                "permissions" to integration.permissions.take(15),
                "webhookVerified" to integration.webhookVerified,
                "approvedTemplates" to integration.approvedTemplates.take(25),
                "tokenExpiresAtMillis" to integration.tokenExpiresAtMillis,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(updateMap, SetOptions.merge()).await()
        } else {
            docRef.set(integration.copy(userId = uid).toCreateMap()).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid/integrations/$channelId")
    }

    private fun logFlowError(error: Throwable, op: OperationType, path: String, uid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        val currentUid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        if (exceptionToLog != null && (currentUid == null || currentUid == uid)) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }
}
