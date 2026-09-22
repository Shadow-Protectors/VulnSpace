package com.vulnspace.app

import com.vulnspace.app.domain.model.AppNotification
import com.vulnspace.app.domain.model.ClaimHeadGrantResponse
import com.vulnspace.app.domain.model.HeadApplicationStatusCheckResponse
import com.vulnspace.app.domain.model.HeadApplicationSubmissionResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadApplicationTrackingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testSubmissionResponseSerialization() {
        val jsonString = """
            {
                "applicationId": "11111111-2222-3333-4444-555555555555",
                "status": "PENDING",
                "trackingToken": "abcdef1234567890abcdef1234567890",
                "expiresAt": "2026-10-22T00:00:00Z",
                "communityName": "CyberSec Club"
            }
        """.trimIndent()

        val response = json.decodeFromString<HeadApplicationSubmissionResponse>(jsonString)
        assertEquals("11111111-2222-3333-4444-555555555555", response.applicationId)
        assertEquals("PENDING", response.status)
        assertEquals("abcdef1234567890abcdef1234567890", response.trackingToken)
        assertEquals("CyberSec Club", response.communityName)
    }

    @Test
    fun testStatusCheckResponseApproved() {
        val jsonString = """
            {
                "status": "APPROVED",
                "applicationId": "11111111-2222-3333-4444-555555555555",
                "communityName": "CyberSec Club",
                "nextAction": "CLAIM_WITH_GOOGLE"
            }
        """.trimIndent()

        val response = json.decodeFromString<HeadApplicationStatusCheckResponse>(jsonString)
        assertEquals("APPROVED", response.status)
        assertEquals("CLAIM_WITH_GOOGLE", response.nextAction)
        assertEquals("CyberSec Club", response.communityName)
        assertNull(response.reason)
    }

    @Test
    fun testStatusCheckResponseRejected() {
        val jsonString = """
            {
                "status": "REJECTED",
                "reason": "Does not meet community guidelines"
            }
        """.trimIndent()

        val response = json.decodeFromString<HeadApplicationStatusCheckResponse>(jsonString)
        assertEquals("REJECTED", response.status)
        assertEquals("Does not meet community guidelines", response.reason)
        assertNull(response.nextAction)
    }

    @Test
    fun testClaimHeadGrantResponse() {
        val jsonString = """
            {
                "claimed": true,
                "already_head": false,
                "community_id": "comm-123",
                "community_name": "CyberSec Club",
                "username": "alice"
            }
        """.trimIndent()

        val response = json.decodeFromString<ClaimHeadGrantResponse>(jsonString)
        assertTrue(response.claimed)
        assertFalse(response.alreadyHead)
        assertEquals("comm-123", response.communityId)
        assertEquals("CyberSec Club", response.communityName)
        assertEquals("alice", response.username)
    }

    @Test
    fun testAppNotificationDeduplication() {
        val notification = AppNotification(
            id = "notif-1",
            userId = "user-123",
            applicationId = "app-123",
            communityId = "comm-123",
            title = "Community Approved & Claimed",
            body = "Your community is ready.",
            type = "HEAD_APPLICATION_APPROVED",
            dedupeKey = "HEAD_APPLICATION_APPROVED:app-123:user-123",
            readAt = null,
            createdAt = "2026-09-22T10:00:00Z"
        )

        assertFalse(notification.isRead)
        assertEquals("HEAD_APPLICATION_APPROVED:app-123:user-123", notification.dedupeKey)

        val readNotification = notification.copy(readAt = "2026-09-22T10:05:00Z")
        assertTrue(readNotification.isRead)
    }
}
