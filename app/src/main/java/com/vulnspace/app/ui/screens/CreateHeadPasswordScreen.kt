package com.vulnspace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vulnspace.app.presentation.viewmodel.CreateHeadPasswordViewModel
import com.vulnspace.app.ui.components.*
import com.vulnspace.app.ui.theme.*

@Composable
fun CreateHeadPasswordScreen(
    onSuccess: () -> Unit,
    vm: CreateHeadPasswordViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column {
            CyberTopBar(
                title = "Set Permanent Password",
                subtitle = "Required step for your Community Head account",
                navigationIcon = null
            )
            HorizontalDivider(color = BlueBorder)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                CyberCard {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Create Password",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Set a password so you can sign in with either Google OAuth or email and password going forward.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        OutlinedCyberTextField(
                            value = state.newPassword,
                            onValueChange = vm::onNewPasswordChange,
                            label = "New Password (min 8 characters)",
                            leadingIcon = Icons.Filled.Lock,
                            keyboardType = KeyboardType.Password,
                            isPassword = true
                        )

                        OutlinedCyberTextField(
                            value = state.confirmPassword,
                            onValueChange = vm::onConfirmPasswordChange,
                            label = "Confirm New Password",
                            leadingIcon = Icons.Filled.Lock,
                            keyboardType = KeyboardType.Password,
                            isPassword = true
                        )

                        val error = state.errorMessage
                        if (error != null) {
                            ErrorState(error)
                        }

                        PrimaryCyberButton(
                            text = "Save Password & Continue",
                            onClick = { vm.submitNewPassword(onSuccess) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.newPassword.isNotBlank() && state.confirmPassword.isNotBlank() && !state.isLoading,
                            isLoading = state.isLoading,
                            leadingIcon = Icons.Filled.Save
                        )
                    }
                }
            }
        }
    }
}
