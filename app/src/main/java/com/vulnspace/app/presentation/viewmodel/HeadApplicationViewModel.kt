package com.vulnspace.app.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.local.EncryptedTokenStorage
import com.vulnspace.app.data.supabase.SupabaseApi
import com.vulnspace.app.domain.model.HeadApplicationSubmissionRequest
import com.vulnspace.app.domain.model.HeadApplicationSubmissionResponse
import com.vulnspace.app.ui.screens.HeadApplicationFormState
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class HeadApplicationViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStorage = EncryptedTokenStorage(application.applicationContext)
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(HeadApplicationFormState())
    val uiState: StateFlow<HeadApplicationFormState> = _uiState.asStateFlow()

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
                val payload = buildJsonObject {
                    put("fullName", state.fullName.trim())
                    put("email", state.email.trim())
                    if (state.phone.isNotBlank()) put("phone", state.phone.trim())
                    put("organization", state.organization.trim())
                    put("proposedCommunityName", state.communityName.trim())
                    put("proposedDescription", state.communityDescription.trim())
                    put("reason", state.reason.trim())
                }

                val response = SupabaseApi.client.functions.invoke(
                    function = "submit-head-application",
                    body = payload
                )

                val responseText = response.bodyAsText()

                if (response.status.value !in 200..299) {
                    val serverError = try {
                        val parsed = json.decodeFromString<Map<String, String>>(responseText)
                        parsed["error"] ?: "Failed to submit application (HTTP ${response.status.value})"
                    } catch (_: Exception) {
                        "Failed to submit application (HTTP ${response.status.value})"
                    }
                    throw Exception(serverError)
                }

                val result = json.decodeFromString<HeadApplicationSubmissionResponse>(responseText)

                // Securely store raw tracking token and metadata locally
                tokenStorage.saveTrackedApplication(
                    token = result.trackingToken,
                    applicationId = result.applicationId,
                    communityName = result.communityName ?: state.communityName.trim(),
                    applicantEmail = state.email.trim()
                )

                _uiState.value = state.copy(isLoading = false, isSubmitted = true)
                onSuccess()
                
            } catch (e: Exception) {
                e.printStackTrace()
                val rawMsg = e.message.orEmpty()
                val safeErrorMessage = when {
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
