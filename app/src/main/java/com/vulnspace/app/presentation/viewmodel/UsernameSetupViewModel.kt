package com.vulnspace.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.supabase.SupabaseApi
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class UsernameSetupViewModel : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun submit(inviteCode: String, username: String, onSuccess: () -> Unit) {
        if (username.isBlank()) {
            _error.value = "Username cannot be empty"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // 1. Validate invite code exists and is ACTIVE
                val inviteResult = SupabaseApi.client.postgrest["invite_links"]
                    .select { 
                        filter { 
                            eq("token_hash", inviteCode)
                            eq("status", "ACTIVE") 
                        } 
                    }
                    .decodeList<JsonObject>()

                if (inviteResult.isEmpty()) {
                    _error.value = "Invalid or expired invite code."
                    _isLoading.value = false
                    return@launch
                }
                
                val communityId = inviteResult.first()["community_id"]?.jsonPrimitive?.content ?: ""

                // 2. Create anonymous session
                val authResult = SupabaseApi.client.auth.signInAnonymously()
                val userId = SupabaseApi.client.auth.currentUserOrNull()?.id
                
                if (userId == null) {
                    _error.value = "Failed to create anonymous session."
                    _isLoading.value = false
                    return@launch
                }

                // 3. Insert into community_members
                val memberData = mapOf(
                    "user_id" to userId,
                    "community_id" to communityId,
                    "username" to username,
                    "role" to "MEMBER"
                )

                SupabaseApi.client.postgrest["community_members"]
                    .insert(memberData)

                _isLoading.value = false
                onSuccess()

            } catch (e: Exception) {
                e.printStackTrace()
                _isLoading.value = false
                _error.value = e.message ?: "An error occurred while joining."
            }
        }
    }
}
