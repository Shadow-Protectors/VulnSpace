package com.vulnspace.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.supabase.SupabaseApi
import com.vulnspace.app.domain.model.InviteLink
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class HeadConsoleViewModel : ViewModel() {

    private val _codes = MutableStateFlow<List<InviteLink>>(emptyList())
    val codes: StateFlow<List<InviteLink>> = _codes

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var communityId: String = ""

    fun initialize(communityId: String) {
        if (this.communityId == communityId) return
        this.communityId = communityId
        loadCodes()
    }

    fun loadCodes() {
        if (communityId.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = SupabaseApi.client.postgrest["invite_links"]
                    .select {
                        filter {
                            eq("community_id", communityId)
                        }
                    }
                    .decodeList<InviteLink>()

                _codes.value = result.sortedByDescending { it.createdAt }
            } catch (e: Exception) {
                _errorMessage.value = extractFriendlyError(e, "Could not load invite codes.")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun generateCode() {
        if (communityId.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                invokeManageInviteLink(
                    buildJsonObject {
                        put("action", "CREATE")
                        put("community_id", communityId)
                    }
                )
                loadCodes()
            } catch (e: Exception) {
                _errorMessage.value = extractFriendlyError(e, "Could not create an invite code.")
                _isLoading.value = false
            }
        }
    }

    fun revokeCode(codeId: String) {
        if (communityId.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                invokeManageInviteLink(
                    buildJsonObject {
                        put("action", "REVOKE")
                        put("community_id", communityId)
                        put("link_id", codeId)
                    }
                )
                loadCodes()
            } catch (e: Exception) {
                _errorMessage.value = extractFriendlyError(e, "Could not revoke the invite code.")
                _isLoading.value = false
            }
        }
    }

    fun clearError() { _errorMessage.value = null }

    /**
     * Calls the manage-invite-link Edge Function and throws with a readable
     * message when the function responds with a non-2xx status.
     */
    private suspend fun invokeManageInviteLink(body: kotlinx.serialization.json.JsonObject) {
        val response = SupabaseApi.client.functions.invoke(
            function = "manage-invite-link",
            body = body
        )
        if (response.status.value !in 200..299) {
            val text = try { response.bodyAsText() } catch (_: Exception) { "" }
            val serverMsg = Regex("\"(?:error|message)\"\\s*:\\s*\"([^\"]+)\"")
                .find(text)?.groupValues?.get(1)
            throw Exception(serverMsg ?: "Server returned HTTP ${response.status.value}")
        }
    }

    private fun extractFriendlyError(e: Throwable, fallback: String): String {
        val raw = e.message.orEmpty()
        val serverMsg = Regex("\"(?:error|message)\"\\s*:\\s*\"([^\"]+)\"")
            .find(raw)?.groupValues?.get(1) ?: raw
        val lower = serverMsg.lowercase()
        return when {
            lower.contains("http") || lower.contains("bearer ") || lower.contains("eyj") -> fallback
            lower.contains("network") || lower.contains("connect") || lower.contains("timeout") ->
                "Network error. Check your connection and try again."
            serverMsg.isNotBlank() && serverMsg.length < 160 && !serverMsg.contains("{") -> serverMsg
            else -> fallback
        }
    }
}
