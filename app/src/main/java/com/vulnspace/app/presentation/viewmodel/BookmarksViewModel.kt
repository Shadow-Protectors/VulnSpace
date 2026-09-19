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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class BookmarksUiState(
    val all: List<Content> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val showEventsOnly: Boolean = false,
    val showResourcesOnly: Boolean = false
) {
    /** Search + type filters applied client-side. */
    val visible: List<Content>
        get() {
            val q = searchQuery.trim().lowercase()
            return all.filter { c ->
                val matchesQuery = q.isBlank() ||
                    c.title.lowercase().contains(q) ||
                    (c.sourceDomain?.lowercase()?.contains(q) == true)
                val matchesType = when {
                    showEventsOnly -> c.contentType == "EVENT"
                    showResourcesOnly -> c.contentType == "RESOURCE"
                    else -> true
                }
                matchesQuery && matchesType
            }
        }
}

class BookmarksViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(BookmarksUiState())
    val uiState: StateFlow<BookmarksUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val uid = SupabaseApi.client.auth.currentUserOrNull()?.id
                if (uid.isNullOrBlank()) {
                    _uiState.update { it.copy(isLoading = false, all = emptyList()) }
                    return@launch
                }

                val bookmarkRows = SupabaseApi.client.postgrest["bookmarks"]
                    .select { filter { eq("user_id", uid) } }
                    .decodeList<JsonObject>()

                val contentIds = bookmarkRows.mapNotNull { it["content_id"]?.jsonPrimitive?.contentOrNull }

                if (contentIds.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, all = emptyList()) }
                    return@launch
                }

                val contents = SupabaseApi.client.postgrest["content"]
                    .select {
                        filter {
                            isIn("id", contentIds)
                            neq("status", "REMOVED")
                        }
                    }
                    .decodeList<Content>()
                    .sortedByDescending { it.createdAt }
                    .map { c -> c.copy(isBookmarked = true, daysLeft = ContentDates.daysUntil(c.registrationDeadline)) }

                _uiState.update { it.copy(isLoading = false, all = contents) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Could not load bookmarks. Pull down to retry.") }
            }
        }
    }

    fun removeBookmark(contentId: String) {
        val uid = SupabaseApi.client.auth.currentUserOrNull()?.id ?: return
        // Optimistic removal
        val previous = _uiState.value.all
        _uiState.update { state -> state.copy(all = state.all.filterNot { it.id == contentId }) }
        viewModelScope.launch {
            try {
                SupabaseApi.client.postgrest["bookmarks"].delete {
                    filter {
                        eq("user_id", uid)
                        eq("content_id", contentId)
                    }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(all = previous, error = "Could not remove bookmark. Try again.") }
            }
        }
    }

    fun onSearch(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun toggleEvents() = _uiState.update { it.copy(showEventsOnly = !it.showEventsOnly, showResourcesOnly = false) }

    fun toggleResources() = _uiState.update { it.copy(showResourcesOnly = !it.showResourcesOnly, showEventsOnly = false) }
}
