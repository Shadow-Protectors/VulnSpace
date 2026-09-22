package com.vulnspace.app

import com.vulnspace.app.domain.model.AppNotification
import com.vulnspace.app.presentation.viewmodel.HeadApplicationTrackingState
import com.vulnspace.app.presentation.viewmodel.NotificationsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsUiStateTest {

    @Test
    fun testUnreadCountCalculation() {
        val notifications = listOf(
            AppNotification(
                id = "1",
                title = "Alert 1",
                body = "Body 1",
                readAt = null
            ),
            AppNotification(
                id = "2",
                title = "Alert 2",
                body = "Body 2",
                readAt = "2026-09-22T10:00:00Z"
            ),
            AppNotification(
                id = "3",
                title = "Alert 3",
                body = "Body 3",
                readAt = null
            )
        )

        val unread = notifications.count { !it.isRead }
        assertEquals(2, unread)

        val state = NotificationsUiState(
            notifications = notifications,
            unreadCount = unread,
            isLoading = false
        )

        assertEquals(2, state.unreadCount)
        assertFalse(state.isLoading)
    }

    @Test
    fun testHeadApplicationTrackingStateTransitions() {
        val pendingState = HeadApplicationTrackingState.Pending(
            communityName = "Test Community",
            applicationId = "app-1"
        )
        assertEquals("Test Community", pendingState.communityName)

        val approvedState = HeadApplicationTrackingState.ApprovedReadyToClaim(
            communityName = "Test Community",
            applicationId = "app-1"
        )
        assertEquals("Test Community", approvedState.communityName)

        val rejectedState = HeadApplicationTrackingState.Rejected(
            reason = "Invalid credentials"
        )
        assertEquals("Invalid credentials", rejectedState.reason)

        val claimedState = HeadApplicationTrackingState.Claimed(
            communityId = "comm-1",
            communityName = "Test Community"
        )
        assertEquals("comm-1", claimedState.communityId)
    }
}
