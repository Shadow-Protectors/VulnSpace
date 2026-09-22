package com.vulnspace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vulnspace.app.domain.model.AppNotification
import com.vulnspace.app.ui.components.*
import com.vulnspace.app.ui.theme.*

@Composable
fun NotificationsScreen(
    notifications: List<AppNotification>,
    isLoading: Boolean,
    unreadCount: Int = 0,
    errorMessage: String? = null,
    onRefresh: () -> Unit = {},
    onMarkRead: (String) -> Unit = {},
    onMarkAllRead: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column {
            CyberTopBar(
                title = "Alerts",
                subtitle = if (unreadCount > 0) "$unreadCount unread" else "All caught up",
                actions = {
                    if (unreadCount > 0) {
                        TextButton(onClick = onMarkAllRead) {
                            Text("Mark all read", color = PrimaryBlue, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TextPrimary)
                    }
                }
            )
            HorizontalDivider(color = BlueBorder)

            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.1f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(errorMessage, style = MaterialTheme.typography.bodyMedium, color = DangerRed)
                        Spacer(Modifier.height(8.dp))
                        PrimaryCyberButton(
                            text = "Refresh",
                            onClick = onRefresh,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (isLoading) {
                LoadingState(modifier = Modifier.fillMaxSize())
                return@Column
            }

            if (notifications.isEmpty() && errorMessage == null) {
                EmptyState(
                    icon = Icons.Filled.Notifications,
                    title = "No alerts yet",
                    message = "New community updates, approvals, and announcements will appear here.",
                    modifier = Modifier.fillMaxSize()
                )
                return@Column
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationRow(
                        notification = notification,
                        onItemClick = {
                            if (!notification.isRead) {
                                onMarkRead(notification.id)
                            }
                        }
                    )
                    HorizontalDivider(color = BlueBorder.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: AppNotification,
    onItemClick: () -> Unit
) {
    val icon = getNotificationIcon(notification.type)
    val isUnread = !notification.isRead

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick)
            .background(if (isUnread) LightBlue.copy(alpha = 0.4f) else WhiteSurface)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(LightBlue, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isUnread) PrimaryBlue else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = notification.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = notification.body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUnread) TextPrimary else TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatNotificationTime(notification.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = TextDisabled
            )
        }
        if (isUnread) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(PrimaryBlue, RoundedCornerShape(4.dp))
                    .align(Alignment.CenterVertically)
            )
        }
    }
}

private fun getNotificationIcon(type: String?): ImageVector {
    return when (type?.uppercase()) {
        "HEAD_APPLICATION_APPROVED" -> Icons.Filled.CheckCircle
        "HEAD_APPLICATION_REJECTED" -> Icons.Filled.Cancel
        "NEW_HEAD_APPLICATION" -> Icons.Filled.PersonAdd
        "COMMUNITY_CONTENT_CREATED" -> Icons.Filled.Event
        "MEMBER_REMOVED", "MEMBER_SUSPENDED" -> Icons.Filled.Warning
        else -> Icons.Filled.Notifications
    }
}

private fun formatNotificationTime(isoTimestamp: String): String {
    if (isoTimestamp.isBlank()) return ""
    return try {
        val clean = isoTimestamp.replace("Z", "").replace("+00:00", "")
        clean.replace("T", " ").take(16)
    } catch (_: Exception) {
        isoTimestamp
    }
}
