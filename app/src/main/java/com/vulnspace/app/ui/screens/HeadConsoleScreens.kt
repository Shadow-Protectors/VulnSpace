package com.vulnspace.app.ui.screens

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vulnspace.app.domain.model.InviteLink
import com.vulnspace.app.domain.model.Member
import com.vulnspace.app.ui.components.*
import com.vulnspace.app.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// Head Console Hub (tabbed entry screen)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HeadConsoleScreen(
    communityName: String,
    onInviteCodesClick: () -> Unit,
    onMembersClick: () -> Unit,
    onContentClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column {
            CyberTopBar(title = "Head Console", subtitle = communityName)
            HorizontalDivider(color = BlueBorder)

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ConsoleMenuCard(
                    icon = Icons.Filled.Key,
                    title = "Invite Codes",
                    description = "Generate, revoke, and share invite codes for your community.",
                    onClick = onInviteCodesClick
                )
                ConsoleMenuCard(
                    icon = Icons.Filled.Group,
                    title = "Members",
                    description = "View, search, and manage active community members.",
                    onClick = onMembersClick
                )
                ConsoleMenuCard(
                    icon = Icons.Filled.ManageSearch,
                    title = "Content",
                    description = "Review, archive, or remove content submitted by members.",
                    onClick = onContentClick
                )
            }
        }
    }
}

@Composable
private fun ConsoleMenuCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    CyberCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(LightBlue, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Invite Code Management
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun InviteCodeManagementScreen(
    codes: List<InviteLink>,
    isLoading: Boolean,
    onGenerateCode: () -> Unit,
    onRevokeCode: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var codeToRevoke by remember { mutableStateOf<InviteLink?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column {
            CyberTopBar(
                title = "Invite Codes",
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = onGenerateCode) {
                        Icon(Icons.Filled.Add, contentDescription = "Generate code")
                    }
                }
            )
            HorizontalDivider(color = BlueBorder)

            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isLoading) { item { LoadingState() }; return@LazyColumn }
                if (codes.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.VpnKey,
                            title = "No invite codes",
                            message = "Tap + to generate a code you can share with prospective members.",
                            action = "Generate Code" to onGenerateCode
                        )
                    }
                    return@LazyColumn
                }
                items(codes, key = { it.id }) { code ->
                    InviteCodeCard(
                        code = code,
                        onShare = {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Join my VulnSpace community with code: ${code.code}")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Invite Code"))
                        },
                        onRevoke = { codeToRevoke = code }
                    )
                }
            }
        }
    }

    codeToRevoke?.let { code ->
        ConfirmationDialog(
            title = "Revoke Code",
            message = "The code ${code.code} will be deactivated. Members cannot use it to join after revocation.",
            confirmText = "Revoke",
            isDangerous = true,
            onConfirm = { onRevokeCode(code.id); codeToRevoke = null },
            onDismiss = { codeToRevoke = null }
        )
    }
}

@Composable
private fun InviteCodeCard(code: InviteLink, onShare: () -> Unit, onRevoke: () -> Unit) {
    val isActive = code.status == "ACTIVE"
    CyberCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = code.code,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                    color = if (isActive) PrimaryBlue else TextDisabled
                )
                StatusChip(if (isActive) ContentStatus.PUBLISHED else ContentStatus.ARCHIVED)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.PeopleAlt, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                Text("${code.usedCount}${if (code.maxUses != null) "/${code.maxUses}" else ""} uses", style = MaterialTheme.typography.labelMedium)
                if (code.expiresAt != null) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                    Text("Expires ${code.expiresAt}", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (isActive) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryCyberButton(text = "Share", onClick = onShare, leadingIcon = Icons.Filled.Share)
                    OutlinedButton(
                        onClick = onRevoke,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed)
                    ) { Text("Revoke") }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Member Management
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MemberManagementScreen(
    members: List<Member>,
    isLoading: Boolean,
    searchQuery: String,
    onSearch: (String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onBack: () -> Unit
) {
    var memberToRemove by remember { mutableStateOf<Member?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column {
            CyberTopBar(
                title = "Members",
                subtitle = "${members.size} active",
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
            )
            HorizontalDivider(color = BlueBorder)

            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearch,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search members", color = TextDisabled) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = TextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue, unfocusedBorderColor = BlueBorder, focusedContainerColor = WhiteSurface, unfocusedContainerColor = WhiteSurface),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (isLoading) { item { LoadingState() }; return@LazyColumn }
                if (members.isEmpty()) {
                    item { EmptyState(icon = Icons.Filled.People, title = "No members found", message = "No members match your search.") }
                    return@LazyColumn
                }
                items(members, key = { it.id }) { member ->
                    CyberCard {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.size(40.dp).background(LightBlue, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Text(member.username.take(2).uppercase(), style = MaterialTheme.typography.titleMedium, color = PrimaryBlue, fontWeight = FontWeight.Bold)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("@${member.username}", style = MaterialTheme.typography.titleMedium)
                                Text("Joined ${member.joinedAt.take(10)}", style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { memberToRemove = member }) {
                                Icon(Icons.Filled.PersonRemove, contentDescription = "Remove member", tint = DangerRed)
                            }
                        }
                    }
                }
            }
        }
    }

    memberToRemove?.let { member ->
        ConfirmationDialog(
            title = "Remove Member",
            message = "@${member.username} will lose access to the community. They can only return with a new invite code.",
            confirmText = "Remove",
            isDangerous = true,
            onConfirm = { onRemoveMember(member.id); memberToRemove = null },
            onDismiss = { memberToRemove = null }
        )
    }
}
