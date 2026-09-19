package com.vulnspace.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.domain.model.Content
import com.vulnspace.app.ui.components.ContentCategory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    init {
        loadFeed()
    }

    fun loadFeed(communityId: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // TODO: Replace with real Supabase query
                // val result = SupabaseApi.client.postgrest["content"]
                //     .select { filter { eq("community_id", communityId); eq("status", "PUBLISHED") } }
                //     .decodeList<Content>()
                delay(800) // Simulate network
                _uiState.update { it.copy(isLoading = false, events = emptyList(), resources = emptyList()) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load feed. Pull to refresh.") }
            }
        }
    }

    fun onSearchChange(query: String) = _uiState.update { it.copy(searchQuery = query) }
    fun onCategorySelect(cat: ContentCategory?) = _uiState.update { it.copy(selectedCategory = cat) }
    fun toggleEventsOnly() = _uiState.update { it.copy(showEventsOnly = !it.showEventsOnly, showResourcesOnly = false) }
    fun toggleResourcesOnly() = _uiState.update { it.copy(showResourcesOnly = !it.showResourcesOnly, showEventsOnly = false) }
    fun refresh() = loadFeed()
}
