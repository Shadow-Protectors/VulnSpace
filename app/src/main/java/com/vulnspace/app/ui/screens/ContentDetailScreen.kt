package com.vulnspace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vulnspace.app.domain.model.Content
import com.vulnspace.app.ui.components.*
import com.vulnspace.app.ui.theme.*

@Composable
fun ContentDetailScreen(
    content: Content?,
    isLoading: Boolean,
    isBookmarked: Boolean,
    onBookmark: () -> Unit,
    onReport: () -> Unit,
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var showOpenLinkDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column {
            CyberTopBar(
                title = if (content?.contentType == "EVENT") "Event Details" else "Resource Details",
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onBookmark) {
                        Icon(
                            if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isBookmarked) PrimaryBlue else TextSecondary
                        )
                    }
                    IconButton(onClick = onReport) {
                        Icon(Icons.Filled.Flag, contentDescription = "Report", tint = TextSecondary)
                    }
                }
            )
            HorizontalDivider(color = BlueBorder)

            if (isLoading) {
                LoadingState(modifier = Modifier.fillMaxSize())
                return@Column
            }
            if (content == null) {
                ErrorState("Content not found.", modifier = Modifier.fillMaxSize())
                return@Column
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header card
                CyberCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val category = try { ContentCategory.valueOf(content.category) } catch (_: Exception) { ContentCategory.OTHER_RESOURCE }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CategoryChip(category)
                            SafetyBadge(content.safetyStatus ?: "UNKNOWN")
                        }
                        Text(content.title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        if (content.description != null) {
                            Text(content.description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Event-specific details
                if (content.contentType == "EVENT") {
                    CyberCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DetailRow(Icons.Filled.Business, "Organizer", content.organizer ?: "Unknown")
                            if (content.registrationDeadline != null) {
                                DetailRow(Icons.Filled.Schedule, "Registration Deadline", content.registrationDeadline)
                                if (content.daysLeft != null) DeadlineBadge(content.daysLeft)
                            }
                            if (content.startDate != null) DetailRow(Icons.Filled.CalendarToday, "Start Date", content.startDate)
                            if (content.endDate != null) DetailRow(Icons.Filled.CalendarViewWeek, "End Date", content.endDate)
                            if (content.mode != null) DetailRow(Icons.Filled.Videocam, "Mode", content.mode)
                            if (content.location != null) DetailRow(Icons.Filled.LocationOn, "Location", content.location)
                        }
                    }
                }

                // Source info
                CyberCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (content.sourceDomain != null) {
                            DetailRow(Icons.Filled.Link, "Source", content.sourceDomain)
                        }
                        if (content.tags.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                content.tags.forEach { tag ->
                                    Box(
                                        modifier = Modifier
                                            .background(LightBlue, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text("#$tag", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                                    }
                                }
                            }
                        }
                        if (content.submittedByUsername != null) {
                            DetailRow(Icons.Filled.Person, "Submitted by", "@${content.submittedByUsername}")
                        }
                    }
                }

                // Open link button
                if (content.sourceUrl.isNotBlank()) {
                    PrimaryCyberButton(
                        text = "Open Original Link",
                        onClick = { showOpenLinkDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = Icons.Filled.OpenInBrowser
                    )
                }
            }
        }
    }

    if (showOpenLinkDialog) {
        ConfirmationDialog(
            title = "Open External Link",
            message = "You are about to open:\n${content?.sourceDomain ?: content?.sourceUrl}\n\nThis will open in your browser.",
            confirmText = "Open",
            cancelText = "Cancel",
            isDangerous = false,
            onConfirm = {
                showOpenLinkDialog = false
                content?.sourceUrl?.let { uriHandler.openUri(it) }
            },
            onDismiss = { showOpenLinkDialog = false }
        )
    }
}

@Composable
private fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = label, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, overflow = TextOverflow.Ellipsis)
        }
    }
}
