package com.vulnspace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vulnspace.app.domain.model.AuditLog
import com.vulnspace.app.domain.model.Community
import com.vulnspace.app.domain.model.HeadApplication
import com.vulnspace.app.ui.components.*
import com.vulnspace.app.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// Admin Dashboard
// ─────────────────────────────────────────────────────────────────────────────

data class AdminStats(
    val pendingApplications: Int = 0,
    val activeCommunities: Int = 0,
    val activeHeads: Int = 0,
    val totalMembers: Int = 0
)

/**
 * A durable summary of a successful head-application approval. Keeping this in
 * UI state means the admin gets a clear next step even after the approved row
 * disappears from the pending queue.
 */
data class ApprovalResult(
    val communityName: String,
    val applicantName: String,
    val emailStatus: String = "NOT_SENT",
    val notificationStatus: String = "IN_APP_PENDING",
    val oneTimePassword: String? = null
) {
    val emailWasDelivered: Boolean get() = emailStatus == "SENT"
    val inAppNotificationWasCreated: Boolean get() = notificationStatus == "IN_APP_CREATED"
}

sealed interface DashboardState {
    data object Loading : DashboardState
    data class Loaded(val stats: AdminStats) : DashboardState
    data object Empty : DashboardState
    data class Error(val message: String) : DashboardState
}

@Composable
fun AdminDashboardScreen(
    state: DashboardState,
    onRefresh: () -> Unit = {},
    onApplicationsClick: () -> Unit,
    onCommunitiesClick: () -> Unit,
    onAuditLogsClick: () -> Unit,
    onSignOut: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column {
            CyberTopBar(
                title = "Admin Dashboard",
                subtitle = "Platform Administration",
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TextPrimary)
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out", tint = DangerRed)
                    }
                }
            )
            HorizontalDivider(color = BlueBorder)

            when (state) {
                is DashboardState.Loading -> {
                    LoadingState(modifier = Modifier.fillMaxSize())
                }
                is DashboardState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.08f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = DangerRed, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "Unable to load dashboard statistics.",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = DangerRed
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = state.message.ifBlank { "Please check your network and privileges, then try again." },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(Modifier.height(16.dp))
                                PrimaryCyberButton(
                                    text = "Retry",
                                    onClick = onRefresh,
                                    leadingIcon = Icons.Filled.Refresh
                                )
                            }
                        }
                    }
                }
                is DashboardState.Empty -> {
                    val emptyStats = AdminStats()
                    DashboardContent(
                        stats = emptyStats,
                        onApplicationsClick = onApplicationsClick,
                        onCommunitiesClick = onCommunitiesClick,
                        onAuditLogsClick = onAuditLogsClick,
                        onSignOut = onSignOut
                    )
                }
                is DashboardState.Loaded -> {
                    DashboardContent(
                        stats = state.stats,
                        onApplicationsClick = onApplicationsClick,
                        onCommunitiesClick = onCommunitiesClick,
                        onAuditLogsClick = onAuditLogsClick,
                        onSignOut = onSignOut
                    )
                }
            }
        }
    }
}

@Composable
fun AdminDashboardScreen(
    stats: AdminStats,
    isLoading: Boolean,
    errorMessage: String? = null,
    onRefresh: () -> Unit = {},
    onApplicationsClick: () -> Unit,
    onCommunitiesClick: () -> Unit,
    onAuditLogsClick: () -> Unit,
    onSignOut: () -> Unit
) {
    val state = when {
        isLoading -> DashboardState.Loading
        errorMessage != null -> DashboardState.Error(errorMessage)
        stats.pendingApplications == 0 && stats.activeCommunities == 0 && stats.activeHeads == 0 && stats.totalMembers == 0 -> DashboardState.Empty
        else -> DashboardState.Loaded(stats)
    }
    AdminDashboardScreen(
        state = state,
        onRefresh = onRefresh,
        onApplicationsClick = onApplicationsClick,
        onCommunitiesClick = onCommunitiesClick,
        onAuditLogsClick = onAuditLogsClick,
        onSignOut = onSignOut
    )
}

