package com.vulnspace.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.supabase.SupabaseApi
import com.vulnspace.app.domain.model.InviteLink
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class HeadConsoleViewModel : ViewModel() {

    private val _codes = MutableStateFlow<List<InviteLink>>(emptyList())
    val codes: StateFlow<List<InviteLink>> = _codes

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private var communityId: String = ""

    fun initialize(communityId: String) {
        if (this.communityId == communityId) return
        this.communityId = communityId
        loadCodes()
    }

    private fun loadCodes() {
        if (communityId.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
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
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun generateCode() {
        if (communityId.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Generate a random 6-character alphanumeric string for the UI code
                val tokenStr = UUID.randomUUID().toString().substring(0, 8).uppercase()
                
                val newLink = mapOf(
                    "community_id" to communityId,
                    "token_hash" to tokenStr,
                    "status" to "ACTIVE"
                )
                
                SupabaseApi.client.postgrest["invite_links"].insert(newLink)
                loadCodes()
            } catch (e: Exception) {
                e.printStackTrace()
                _isLoading.value = false
            }
        }
    }

    fun revokeCode(codeId: String) {
        viewModelScope.launch {
            try {
                val updateData = mapOf("status" to "REVOKED")
                SupabaseApi.client.postgrest["invite_links"]
                    .update(updateData) {
                        filter { eq("id", codeId) }
                    }
                loadCodes()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
