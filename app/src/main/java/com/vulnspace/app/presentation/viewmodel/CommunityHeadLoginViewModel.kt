package com.vulnspace.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.supabase.SupabaseApi
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.Google
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.providers.builtin.IDToken
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CommunityHeadLoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val errorMessage: String? = null
)

class CommunityHeadLoginViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CommunityHeadLoginState())
    val uiState: StateFlow<CommunityHeadLoginState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email, errorMessage = null)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = null)
    }

    fun signInWithPassword(
        onForceCreatePassword: () -> Unit,
        onSuccess: () -> Unit
    ) {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please enter both email and password.")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                SupabaseApi.client.auth.signInWith(Email) {
                    email = state.email.trim()
                    password = state.password
                }

                // Call claim-head-grant to verify grant / membership and check password_configured
                handleClaimHeadGrant(
                    onForceCreatePassword = onForceCreatePassword,
                    onSuccess = onSuccess
                )
            } catch (_: Exception) {
                _uiState.value = state.copy(
                    isLoading = false,
                    errorMessage = "Incorrect password, or you haven't set one yet, use Continue with Google first."
                )
            }
        }
    }

    fun signInWithGoogle(
        idToken: String? = null,
        onForceCreatePassword: () -> Unit,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGoogleLoading = true, errorMessage = null)
            try {
                if (!idToken.isNullOrBlank()) {
                    SupabaseApi.client.auth.signInWith(IDToken) {
                        this.idToken = idToken
                        provider = Google
                    }
                    handleClaimHeadGrant(
                        onForceCreatePassword = onForceCreatePassword,
                        onSuccess = onSuccess
                    )
                } else {
                    SupabaseApi.client.auth.signInWith(Google)
                    _uiState.value = _uiState.value.copy(isGoogleLoading = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGoogleLoading = false,
                    errorMessage = sanitizeErrorMessage(e.message)
                )
            }
        }
    }

    private suspend fun handleClaimHeadGrant(
        onForceCreatePassword: () -> Unit,
        onSuccess: () -> Unit
    ) {
        val currentSession = SupabaseApi.client.auth.currentSessionOrNull()
        if (currentSession == null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isGoogleLoading = false,
                errorMessage = "Please complete Google Sign-In or enter your password."
            )
            return
        }

        try {
            val claimResponse = SupabaseApi.client.functions.invoke(
                function = "claim-head-grant"
            )
            val responseText = claimResponse.bodyAsText()
            val isClaimed = responseText.contains("\"claimed\":true")
            val isAlreadyHead = responseText.contains("\"already_head\":true")
            val isPasswordConfigured = responseText.contains("\"password_configured\":true")

            if (!isClaimed && !isAlreadyHead) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isGoogleLoading = false,
                    errorMessage = "No approved Community Head application found for this account. Please apply first."
                )
                return
            }

            _uiState.value = _uiState.value.copy(isLoading = false, isGoogleLoading = false)

            if (!isPasswordConfigured) {
                onForceCreatePassword()
            } else {
                onSuccess()
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isGoogleLoading = false,
                errorMessage = sanitizeErrorMessage(e.message)
            )
        }
    }

    private fun sanitizeErrorMessage(rawMessage: String?): String {
        val msg = rawMessage.orEmpty()
        return when {
            msg.contains("Invalid or expired session", ignoreCase = true) ||
            msg.contains("401", ignoreCase = true) ->
                "Please complete Google Sign-In or sign in with your email and password."
            msg.contains("No unclaimed community head grant", ignoreCase = true) ->
                "No approved Community Head application found for this email address."
            else -> msg.ifBlank { "Sign-in failed. Please try again." }
        }
    }
}
