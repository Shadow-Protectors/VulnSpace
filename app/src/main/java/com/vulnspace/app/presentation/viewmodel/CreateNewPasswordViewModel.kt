package com.vulnspace.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.supabase.SupabaseApi
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.github.jan.supabase.postgrest.postgrest

data class CreateNewPasswordState(
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class CreateNewPasswordViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CreateNewPasswordState())
    val uiState: StateFlow<CreateNewPasswordState> = _uiState

    fun onNewPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(newPassword = password, errorMessage = null)
    }

    fun onConfirmPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = password, errorMessage = null)
    }

    fun submitNewPassword(userId: String, onSuccess: () -> Unit) {
        val state = _uiState.value
        
        if (state.newPassword.isBlank() || state.confirmPassword.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please fill in all fields")
            return
        }
        
        if (state.newPassword.length < 8) {
            _uiState.value = state.copy(errorMessage = "Password must be at least 8 characters")
            return
        }
        
        if (state.newPassword != state.confirmPassword) {
            _uiState.value = state.copy(errorMessage = "Passwords do not match")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                // Update the user's password in Supabase Auth
                SupabaseApi.client.auth.modifyUser {
                    password = state.newPassword
                }
                
                // Set must_change_password to false in the profiles table
                SupabaseApi.client.postgrest["profiles"]
                    .update(buildJsonObject { put("must_change_password", false) }) {
                        filter { eq("id", userId) }
                    }
                
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = state.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to update password. Please try again."
                )
            }
        }
    }
}
