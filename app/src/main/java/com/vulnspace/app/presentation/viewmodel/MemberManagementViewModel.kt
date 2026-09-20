package com.vulnspace.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.supabase.SupabaseApi
import com.vulnspace.app.domain.model.Member
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class MembersUiState(
    val members: List<Member> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val errorMessage: String? = null
) {
    val visible: List<Member>
        get() {
            val q = searchQuery.trim().lowercase()
            return if (q.isBlank()) members
            else members.filter {
                it.username.lowercase().contains(q) || it.role.lowercase().contains(q)
            }
        }
}

class MemberManagementViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MembersUiState())
    val uiState: StateFlow<MembersUiState> = _uiState.asStateFlow()

    private var communityId: String = ""

    fun initialize(communityId: String) {
        if (this.communityId == communityId) return
        this.communityId = communityId
        loadMembers()
    }

    fun loadMembers() {
        if (communityId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val result = SupabaseApi.client.postgrest["community_members"]
                    .select {
                        filter {
                            eq("community_id", communityId)
                            eq("status", "ACTIVE")
                        }
                    }
                    .decodeList<Member>()

                // Heads first, then alphabetical
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        members = result.sortedWith(
                            compareBy<Member> { m -> if (m.role == "COMMUNITY_HEAD" || m.role == "HEAD") 0 else 1 }
                                .thenBy { m -> m.username.lowercase() }
                        )
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Could not load members. Try again.") }
            }
        }
    }

    fun onSearch(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun removeMember(memberId: String) {
        if (communityId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = SupabaseApi.client.functions.invoke(
                    function = "manage-member",
                    body = buildJsonObject {
                        put("action", "REMOVE")
                        put("community_id", communityId)
                        put("member_id", memberId)
                    }
                )

                if (response.status.value !in 200..299) {
                    val text = try { response.bodyAsText() } catch (_: Exception) { "" }
                    val serverMsg = Regex("\"(?:error|message)\"\\s*:\\s*\"([^\"]+)\"")
                        .find(text)?.groupValues?.get(1)
                    throw Exception(serverMsg ?: "Server returned HTTP ${response.status.value}")
                }

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        members = state.members.filterNot { it.id == memberId }
                    )
                }
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = if (msg.isNotBlank() && msg.length < 160 && !msg.contains("http"))
                            msg else "Could not remove the member. Try again."
                    )
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
