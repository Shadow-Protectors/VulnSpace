package com.vulnspace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vulnspace.app.presentation.viewmodel.SignInState
import com.vulnspace.app.ui.components.ErrorState
import com.vulnspace.app.ui.components.OutlinedCyberTextField
import com.vulnspace.app.ui.components.PrimaryCyberButton
import com.vulnspace.app.ui.theme.AppBackground
import com.vulnspace.app.ui.theme.BlueBorder
import com.vulnspace.app.ui.components.CyberTopBar

@Composable
fun SignInScreen(
    state: SignInState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            CyberTopBar(
                title = "Sign In",
                subtitle = "For Community Heads & Admins",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
            HorizontalDivider(color = BlueBorder)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                OutlinedCyberTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = "Email address",
                    leadingIcon = Icons.Filled.Email,
                    keyboardType = KeyboardType.Email,
                    enabled = !state.isLoading
                )

                Spacer(Modifier.height(16.dp))

                OutlinedCyberTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = "Password",
                    leadingIcon = Icons.Filled.Lock,
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    enabled = !state.isLoading
                )

                Spacer(Modifier.height(24.dp))

                if (state.errorMessage != null) {
                    ErrorState(state.errorMessage)
                    Spacer(Modifier.height(16.dp))
                }

                PrimaryCyberButton(
                    text = "Sign In",
                    onClick = onSubmit,
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = state.isLoading,
                    enabled = state.email.isNotBlank() && state.password.isNotBlank() && !state.isLoading,
                    leadingIcon = Icons.Filled.Login
                )
            }
        }
    }
}
