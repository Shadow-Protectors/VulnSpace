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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vulnspace.app.ui.components.*
import com.vulnspace.app.ui.theme.*

@Composable
fun UsernameSetupScreen(
    inviteCode: String,
    username: String,
    onUsernameChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onSubmit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))

            Icon(
                Icons.Filled.PersonAdd,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(56.dp)
            )

            Spacer(Modifier.height(24.dp))

            Text("Choose your username", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, textAlign = TextAlign.Center)

            Spacer(Modifier.height(8.dp))

            Text(
                "This is how you appear in your community. You can use a pseudonym.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(36.dp))

            OutlinedCyberTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = "Username",
                placeholder = "e.g. h4x0r_42",
                leadingIcon = Icons.Filled.AlternateEmail,
                isError = errorMessage != null,
                errorMessage = errorMessage,
                enabled = !isLoading
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "3–24 characters. Letters, numbers, underscores, and hyphens only.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(Modifier.height(32.dp))

            PrimaryCyberButton(
                text = "Enter Community",
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = username.length >= 3,
                isLoading = isLoading,
                leadingIcon = Icons.Filled.Check
            )
        }
    }
}
