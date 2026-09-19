package com.vulnspace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vulnspace.app.ui.components.*
import com.vulnspace.app.ui.theme.*

data class HeadApplicationFormState(
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val organization: String = "",
    val communityName: String = "",
    val communityDescription: String = "",
    val reason: String = "",
    val isLoading: Boolean = false,
    val isSubmitted: Boolean = false,
    val errorMessage: String? = null
)

@Composable
fun HeadApplicationFormScreen(
    state: HeadApplicationFormState,
    onFieldChange: (HeadApplicationFormState) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column {
            CyberTopBar(
                title = "Apply to Create a Community",
                subtitle = "Your application will be reviewed by a platform admin",
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
            HorizontalDivider(color = BlueBorder)

            if (state.isSubmitted) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Application Submitted", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Your application is under review. You will be notified when it is approved or rejected.", style = MaterialTheme.typography.bodyMedium)
                }
                return@Column
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CyberCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Personal Information", style = MaterialTheme.typography.titleMedium)
                        OutlinedCyberTextField(value = state.fullName, onValueChange = { onFieldChange(state.copy(fullName = it)) }, label = "Full Name *", leadingIcon = Icons.Filled.Person)
                        OutlinedCyberTextField(value = state.email, onValueChange = { onFieldChange(state.copy(email = it)) }, label = "Email *", leadingIcon = Icons.Filled.Email, keyboardType = KeyboardType.Email)
                        OutlinedCyberTextField(value = state.phone, onValueChange = { onFieldChange(state.copy(phone = it)) }, label = "Phone (optional)", leadingIcon = Icons.Filled.Phone, keyboardType = KeyboardType.Phone)
                        OutlinedCyberTextField(value = state.organization, onValueChange = { onFieldChange(state.copy(organization = it)) }, label = "College / Organization *", leadingIcon = Icons.Filled.School)
                    }
                }

                CyberCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Proposed Community", style = MaterialTheme.typography.titleMedium)
                        OutlinedCyberTextField(value = state.communityName, onValueChange = { onFieldChange(state.copy(communityName = it)) }, label = "Community Name *", leadingIcon = Icons.Filled.Group)
                        OutlinedCyberTextField(value = state.communityDescription, onValueChange = { onFieldChange(state.copy(communityDescription = it)) }, label = "Community Description *", singleLine = false, maxLines = 3)
                        OutlinedCyberTextField(value = state.reason, onValueChange = { onFieldChange(state.copy(reason = it)) }, label = "Why do you want to create this community? *", singleLine = false, maxLines = 5)
                    }
                }

                if (state.errorMessage != null) {
                    ErrorState(state.errorMessage)
                }

                val isValid = state.fullName.isNotBlank() && state.email.isNotBlank() &&
                    state.organization.isNotBlank() && state.communityName.isNotBlank() &&
                    state.communityDescription.isNotBlank() && state.reason.isNotBlank()

                PrimaryCyberButton(
                    text = "Submit Application",
                    onClick = onSubmit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isValid,
                    isLoading = state.isLoading,
                    leadingIcon = Icons.Filled.Send
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
