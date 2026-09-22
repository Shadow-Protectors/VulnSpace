package com.vulnspace.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.supabase.SupabaseApi
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class CreateHeadPasswordState(
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class CreateHeadPasswordViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CreateHeadPasswordState())
    val uiState: StateFlow<CreateHeadPasswordState> = _uiState.asStateFlow()

    fun onNewPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(newPassword = password, errorMessage = null)
    }

    fun onConfirmPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = password, errorMessage = null)
    }

    fun submitNewPassword(onSuccess: () -> Unit) {
        val state = _uiState.value

        if (state.newPassword.isBlank() || state.confirmPassword.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please fill in all fields.")
            return
        }

        if (state.newPassword.length < 8) {
            _uiState.value = state.copy(errorMessage = "Password must be at least 8 characters long.")
            return
        }

        if (state.newPassword != state.confirmPassword) {
            _uiState.value = state.copy(errorMessage = "Passwords do not match.")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                val user = SupabaseApi.client.auth.currentUserOrNull()
                if (user == null) {
                    _uiState.value = state.copy(isLoading = false, errorMessage = "Not authenticated.")
                    return@launch
                }

                // 1. Update user password in Supabase Auth
                SupabaseApi.client.auth.updateUser {
                    password = state.newPassword
                }

                // 2. Set password_configured = true on profiles table
                SupabaseApi.client.postgrest["profiles"]
                    .update(buildJsonObject { put("password_configured", true) }) {
                        filter { eq("id", user.id) }
                    }

                _uiState.value = state.copy(isLoading = false)
                onSuccess()

            } catch (e: Exception) {
                _uiState.value = state.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to set password. Please try again."
                )
            }
        }
    }
}
