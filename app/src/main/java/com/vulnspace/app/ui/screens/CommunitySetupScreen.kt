package com.vulnspace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vulnspace.app.ui.components.PrimaryCyberButton
import com.vulnspace.app.ui.components.ErrorState
import com.vulnspace.app.ui.components.OutlinedCyberTextField
import com.vulnspace.app.ui.theme.AppBackground
import com.vulnspace.app.ui.theme.TextPrimary

@Composable
fun CommunitySetupScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onSubmit: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(AppBackground).padding(24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Complete Community Setup", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Text("Your head application has been approved! Provide the final details for your community.", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            
            OutlinedCyberTextField(
                value = name,
                onValueChange = { name = it },
                label = "Community Name",
                leadingIcon = Icons.Filled.Business
            )
            
            OutlinedCyberTextField(
                value = description,
                onValueChange = { description = it },
                label = "Description",
                leadingIcon = Icons.Filled.Description
            )
            
            if (errorMessage != null) {
                ErrorState(errorMessage)
            }
            
            Spacer(Modifier.height(16.dp))
            
            PrimaryCyberButton(
                text = if (isLoading) "Creating..." else "Create Community",
                onClick = { onSubmit(name, description) },
                enabled = !isLoading && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
