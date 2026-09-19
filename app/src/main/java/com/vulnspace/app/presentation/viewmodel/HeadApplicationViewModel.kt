package com.vulnspace.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.supabase.SupabaseApi
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
        if (state.fullName.isBlank() || state.email.isBlank() || state.password.isBlank() || 
            state.organization.isBlank() || state.communityName.isBlank() || state.reason.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please fill in all required fields.")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            
            try {
                // 1. Sign up user
                val authResult = SupabaseApi.client.auth.signUpWith(Email) {
                    email = state.email
                    password = state.password
                }
                
                // If sign up is successful, the session is created.
                val userId = authResult?.id ?: SupabaseApi.client.auth.currentUserOrNull()?.id
                
                if (userId == null) {
                    _uiState.value = state.copy(isLoading = false, errorMessage = "Failed to create account. Please try again.")
                    return@launch
                }

                // 2. Insert into head_applications
                val applicationData = mapOf(
                    "applicant_user_id" to userId,
                    "full_name" to state.fullName,
                    "email" to state.email,
                    "phone" to state.phone.ifBlank { null },
                    "organization" to state.organization,
                    "proposed_community_name" to state.communityName,
                    "proposed_description" to state.communityDescription,
                    "reason" to state.reason
                )

                SupabaseApi.client.postgrest["head_applications"]
                    .insert(applicationData)
                
                _uiState.value = state.copy(isLoading = false, isSubmitted = true)
                onSuccess()
                
            } catch (e: Exception) {
                e.printStackTrace()
                // Could be "User already registered" or Postgrest error
                _uiState.value = state.copy(
                    isLoading = false, 
                    errorMessage = e.message ?: "An error occurred while submitting."
                )
            }
        }
    }
}
