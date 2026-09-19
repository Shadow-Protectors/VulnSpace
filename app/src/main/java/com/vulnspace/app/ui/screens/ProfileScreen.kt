package com.vulnspace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vulnspace.app.ui.components.*
import com.vulnspace.app.ui.theme.*

@Composable
fun ProfileScreen(
    username: String,
    communityName: String,
    role: String,
    onApplyAsHead: () -> Unit,
    onSignOut: () -> Unit
) {
    var showSignOutDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column {
            CyberTopBar(title = "Profile")
            HorizontalDivider(color = BlueBorder)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Avatar and identity card
                CyberCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(LightBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = username.take(2).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                color = PrimaryBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column {
                            Text("@$username", style = MaterialTheme.typography.titleMedium)
                            Text(role.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                }

                // Community info
                CyberCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Shield, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            Text("Community", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(communityName.ifBlank { "No community" }, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    }
                }

                // Actions
                if (role == "MEMBER") {
                    CyberCard {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Expand your role", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text("You can apply to start your own VulnSpace community as a Community Head.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            SecondaryCyberButton(
                                text = "Apply as Community Head",
                                onClick = onApplyAsHead,
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = Icons.Filled.AdminPanelSettings
                            )
                        }
                    }
                }

                // Sign out
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showSignOutDialog = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, DangerRed)
                ) {
                    Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (showSignOutDialog) {
        ConfirmationDialog(
            title = "Sign Out",
            message = "You will need an invite code to rejoin your community.",
            confirmText = "Sign Out",
            isDangerous = true,
            onConfirm = onSignOut,
            onDismiss = { showSignOutDialog = false }
        )
    }
}
