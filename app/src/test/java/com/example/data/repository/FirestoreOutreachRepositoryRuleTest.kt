package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ChannelIntegration
import com.example.data.model.ChatMessage
import com.example.data.model.ConnectionStatus
import com.example.data.model.ConversationThread
import com.example.data.model.CrmStage
import com.example.data.model.MessageStatus
import com.example.data.model.OutreachChannel
import com.example.data.model.OutreachDraft
import com.example.data.model.ShopifyLead
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FirestoreOutreachRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveLeadAndDraft_authenticatedOwner_succeedsAndStreams() = runBlocking {
        val uid = signInTestUser("alice@leadfinder.test")
        val repo = FirestoreOutreachRepository(firestore)

        val lead = ShopifyLead(
            storeName = "Pacific Roast Co",
            websiteUrl = "https://pacificroast.example.com",
            niche = "Specialty Coffee",
            location = "Portland, OR, USA",
            instagramUsername = "pacificroastco",
            instagramProfileUrl = "https://instagram.com/pacificroastco",
            whatsappNumber = "+15035550199",
            crmStage = CrmStage.AUDITED.name,
            auditScore = 64L,
            auditOpportunities = listOf("Weak hero value proposition", "Missing trust badges on PDP")
        )
        val leadId = withTimeout(TIMEOUT_MS) { repo.saveLead(uid, lead).getOrThrow() }
        assertTrue(leadId.isNotBlank())

        val emittedLeads = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeLeads(uid).first { list -> list.any { it.id == leadId } }
        }
        assertEquals("Pacific Roast Co", emittedLeads.first { it.id == leadId }.storeName)

        val draft = OutreachDraft(
            leadId = leadId,
            channel = OutreachChannel.INSTAGRAM.name,
            generatedMessage = "Hey! Came across Pacific Roast Co and noticed a couple hero layout ideas.",
            status = MessageStatus.AWAITING_APPROVAL.name,
            apiConfirmed = false
        )
        val draftId = withTimeout(TIMEOUT_MS) { repo.saveDraft(uid, draft).getOrThrow() }
        assertTrue(draftId.isNotBlank())

        val convId = withTimeout(TIMEOUT_MS) {
            repo.upsertConversation(
                uid,
                ConversationThread(
                    leadId = leadId,
                    storeName = "Pacific Roast Co",
                    channel = OutreachChannel.WHATSAPP.name,
                    recipientHandleOrPhone = "+15035550199",
                    deliveryStatus = "SENT"
                )
            ).getOrThrow()
        }

        val msgId = withTimeout(TIMEOUT_MS) {
            repo.addMessage(
                uid,
                convId,
                ChatMessage(
                    leadId = leadId,
                    channel = OutreachChannel.WHATSAPP.name,
                    direction = "OUTBOUND",
                    body = "Hi team!",
                    status = "SENT",
                    apiConfirmed = true
                )
            ).getOrThrow()
        }
        assertTrue(msgId.isNotBlank())

        val integrationResult = withTimeout(TIMEOUT_MS) {
            repo.saveIntegration(
                uid,
                "instagram_${UUID.randomUUID().toString().take(6)}",
                ChannelIntegration(
                    channel = OutreachChannel.INSTAGRAM.name,
                    connectionStatus = ConnectionStatus.CONNECTED.name,
                    accountName = "Shopify Studio",
                    accountHandleOrPhone = "@shopifystudio"
                )
            )
        }
        assertTrue(integrationResult.isSuccess)
    }

    @Test
    fun observeLeads_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_iso@leadfinder.test")
        val aliceRepo = FirestoreOutreachRepository(firestore)
        withTimeout(TIMEOUT_MS) {
            aliceRepo.saveLead(
                aliceUid,
                ShopifyLead(storeName = "Private Brand", websiteUrl = "https://private.example.com")
            ).getOrThrow()
        }

        signInTestUser("bob_iso@leadfinder.test")
        val bobRepo = FirestoreOutreachRepository(firestore)
        val thrown = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) { bobRepo.observeLeads(aliceUid).first() }
        }.exceptionOrNull()

        assertNotNull(thrown)
        val fsEx = thrown!!.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
    }

    @Test
    fun observeLeads_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = FirestoreOutreachRepository(firestore)

        val thrown = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) { repo.observeLeads("some_uid").first() }
        }.exceptionOrNull()

        assertNotNull(thrown)
        val fsEx = thrown!!.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
    }

    private companion object {
        const val TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