@Composable
private fun DashboardContent(
    stats: AdminStats,
    onApplicationsClick: () -> Unit,
    onCommunitiesClick: () -> Unit,
    onAuditLogsClick: () -> Unit,
    onSignOut: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Stats grid
        item {
            Text("Platform Overview", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    label = "Pending",
                    value = stats.pendingApplications.toString(),
                    icon = Icons.Filled.HourglassEmpty,
                    highlight = stats.pendingApplications > 0,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Communities",
                    value = stats.activeCommunities.toString(),
                    icon = Icons.Filled.Shield,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    label = "Heads",
                    value = stats.activeHeads.toString(),
                    icon = Icons.Filled.AdminPanelSettings,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Members",
                    value = stats.totalMembers.toString(),
                    icon = Icons.Filled.Group,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Quick actions
        item {
            Spacer(Modifier.height(4.dp))
            Text("Actions", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
        }
        item {
            AdminMenuCard(
                icon = Icons.Filled.Assignment,
                title = "Head Applications",
                badge = if (stats.pendingApplications > 0) stats.pendingApplications.toString() else null,
                onClick = onApplicationsClick
            )
        }
        item {
            AdminMenuCard(
                icon = Icons.Filled.Shield,
                title = "Communities",
                onClick = onCommunitiesClick
            )
        }
        item {
            AdminMenuCard(
                icon = Icons.Filled.History,
                title = "Audit Logs",
                onClick = onAuditLogsClick
            )
        }
        item {
            AdminMenuCard(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = "Sign Out",
                onClick = onSignOut
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, highlight: Boolean = false, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (highlight) LightBlue else WhiteSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (highlight) PrimaryBlue else BlueBorder),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = label, tint = if (highlight) PrimaryBlue else TextSecondary, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, color = if (highlight) PrimaryBlue else TextPrimary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

@Composable
private fun AdminMenuCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    CyberCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(44.dp).background(LightBlue, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = title, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (badge != null) {
                Box(modifier = Modifier.background(DangerRed, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(badge, style = MaterialTheme.typography.labelSmall, color = WhiteSurface)
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Head Applications Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HeadApplicationsScreen(
    applications: List<HeadApplication>,
    isLoading: Boolean,
    errorMessage: String? = null,
    actionMessage: String? = null,
    approvalResult: ApprovalResult? = null,
    onRefresh: () -> Unit = {},
    onApprove: (String) -> Unit,
    onReject: (String, String) -> Unit,
    onViewApprovedCommunity: () -> Unit = {},
    onDismissApprovalResult: () -> Unit = {},
    onBack: () -> Unit
) {
    var approveTarget by remember { mutableStateOf<HeadApplication?>(null) }
    var rejectTarget by remember { mutableStateOf<HeadApplication?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column {
            CyberTopBar(
                title = "Head Applications",
                subtitle = "${applications.count { it.status == "PENDING" }} pending",
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Error, contentDescription = null, tint = DangerRed)
                            Spacer(Modifier.width(8.dp))
                            Text("Application Error", style = MaterialTheme.typography.titleSmall, color = DangerRed)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onRefresh) {
                            Text("Retry Query")
                        }
                    }
                }
            }

            approvalResult?.let { result ->
                ApprovalCompletedCard(
                    result = result,
                    onViewCommunity = onViewApprovedCommunity,
                    onDismiss = onDismissApprovalResult
                )
            }

            if (actionMessage != null) {
                val isEmailWarning = actionMessage.contains("email", ignoreCase = true) && actionMessage.contains("failed", ignoreCase = true)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isEmailWarning) WarningBackground else SuccessGreen.copy(alpha = 0.15f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isEmailWarning) WarningAmber else SuccessGreen)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isEmailWarning) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (isEmailWarning) WarningAmber else SuccessGreen
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(actionMessage, style = MaterialTheme.typography.bodyMedium, color = if (isEmailWarning) WarningAmber else SuccessGreen)
                    }
                }
            }

            if (isLoading) { LoadingState(modifier = Modifier.fillMaxSize()); return@Column }
            if (applications.isEmpty() && errorMessage == null) {
                EmptyState(icon = Icons.Filled.List, title = "No applications", message = "No head applications to review.", modifier = Modifier.fillMaxSize())
                return@Column
            }

            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(applications, key = { it.id }) { app ->
                    ApplicationCard(
                        application = app,
                        onApprove = { approveTarget = app },
                        onReject = { rejectTarget = app }
                    )
                }
            }
        }
    }

    approveTarget?.let { app ->
        ConfirmationDialog(
            title = "Approve Community Application",
            message = "Approve ${app.full_name}'s application and create ${app.proposed_community_name}? The Community Head will receive an approval notification and sign-in instructions.",
            confirmText = "Approve & Notify",
            onConfirm = {
                onApprove(app.id)
                approveTarget = null
            },
            onDismiss = { approveTarget = null }
        )
    }

    rejectTarget?.let { app ->
        AlertDialog(
            onDismissRequest = { rejectTarget = null; rejectReason = "" },
            title = { Text("Reject Application") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Provide an optional reason for ${app.full_name}.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        label = { Text("Reason (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onReject(app.id, rejectReason); rejectTarget = null; rejectReason = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) { Text("Reject") }
            },
            dismissButton = { TextButton(onClick = { rejectTarget = null; rejectReason = "" }) { Text("Cancel") } },
            containerColor = WhiteSurface
        )
    }
}

@Composable
private fun ApprovalCompletedCard(
    result: ApprovalResult,
    onViewCommunity: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.12f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Community approved", style = MaterialTheme.typography.titleSmall, color = SuccessGreen)
                    Text(result.communityName, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss approval result", tint = TextSecondary)
                }
            }

            Text(
                "${result.applicantName} is now the Community Head. The community is ready to manage.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )

            val inAppMessage = if (result.inAppNotificationWasCreated) {
                "An in-app approval alert was created for the Community Head."
            } else {
                "The in-app approval alert is pending. Deploy the latest database migration, then refresh."
            }
            Text(inAppMessage, style = MaterialTheme.typography.bodySmall, color = TextSecondary)

            if (result.emailWasDelivered) {
                Text(
                    "Sign-in instructions were emailed to the Community Head.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            } else {
                Text(
                    if (result.oneTimePassword != null) {
                        "Email was not delivered. Share the one-time password with the Community Head."
                    } else {
                        "Email was not delivered. The Community Head can sign in with their existing password."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningAmber
                )
            }

            result.oneTimePassword?.let { password ->
                Text(
                    "One-time password: $password",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss) { Text("Done") }
                Button(onClick = onViewCommunity) {
                    Icon(Icons.Filled.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("View Community")
                }
            }
        }
    }
}

@Composable
private fun ApplicationCard(application: HeadApplication, onApprove: () -> Unit, onReject: () -> Unit) {
    val statusColor = when (application.status) {
        "PENDING" -> WarningAmber
        "APPROVED" -> SuccessGreen
        "REJECTED" -> DangerRed
        else -> TextSecondary
    }
    CyberCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(application.full_name, style = MaterialTheme.typography.titleMedium)
                Text(application.status, style = MaterialTheme.typography.labelSmall, color = statusColor)
            }
            Text(application.organization, style = MaterialTheme.typography.bodyMedium)
            Text("Community: ${application.proposed_community_name}", style = MaterialTheme.typography.bodyMedium, color = PrimaryBlue)
            Text(application.reason, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
            if (application.status == "PENDING") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        modifier = Modifier.weight(1f)
                    ) { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Approve") }
                    OutlinedButton(
                        onClick = onReject,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed),
                        modifier = Modifier.weight(1f)
                    ) { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Reject") }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Communities Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CommunitiesScreen(
    communities: List<Community>,
    isLoading: Boolean,
    onSuspend: (String) -> Unit,
    onReactivate: (String) -> Unit,
    onBack: () -> Unit
) {
    var actionTarget by remember { mutableStateOf<Pair<Community, Boolean>?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column {
            CyberTopBar(title = "Communities", subtitle = "${communities.size} total", navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } })
            HorizontalDivider(color = BlueBorder)

            if (isLoading) { LoadingState(modifier = Modifier.fillMaxSize()); return@Column }
            if (communities.isEmpty()) { EmptyState(icon = Icons.Filled.Security, title = "No communities", message = "No communities have been created yet.", modifier = Modifier.fillMaxSize()); return@Column }

            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(communities, key = { it.id }) { community ->
                    CyberCard {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(community.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                StatusChip(if (community.status == "ACTIVE") ContentStatus.PUBLISHED else ContentStatus.ARCHIVED)
                            }
                            if (community.description != null) Text(community.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                            Text("${community.memberCount} members", style = MaterialTheme.typography.labelSmall)
                            if (community.status == "ACTIVE") {
                                SecondaryCyberButton(text = "Suspend", onClick = { actionTarget = community to true }, leadingIcon = Icons.Filled.PauseCircle)
                            } else {
                                PrimaryCyberButton(text = "Reactivate", onClick = { actionTarget = community to false }, leadingIcon = Icons.Filled.PlayCircle)
                            }
                        }
                    }
                }
            }
        }
    }

    actionTarget?.let { (community, isSuspend) ->
        ConfirmationDialog(
            title = if (isSuspend) "Suspend Community" else "Reactivate Community",
            message = if (isSuspend) "Members of ${community.name} will lose access immediately." else "Members of ${community.name} will regain access.",
            confirmText = if (isSuspend) "Suspend" else "Reactivate",
            isDangerous = isSuspend,
            onConfirm = { if (isSuspend) onSuspend(community.id) else onReactivate(community.id); actionTarget = null },
            onDismiss = { actionTarget = null }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Audit Logs Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AuditLogsScreen(
    logs: List<AuditLog>,
    isLoading: Boolean,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column {
            CyberTopBar(title = "Audit Logs", navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } })
            HorizontalDivider(color = BlueBorder)

            if (isLoading) { LoadingState(modifier = Modifier.fillMaxSize()); return@Column }
            if (logs.isEmpty()) { EmptyState(icon = Icons.Filled.AccessTime, title = "No audit events", message = "Administrative actions will be recorded here.", modifier = Modifier.fillMaxSize()); return@Column }

            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(logs, key = { it.id }) { log ->
                    CyberCard {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(log.action, style = MaterialTheme.typography.titleMedium)
                                Text(log.createdAt.take(16).replace("T", " "), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                            Text("Actor: @${log.actorUsername ?: log.actorUserId}", style = MaterialTheme.typography.bodyMedium)
                            if (log.target != null) Text("Target: ${log.target}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            if (log.metadata != null) Text(log.metadata.toString(), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Suspended User Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SuspendedUserScreen(reason: String?, onSignOut: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(AppBackground), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Filled.Block, contentDescription = null, tint = DangerRed, modifier = Modifier.size(64.dp))
            Text("Access Restricted", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Text(
                reason ?: "Your access to this community has been restricted. Contact your community head for more information.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            SecondaryCyberButton(text = "Sign Out", onClick = onSignOut, leadingIcon = Icons.Filled.Logout)
        }
    }
}
