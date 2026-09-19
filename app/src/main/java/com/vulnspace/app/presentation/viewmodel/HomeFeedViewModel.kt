package com.vulnspace.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.supabase.SupabaseApi
import com.vulnspace.app.domain.model.Content
import com.vulnspace.app.domain.model.ContentDates
import com.vulnspace.app.ui.components.ContentCategory
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class HomeFeedUiState(
    val isLoading: Boolean = false,
    val events: List<Content> = emptyList(),
    val resources: List<Content> = emptyList(),
    val error: String? = null,
    val searchQuery: String = "",
    val selectedCategory: ContentCategory? = null,
    val showEventsOnly: Boolean = false,
    val showResourcesOnly: Boolean = false,
    val communityName: String = ""
)

class HomeFeedViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeFeedUiState())
    val uiState: StateFlow<HomeFeedUiState> = _uiState.asStateFlow()

    /** Full published feed for the community; filtered copies live in uiState. */
    private var allContent: List<Content> = emptyList()
    private var communityId: String = ""

    fun loadFeed(communityId: String = this.communityId) {
        if (communityId.isBlank()) return
        this.communityId = communityId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Community display name (best-effort)
                val communityName = try {
                    SupabaseApi.client.postgrest["communities"]
                        .select { filter { eq("id", communityId) } }
                        .decodeList<JsonObject>()
                        .firstOrNull()?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
                } catch (_: Exception) { "" }

                // Current user's bookmark ids (best-effort — never blocks the feed)
                val uid = SupabaseApi.client.auth.currentUserOrNull()?.id
                val bookmarkedIds = if (!uid.isNullOrBlank()) {
                    try {
                        SupabaseApi.client.postgrest["bookmarks"]
                            .select { filter { eq("user_id", uid) } }
                            .decodeList<JsonObject>()
                            .mapNotNull { it["content_id"]?.jsonPrimitive?.contentOrNull }
                            .toSet()
                    } catch (_: Exception) { emptySet() }
                } else emptySet()

                // Published content (RLS restricts to own community, published only)
                val rows = SupabaseApi.client.postgrest["content"]
                    .select {
                        filter {
                            eq("community_id", communityId)
                            eq("status", "PUBLISHED")
                        }
                        order("priority", Order.ASCENDING)
                        limit(200)
                    }
                    .decodeList<Content>()

                allContent = rows.map { c ->
                    c.copy(
                        isBookmarked = bookmarkedIds.contains(c.id),
                        daysLeft = ContentDates.daysUntil(c.registrationDeadline)
                    )
                }

                _uiState.update { state ->
                    val (events, resources) = applyFilters(allContent, state)
                    state.copy(
                        isLoading = false,
                        events = events,
                        resources = resources,
                        communityName = communityName.ifBlank { state.communityName }
                    )
                }
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = when {
                            msg.contains("network", true) || msg.contains("connect", true) ->
                                "No connection. Pull to refresh when you're back online."
                            else -> "Failed to load feed. Pull to refresh."
                        }
                    )
                }
            }
        }
    }

    /** Feed never filters server-side for search/category — done here. */
    private fun applyFilters(source: List<Content>, state: HomeFeedUiState): Pair<List<Content>, List<Content>> {
        val q = state.searchQuery.trim().lowercase()
        val filtered = source.filter { c ->
            val matchesQuery = q.isBlank() ||
                c.title.lowercase().contains(q) ||
                (c.description?.lowercase()?.contains(q) == true) ||
                (c.sourceDomain?.lowercase()?.contains(q) == true) ||
                c.tags.any { it.lowercase().contains(q) }
            val matchesCategory = state.selectedCategory == null || c.category == state.selectedCategory.name
            matchesQuery && matchesCategory
        }
        return filtered.partition { it.contentType == "EVENT" }
    }

    private fun refilter() {
        _uiState.update { state ->
            val (events, resources) = applyFilters(allContent, state)
            state.copy(events = events, resources = resources)
        }
    }

    fun toggleBookmark(contentId: String) {
        val uid = SupabaseApi.client.auth.currentUserOrNull()?.id ?: return
        val target = allContent.firstOrNull { it.id == contentId } ?: return
        viewModelScope.launch {
            try {
                if (target.isBookmarked) {
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
                allContent = allContent.map {
                    if (it.id == contentId) it.copy(isBookmarked = !target.isBookmarked) else it
                }
                refilter()
            } catch (_: Exception) {
                // Bookmark toggles are low-stakes — keep the feed quiet on failure
            }
        }
    }

    fun onSearchChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        refilter()
    }

    fun onCategorySelect(cat: ContentCategory?) {
        _uiState.update { it.copy(selectedCategory = cat) }
        refilter()
    }

    fun toggleEventsOnly() {
        _uiState.update { it.copy(showEventsOnly = !it.showEventsOnly, showResourcesOnly = false) }
    }

    fun toggleResourcesOnly() {
        _uiState.update { it.copy(showResourcesOnly = !it.showResourcesOnly, showEventsOnly = false) }
    }

    fun refresh() = loadFeed()
}
