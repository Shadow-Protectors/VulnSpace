package com.vulnspace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vulnspace.app.ui.components.*
import com.vulnspace.app.ui.theme.*

data class JoinState(
    val code: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isCodeValid: Boolean = false
)

@Composable
fun JoinCommunityScreen(
    state: JoinState,
    onCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
    onBack: () -> Unit
) {
    val clipboard: ClipboardManager = LocalClipboardManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(Modifier.height(24.dp))

            Text(
                text = "Join a Community",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Enter the invite code shared by your community head. Codes are case-insensitive.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(Modifier.height(32.dp))

            InviteCodeInput(
                value = state.code,
                onValueChange = onCodeChange,
                onPaste = {
                    val text = clipboard.getText()?.text ?: ""
                    if (text.isNotBlank()) onCodeChange(text)
                },
                isError = state.errorMessage != null,
                errorMessage = state.errorMessage,
                enabled = !state.isLoading
            )

            Spacer(Modifier.height(24.dp))

            // Status explanation cards
            if (state.errorMessage != null) {
                ErrorExplanationCard(state.errorMessage)
                Spacer(Modifier.height(16.dp))
            }

            PrimaryCyberButton(
                text = "Join Community",
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.code.length >= 6,
                isLoading = state.isLoading,
                leadingIcon = Icons.Filled.Login
            )

            Spacer(Modifier.height(24.dp))

            // What to expect
            CyberCard {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                    Column {
                        Text("How invite codes work", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Your community head generates a unique code. Codes may have an expiry date or a maximum number of uses. Ask your head for a fresh code if yours is not working.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorExplanationCard(error: String) {
    val (icon, bg, textColor) = when {
        error.contains("expired", ignoreCase = true) -> Triple(Icons.Filled.EventBusy, WarningBackground, WarningAmber)
        error.contains("revoked", ignoreCase = true) -> Triple(Icons.Filled.Block, DangerBackground, DangerRed)
        error.contains("full", ignoreCase = true) || error.contains("max", ignoreCase = true) -> Triple(Icons.Filled.GroupOff, WarningBackground, WarningAmber)
        error.contains("suspended", ignoreCase = true) -> Triple(Icons.Filled.PauseCircle, DangerBackground, DangerRed)
        else -> Triple(Icons.Filled.ErrorOutline, DangerBackground, DangerRed)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(20.dp))
        Text(text = error, style = MaterialTheme.typography.bodyMedium, color = textColor)
    }
}
