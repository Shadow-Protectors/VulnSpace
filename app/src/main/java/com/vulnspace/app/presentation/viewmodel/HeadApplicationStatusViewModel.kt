package com.vulnspace.app.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.local.EncryptedTokenStorage
import com.vulnspace.app.data.supabase.SupabaseApi
import com.vulnspace.app.domain.model.ClaimHeadGrantResponse
import com.vulnspace.app.domain.model.HeadApplicationStatusCheckResponse
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.Google
import io.github.jan.supabase.gotrue.providers.builtin.IDToken
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed class HeadApplicationTrackingState {
    data object NoTrackedApplication : HeadApplicationTrackingState()
    data object CheckingStatus : HeadApplicationTrackingState()
    data class Pending(val communityName: String, val applicationId: String) : HeadApplicationTrackingState()
    data class ApprovedReadyToClaim(val communityName: String, val applicationId: String) : HeadApplicationTrackingState()
    data class Rejected(val reason: String?) : HeadApplicationTrackingState()
    data object StartingGoogleSignIn : HeadApplicationTrackingState()
    data object Claiming : HeadApplicationTrackingState()
    data class Claimed(val communityId: String, val communityName: String) : HeadApplicationTrackingState()
    data class RecoverableError(val message: String, val lastState: HeadApplicationTrackingState? = null) : HeadApplicationTrackingState()
    data object TrackingExpired : HeadApplicationTrackingState()
}

class HeadApplicationStatusViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStorage = EncryptedTokenStorage(application.applicationContext)
    private val json = Json { ignoreUnknownKeys = true }

    private val _trackingState = MutableStateFlow<HeadApplicationTrackingState>(HeadApplicationTrackingState.NoTrackedApplication)
    val trackingState: StateFlow<HeadApplicationTrackingState> = _trackingState.asStateFlow()

    init {
        restoreAndCheckStatus()
    }

    fun restoreAndCheckStatus() {
        val rawToken = tokenStorage.getTrackingToken()
        val appId = tokenStorage.getApplicationId() ?: ""
        val commName = tokenStorage.getCommunityName() ?: "Cyber Community"

        if (rawToken.isNullOrBlank()) {
            _trackingState.value = HeadApplicationTrackingState.NoTrackedApplication
            return
        }

        checkStatus()
    }

    fun checkStatus() {
        val rawToken = tokenStorage.getTrackingToken()
        val fallbackAppId = tokenStorage.getApplicationId() ?: ""
        val fallbackCommName = tokenStorage.getCommunityName() ?: "Cyber Community"

        if (rawToken.isNullOrBlank()) {
            _trackingState.value = HeadApplicationTrackingState.NoTrackedApplication
            return
        }

        viewModelScope.launch {
            _trackingState.value = HeadApplicationTrackingState.CheckingStatus
            try {
                val response = SupabaseApi.client.functions.invoke(
                    function = "check-head-application-status",
                    body = buildJsonObject {
                        put("trackingToken", rawToken)
                    }
                )

                val responseText = response.bodyAsText()

                if (response.status.value == 404 || responseText.contains("EXPIRED") || responseText.contains("expired")) {
                    _trackingState.value = HeadApplicationTrackingState.TrackingExpired
                    return@launch
                }

                if (response.status.value !in 200..299) {
                    val errorMsg = parseErrorMessage(responseText, "Unable to check status at this time.")
                    _trackingState.value = HeadApplicationTrackingState.RecoverableError(errorMsg)
                    return@launch
                }

                val statusResult = json.decodeFromString<HeadApplicationStatusCheckResponse>(responseText)
                val resolvedAppId = statusResult.applicationId ?: fallbackAppId
                val resolvedCommName = statusResult.communityName ?: fallbackCommName

                when (statusResult.status.uppercase()) {
                    "APPROVED" -> {
                        _trackingState.value = HeadApplicationTrackingState.ApprovedReadyToClaim(
                            communityName = resolvedCommName,
                            applicationId = resolvedAppId
                        )
                    }
                    "REJECTED" -> {
                        _trackingState.value = HeadApplicationTrackingState.Rejected(
                            reason = statusResult.reason
                        )
                    }
                    "CLAIMED" -> {
                        _trackingState.value = HeadApplicationTrackingState.Claimed(
                            communityId = "",
                            communityName = resolvedCommName
                        )
                    }
                    "EXPIRED" -> {
                        _trackingState.value = HeadApplicationTrackingState.TrackingExpired
                    }
                    else -> {
                        _trackingState.value = HeadApplicationTrackingState.Pending(
                            communityName = resolvedCommName,
                            applicationId = resolvedAppId
                        )
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _trackingState.value = HeadApplicationTrackingState.RecoverableError(
                    message = "Network error. Tap refresh to try again."
                )
            }
        }
    }

    fun claimWithGoogle(idToken: String? = null, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _trackingState.value = HeadApplicationTrackingState.StartingGoogleSignIn
            try {
                if (!idToken.isNullOrBlank()) {
                    SupabaseApi.client.auth.signInWith(IDToken) {
                        this.idToken = idToken
                        provider = Google
                    }
                } else {
                    SupabaseApi.client.auth.signInWith(Google)
                }

                val currentSession = SupabaseApi.client.auth.currentSessionOrNull()
                if (currentSession == null) {
                    _trackingState.value = HeadApplicationTrackingState.RecoverableError("Google sign-in incomplete. Please try again.")
                    return@launch
                }

                _trackingState.value = HeadApplicationTrackingState.Claiming

                val claimResponse = SupabaseApi.client.functions.invoke(
                    function = "claim-head-grant"
                )

                val responseText = claimResponse.bodyAsText()
                val claimResult = try {
                    json.decodeFromString<ClaimHeadGrantResponse>(responseText)
                } catch (_: Exception) {
                    null
                }

                if (claimResult != null && (claimResult.claimed || claimResult.alreadyHead)) {
                    // Confirmed claim - clear local token storage
                    tokenStorage.clearTrackedApplication()

                    val communityId = claimResult.communityId ?: ""
                    val commName = claimResult.communityName ?: "Cyber Community"

                    _trackingState.value = HeadApplicationTrackingState.Claimed(
                        communityId = communityId,
                        communityName = commName
                    )

                    onSuccess()
                } else {
                    val expectedEmail = tokenStorage.getApplicantEmail()
                    val errorMsg = if (claimResult?.message?.contains("No unclaimed", ignoreCase = true) == true) {
                        "Use the same Google account associated with your approved application."
                    } else {
                        claimResult?.message ?: claimResult?.error ?: "Failed to claim community. Please verify your Google account."
                    }
                    _trackingState.value = HeadApplicationTrackingState.RecoverableError(errorMsg)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                val rawMsg = e.message.orEmpty()
                val safeMsg = when {
                    rawMsg.contains("No unclaimed", ignoreCase = true) ->
                        "Use the same Google account associated with your approved application."
                    else -> "Failed to claim community: ${rawMsg.ifBlank { "Unknown error" }}"
                }
                _trackingState.value = HeadApplicationTrackingState.RecoverableError(safeMsg)
            }
        }
    }

    fun clearTrackedState() {
        tokenStorage.clearTrackedApplication()
        _trackingState.value = HeadApplicationTrackingState.NoTrackedApplication
    }

    private fun parseErrorMessage(responseText: String, defaultMessage: String): String {
        return try {
            val parsed = json.decodeFromString<Map<String, String>>(responseText)
            parsed["error"] ?: parsed["message"] ?: defaultMessage
        } catch (_: Exception) {
            defaultMessage
        }
    }
}
