package com.vulnspace.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.supabase.SupabaseApi
import com.vulnspace.app.domain.model.Content
import com.vulnspace.app.domain.model.ContentDates
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class ContentDetailUiState(
    val content: Content? = null,
    val isLoading: Boolean = false,
    val isBookmarked: Boolean = false,
    val error: String? = null,
    val reported: Boolean = false
)

class ContentDetailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ContentDetailUiState())
    val uiState: StateFlow<ContentDetailUiState> = _uiState.asStateFlow()

    private var contentId: String = ""

    fun load(contentId: String) {
        if (contentId.isBlank()) return
        this.contentId = contentId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val content = SupabaseApi.client.postgrest["content"]
                    .select { filter { eq("id", contentId) } }
                    .decodeList<Content>()
                    .firstOrNull()
                    ?.let { it.copy(daysLeft = ContentDates.daysUntil(it.registrationDeadline)) }

                val uid = SupabaseApi.client.auth.currentUserOrNull()?.id
                val isBookmarked = if (!uid.isNullOrBlank() && content != null) {
                    try {
                        SupabaseApi.client.postgrest["bookmarks"]
                            .select {
                                filter {
                                    eq("user_id", uid)
                                    eq("content_id", contentId)
                                }
                            }
                            .decodeList<JsonObject>()
                            .isNotEmpty()
                    } catch (_: Exception) { false }
                } else false

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        content = content,
                        isBookmarked = isBookmarked,
                        error = if (content == null) "Content not found or not visible to you." else null
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Could not load this content.") }
            }
        }
    }

    fun toggleBookmark() {
        val uid = SupabaseApi.client.auth.currentUserOrNull()?.id ?: return
        if (contentId.isBlank()) return
        val currentlyBookmarked = _uiState.value.isBookmarked
        _uiState.update { it.copy(isBookmarked = !currentlyBookmarked) } // optimistic
        viewModelScope.launch {
            try {
                if (currentlyBookmarked) {
                    SupabaseApi.client.postgrest["bookmarks"].delete {
                        filter {
                            eq("user_id", uid)
                            eq("content_id", contentId)
                        }
                    }
                } else {
                    SupabaseApi.client.postgrest["bookmarks"].insert(
                        mapOf("user_id" to uid, "content_id" to contentId)
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isBookmarked = currentlyBookmarked) } // revert
            }
        }
    }

    fun report() {
        val uid = SupabaseApi.client.auth.currentUserOrNull()?.id ?: return
        if (contentId.isBlank() || _uiState.value.reported) return
        viewModelScope.launch {
            try {
                SupabaseApi.client.postgrest["reports"].insert(
                    mapOf(
                        "content_id" to contentId,
                        "reported_by" to uid,
                        "reason" to "Flagged by a community member from the app."
                    )
                )
                _uiState.update { it.copy(reported = true) }
            } catch (_: Exception) {
                // Reporting is fire-and-forget from the UI's perspective
            }
        }
    }
}
