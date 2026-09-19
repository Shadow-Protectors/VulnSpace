package com.vulnspace.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.supabase.SupabaseApi
import com.vulnspace.app.domain.model.HeadApplicationInsert
import com.vulnspace.app.ui.screens.HeadApplicationFormState
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HeadApplicationViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HeadApplicationFormState())
    val uiState: StateFlow<HeadApplicationFormState> = _uiState

    fun onFieldChange(newState: HeadApplicationFormState) {
        _uiState.value = newState.copy(errorMessage = null)
    }

    fun submitApplication(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.fullName.isBlank() || state.email.isBlank() || 
            state.organization.isBlank() || state.communityName.isBlank() || state.reason.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please fill in all required fields.")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            
            try {
                // Attempt anonymous session if not already signed in (doesn't block if disabled)
                val currentSession = SupabaseApi.client.auth.currentSessionOrNull() ?: run {
                    try {
                        SupabaseApi.client.auth.signInAnonymously()
                        SupabaseApi.client.auth.currentSessionOrNull()
                    } catch (authEx: Exception) {
                        authEx.printStackTrace()
                        null
                    }
                }
                val userId = currentSession?.user?.id

                // Insert into head_applications using typed @Serializable model
                val application = HeadApplicationInsert(
                    applicant_user_id = userId,
                    full_name = state.fullName.trim(),
                    email = state.email.trim(),
                    phone = state.phone.trim().ifBlank { null },
                    organization = state.organization.trim(),
                    proposed_community_name = state.communityName.trim(),
                    proposed_description = state.communityDescription.trim(),
                    reason = state.reason.trim(),
                    status = "PENDING"
                )

                SupabaseApi.client.postgrest["head_applications"]
                    .insert(application)
                
                _uiState.value = state.copy(isLoading = false, isSubmitted = true)
                onSuccess()
                
            } catch (e: Exception) {
                e.printStackTrace()
                val rawMsg = e.message.orEmpty()
                val safeErrorMessage = when {
                    rawMsg.contains("permission denied", ignoreCase = true) ->
                        "Database permission denied. Please grant table privileges in Supabase."
                    rawMsg.contains("anonymous_provider_disabled", ignoreCase = true) || rawMsg.contains("Anonymous sign-ins are disabled", ignoreCase = true) ->
                        "Anonymous sign-ins are disabled in Supabase. Please enable Anonymous provider in your Supabase dashboard."
                    rawMsg.contains("network", ignoreCase = true) || rawMsg.contains("connect", ignoreCase = true) ->
                        "Network error. Please check your internet connection and try again."
                    else -> rawMsg.ifBlank { "Failed to submit application. Please try again later." }
                }
                _uiState.value = state.copy(
                    isLoading = false, 
                    errorMessage = safeErrorMessage
                )
            }
        }
    }
}
