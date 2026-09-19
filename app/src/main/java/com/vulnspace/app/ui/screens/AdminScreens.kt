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

@Composable
fun AdminDashboardScreen(
    stats: AdminStats,
    isLoading: Boolean,
    onApplicationsClick: () -> Unit,
    onCommunitiesClick: () -> Unit,
    onAuditLogsClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column {
            CyberTopBar(title = "Admin Dashboard", subtitle = "Platform Administration")
            HorizontalDivider(color = BlueBorder)

            if (isLoading) { LoadingState(modifier = Modifier.fillMaxSize()); return@Column }

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
            }
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
    onApprove: (String) -> Unit,
    onReject: (String, String) -> Unit,
    onBack: () -> Unit
) {
    var rejectTarget by remember { mutableStateOf<HeadApplication?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column {
            CyberTopBar(
                title = "Head Applications",
                subtitle = "${applications.count { it.status == "PENDING" }} pending",
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
            )
            HorizontalDivider(color = BlueBorder)

            if (isLoading) { LoadingState(modifier = Modifier.fillMaxSize()); return@Column }
            if (applications.isEmpty()) {
                EmptyState(icon = Icons.Filled.List, title = "No applications", message = "No head applications to review.", modifier = Modifier.fillMaxSize())
                return@Column
            }

            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(applications, key = { it.id }) { app ->
                    ApplicationCard(
                        application = app,
                        onApprove = { onApprove(app.id) },
                        onReject = { rejectTarget = app }
                    )
                }
            }
        }
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
                            if (log.metadata != null) Text(log.metadata, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
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
