package com.vulnspace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vulnspace.app.presentation.viewmodel.CommunityHeadLoginViewModel
import com.vulnspace.app.ui.components.*
import com.vulnspace.app.ui.theme.*

@Composable
fun CommunityHeadLoginScreen(
    onForceCreatePassword: () -> Unit,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
    vm: CommunityHeadLoginViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column {
            CyberTopBar(
                title = "Community Head Login",
                subtitle = "Sign in with Google OAuth or enter your password",
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
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
                            text = "Coordinator Console Access",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // 1. Primary Action: Continue with Google
                        SecondaryCyberButton(
                            text = "Continue with Google",
                            onClick = {
                                vm.signInWithGoogle(
                                    idToken = null,
                                    onForceCreatePassword = onForceCreatePassword,
                                    onSuccess = onSuccess
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isGoogleLoading && !state.isLoading,
                            leadingIcon = Icons.Filled.AccountCircle
                        )

                        if (state.isGoogleLoading) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = PrimaryBlue,
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        // Divider with OR
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = BlueBorder)
                            Text(
                                text = "OR",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = BlueBorder)
                        }

                        // 2. Secondary Action: Sign in with email and password
                        OutlinedCyberTextField(
                            value = state.email,
                            onValueChange = vm::onEmailChange,
                            label = "Email Address",
                            leadingIcon = Icons.Filled.Email,
                            keyboardType = KeyboardType.Email
                        )

                        OutlinedCyberTextField(
                            value = state.password,
                            onValueChange = vm::onPasswordChange,
                            label = "Password",
                            leadingIcon = Icons.Filled.Lock,
                            keyboardType = KeyboardType.Password,
                            isPassword = true
                        )

                        val error = state.errorMessage
                        if (error != null) {
                            ErrorState(error)
                        }

                        PrimaryCyberButton(
                            text = "Login with Password",
                            onClick = {
                                vm.signInWithPassword(
                                    onForceCreatePassword = onForceCreatePassword,
                                    onSuccess = onSuccess
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.email.isNotBlank() && state.password.isNotBlank() && !state.isLoading && !state.isGoogleLoading,
                            isLoading = state.isLoading,
                            leadingIcon = Icons.Filled.Login
                        )
                    }
                }
            }
        }
    }
}
