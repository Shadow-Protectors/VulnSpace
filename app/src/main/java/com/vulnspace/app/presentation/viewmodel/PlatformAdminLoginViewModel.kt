package com.vulnspace.app.presentation.viewmodel

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.repository.PlatformAdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminLoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false
)

class PlatformAdminLoginViewModel(
    private val platformAdminRepository: PlatformAdminRepository = PlatformAdminRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminLoginUiState())
    val uiState: StateFlow<AdminLoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email, errorMessage = null)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = null)
    }

    fun signInAsAdmin(onSuccess: () -> Unit) {
        val state = _uiState.value
        val trimmedEmail = state.email.trim()
        val password = state.password

        // Field validation
        if (trimmedEmail.isBlank() || password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Email and password are required")
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            _uiState.value = state.copy(errorMessage = "Invalid email format")
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val result = platformAdminRepository.signInAsAdmin(trimmedEmail, password)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        password = "", // Clear password after successful login
                        isLoading = false,
                        isAuthenticated = true,
                        errorMessage = null
                    )
                    onSuccess()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        password = "", // Clear password on error
                        isLoading = false,
                        errorMessage = error.message ?: "Authentication failed"
                    )
                }
            )
        }
    }
}
