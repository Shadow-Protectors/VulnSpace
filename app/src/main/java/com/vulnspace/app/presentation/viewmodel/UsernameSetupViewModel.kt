package com.vulnspace.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.supabase.SupabaseApi
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.gotrue.auth
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class UsernameSetupViewModel : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /**
     * Joins a community with an invite code.
     *
     * All validation (code exists, not revoked, not expired, uses left,
     * username free) happens SERVER-SIDE in the join-community Edge Function —
     * the client never touches invite_links or community_members directly
     * (RLS intentionally gives it no access there).
     */
    fun submit(inviteCode: String, username: String, onSuccess: () -> Unit) {
        val trimmedUsername = username.trim()
        if (inviteCode.isBlank()) {
            _error.value = "Invite code cannot be empty"
            return
        }
        if (trimmedUsername.length < 2) {
            _error.value = "Username must be at least 2 characters"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // 1. Ensure we have an authenticated session. Anonymous sign-in
                //    gives every joiner a real auth uid to attach membership to.
                if (SupabaseApi.client.auth.currentSessionOrNull() == null) {
                    try {
                        SupabaseApi.client.auth.signInAnonymously()
                    } catch (authEx: Exception) {
                        _isLoading.value = false
                        _error.value = "Could not start a session. Anonymous sign-ins may be disabled in Supabase Auth settings."
                        return@launch
                    }
                }

                val token = SupabaseApi.client.auth.currentAccessTokenOrNull()
                if (token.isNullOrBlank()) {
                    _isLoading.value = false
                    _error.value = "Could not start a session. Please try again."
                    return@launch
                }

                // 2. Ask the Edge Function to validate the code and join
                val response = SupabaseApi.client.functions.invoke(
                    function = "join-community",
                    body = buildJsonObject {
                        put("code", inviteCode.trim())
                        put("username", trimmedUsername)
                    }
                )

                val responseText = try { response.bodyAsText() } catch (_: Exception) { "" }

                if (response.status.value !in 200..299) {
                    val serverMsg = Regex("\"(?:error|message)\"\\s*:\\s*\"([^\"]+)\"")
                        .find(responseText)?.groupValues?.get(1)
                    _error.value = serverMsg ?: "Could not join (HTTP ${response.status.value}). Please try again."
                    _isLoading.value = false
                    return@launch
                }

                _isLoading.value = false
                onSuccess()

            } catch (e: Exception) {
                e.printStackTrace()
                _isLoading.value = false
                val raw = e.message.orEmpty()
                val serverMsg = Regex("\"(?:error|message)\"\\s*:\\s*\"([^\"]+)\"")
                    .find(raw)?.groupValues?.get(1)
                _error.value = when {
                    serverMsg != null && serverMsg.length < 160 && !serverMsg.contains("http") -> serverMsg
                    raw.contains("network", true) || raw.contains("connect", true) ->
                        "Network error. Please check your connection and try again."
                    else -> "Could not join the community. Please try again."
                }
            }
        }
    }
}
