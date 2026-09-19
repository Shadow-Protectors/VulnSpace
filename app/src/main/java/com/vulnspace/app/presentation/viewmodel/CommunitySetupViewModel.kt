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

class CommunitySetupViewModel : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun submitSetup(
        communityName: String,
        communityDescription: String,
        onSuccess: () -> Unit
    ) {
        if (communityName.isBlank()) {
            _error.value = "Community name is required"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val userId = SupabaseApi.client.auth.currentUserOrNull()?.id
                if (userId == null) {
                    _error.value = "Not authenticated"
                    _isLoading.value = false
                    return@launch
                }

                // 1. Insert into communities
                val communityData = mapOf(
                    "name" to communityName,
                    "description" to communityDescription,
                    "created_by" to userId,
                    "status" to "ACTIVE"
                )

                val communityResult = SupabaseApi.client.postgrest["communities"]
                    .insert(communityData) { select() }
                    .decodeList<JsonObject>()
                
                if (communityResult.isEmpty()) {
                    _error.value = "Failed to create community"
                    _isLoading.value = false
                    return@launch
                }
                
                val communityId = communityResult.first()["id"]?.jsonPrimitive?.content ?: ""

                // 2. Insert into community_members as COMMUNITY_HEAD
                val memberData = mapOf(
                    "user_id" to userId,
                    "community_id" to communityId,
                    // Get full name from head_applications or profile, for now just use a default or empty string
                    "username" to "Community Head", 
                    "role" to "COMMUNITY_HEAD"
                )

                SupabaseApi.client.postgrest["community_members"]
                    .insert(memberData)

                _isLoading.value = false
                onSuccess()

            } catch (e: Exception) {
                e.printStackTrace()
                _isLoading.value = false
                _error.value = e.message ?: "An error occurred during setup."
            }
        }
    }
}
