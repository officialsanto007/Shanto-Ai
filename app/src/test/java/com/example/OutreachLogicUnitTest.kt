package com.example

import com.example.data.remote.MetaGraphApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class OutreachLogicUnitTest {

    @Test
    fun parseMetaWebhookPayload_whatsappInboundAndStatus_parsesCorrectly() {
        val client = MetaGraphApiClient()
        val waJson = """
            {
              "object": "whatsapp_business_account",
              "entry": [{
                "changes": [{
                  "value": {
                    "contacts": [{"profile": {"name": "Coastal Goods"}}],
                    "messages": [{
                      "from": "15559876543",
                      "id": "wamid.123",
                      "text": {"body": "Yes, please send the audit!"}
                    }],
                    "statuses": [{
                      "id": "wamid.100",
                      "recipient_id": "15559876543",
                      "status": "delivered"
                    }]
                  }
                }]
              }]
            }
        """.trimIndent()

        val events = client.parseMetaWebhookPayload(waJson).getOrThrow()
        assertEquals(2, events.size)
        assertEquals("WHATSAPP", events[0].channel)
        assertEquals("Coastal Goods", events[0].senderName)
        assertEquals("Yes, please send the audit!", events[0].messageText)
        assertEquals("DELIVERED", events[1].statusUpdate)
    }

    @Test
    fun parseMetaWebhookPayload_instagramInbound_parsesCorrectly() {
        val client = MetaGraphApiClient()
        val igJson = """
            {
              "object": "instagram",
              "entry": [{
                "messaging": [{
                  "sender": {"id": "igsid_9988"},
                  "message": {"mid": "m_ig_1", "text": "Hey! How much for a homepage redesign?"}
                }]
              }]
            }
        """.trimIndent()

        val events = client.parseMetaWebhookPayload(igJson).getOrThrow()
        assertEquals(1, events.size)
        assertEquals("INSTAGRAM", events[0].channel)
        assertEquals("igsid_9988", events[0].senderIdOrPhone)
        assertTrue(events[0].messageText.contains("homepage redesign"))
    }
}
