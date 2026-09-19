package com.vulnspace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vulnspace.app.presentation.viewmodel.CommunityHeadLoginViewModel
import com.vulnspace.app.ui.components.*
import com.vulnspace.app.ui.theme.*

@Composable
fun CommunityHeadLoginScreen(
    onSuccess: () -> Unit,
    onBack: () -> Unit,
    vm: CommunityHeadLoginViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column {
            CyberTopBar(
                title = "Community Head Login",
                subtitle = "Log in with your temporary or permanent password",
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
            HorizontalDivider(color = BlueBorder)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center
            ) {
                CyberCard {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Access your console",
                            style = MaterialTheme.typography.titleMedium
                        )

                        OutlinedCyberTextField(
                            value = state.email,
                            onValueChange = vm::onEmailChange,
                            label = "Email",
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
                            text = "Login",
                            onClick = { vm.signIn(onSuccess) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.email.isNotBlank() && state.password.isNotBlank() && !state.isLoading,
                            isLoading = state.isLoading,
                            leadingIcon = Icons.Filled.Login
                        )
                    }
                }
            }
        }
    }
}
