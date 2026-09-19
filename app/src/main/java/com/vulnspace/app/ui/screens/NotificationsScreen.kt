package com.vulnspace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vulnspace.app.domain.model.AppNotification
import com.vulnspace.app.ui.components.*
import com.vulnspace.app.ui.theme.*

@Composable
fun NotificationsScreen(
    notifications: List<AppNotification>,
    isLoading: Boolean
) {
    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column {
            CyberTopBar(title = "Alerts", subtitle = "${notifications.count { !it.isRead }} unread")
            HorizontalDivider(color = BlueBorder)

            if (isLoading) {
                LoadingState(modifier = Modifier.fillMaxSize())
                return@Column
            }
            if (notifications.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Notifications,
                    title = "No alerts yet",
                    message = "New events and community announcements will appear here.",
                    modifier = Modifier.fillMaxSize()
                )
                return@Column
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationRow(notification)
                    HorizontalDivider(color = BlueBorder.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: AppNotification) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (!notification.isRead) LightBlue else WhiteSurface)
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
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(notification.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(notification.body, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(notification.createdAt, style = MaterialTheme.typography.labelSmall, color = TextDisabled)
        }
        if (!notification.isRead) {
            Box(modifier = Modifier.size(8.dp).background(PrimaryBlue, RoundedCornerShape(4.dp)))
        }
    }
}
