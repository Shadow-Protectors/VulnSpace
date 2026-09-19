package com.vulnspace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.vulnspace.app.domain.model.Content
import com.vulnspace.app.presentation.viewmodel.HomeFeedUiState
import com.vulnspace.app.ui.components.*
import com.vulnspace.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    state: HomeFeedUiState,
    onSearch: (String) -> Unit,
    onCategorySelect: (ContentCategory?) -> Unit,
    onToggleEvents: () -> Unit,
    onToggleResources: () -> Unit,
    onRefresh: () -> Unit,
    onContentClick: (String) -> Unit,
    onBookmark: (String) -> Unit,
    communityName: String
) {
    val pullState = rememberPullToRefreshState()
    if (pullState.isRefreshing) {
        LaunchedEffect(Unit) {
            onRefresh()
            pullState.endRefresh()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .nestedScroll(pullState.nestedScrollConnection)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier
                        .background(WhiteSurface)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(communityName.ifBlank { "Community" }, style = MaterialTheme.typography.titleMedium, color = PrimaryBlue)
                    }
                    Spacer(Modifier.height(10.dp))
                    // Search
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearch,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search events & resources", color = TextDisabled) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = TextSecondary) },
                        trailingIcon = if (state.searchQuery.isNotBlank()) {
                            { IconButton(onClick = { onSearch("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = TextSecondary) } }
                        } else null,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = BlueBorder,
                            focusedContainerColor = WhiteSurface,
                            unfocusedContainerColor = WhiteSurface
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    // Filter chips
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.showEventsOnly,
                            onClick = onToggleEvents,
                            label = { Text("Events") },
                            leadingIcon = if (state.showEventsOnly) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LightBlue,
                                selectedLabelColor = PrimaryBlue
                            )
                        )
                        FilterChip(
                            selected = state.showResourcesOnly,
                            onClick = onToggleResources,
                            label = { Text("Resources") },
                            leadingIcon = if (state.showResourcesOnly) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LightBlue,
                                selectedLabelColor = PrimaryBlue
                            )
                        )
                        ContentCategory.values().forEach { cat ->
                            FilterChip(
                                selected = state.selectedCategory == cat,
                                onClick = { onCategorySelect(if (state.selectedCategory == cat) null else cat) },
                                label = { Text(cat.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LightBlue,
                                    selectedLabelColor = PrimaryBlue
                                )
                            )
                        }
                    }
                }
                HorizontalDivider(color = BlueBorder, thickness = 1.dp)
            }

            // Loading
            if (state.isLoading) {
                item { LoadingState() }
                return@LazyColumn
            }

            // Error
            if (state.error != null) {
                item { ErrorState(message = state.error, onRetry = onRefresh) }
                return@LazyColumn
            }

            // Events section
            if (!state.showResourcesOnly && state.events.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Upcoming Events",
                        icon = Icons.Filled.Event,
                        count = state.events.size
                    )
                }
                items(state.events, key = { it.id }) { content ->
                    EventCardItem(content, onContentClick, onBookmark)
                }
            }

            // Resources section
            if (!state.showEventsOnly && state.resources.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Resources",
                        icon = Icons.Filled.MenuBook,
                        count = state.resources.size
                    )
                }
                items(state.resources, key = { it.id }) { content ->
                    ResourceCardItem(content, onContentClick, onBookmark)
                }
            }

            // Empty
            if (state.events.isEmpty() && state.resources.isEmpty() && !state.isLoading) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Info,
                        title = "Nothing here yet",
                        message = "Be the first to submit a CTF, hackathon, or resource link to your community.",
                        action = null
                    )
                }
            }
        }

        PullToRefreshContainer(
            state = pullState,
            modifier = Modifier.align(Alignment.TopCenter),
            containerColor = WhiteSurface,
            contentColor = PrimaryBlue
        )
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}

@Composable
private fun EventCardItem(content: Content, onContentClick: (String) -> Unit, onBookmark: (String) -> Unit) {
    val category = try { ContentCategory.valueOf(content.category) } catch (_: Exception) { ContentCategory.CTF }
    EventCard(
        title = content.title,
        category = category,
        organizer = content.organizer,
        daysLeft = content.daysLeft,
        safetyStatus = content.safetyStatus ?: "UNKNOWN",
        isBookmarked = content.isBookmarked,
        onBookmark = { onBookmark(content.id) },
        onClick = { onContentClick(content.id) },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun ResourceCardItem(content: Content, onContentClick: (String) -> Unit, onBookmark: (String) -> Unit) {
    val category = try { ContentCategory.valueOf(content.category) } catch (_: Exception) { ContentCategory.OTHER_RESOURCE }
    ResourceCard(
        title = content.title,
        category = category,
        description = content.description,
        sourceDomain = content.sourceDomain ?: "",
        safetyStatus = content.safetyStatus ?: "UNKNOWN",
        isBookmarked = content.isBookmarked,
        onBookmark = { onBookmark(content.id) },
        onClick = { onContentClick(content.id) },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}
