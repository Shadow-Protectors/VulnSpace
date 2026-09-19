package com.vulnspace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vulnspace.app.domain.model.Content
import com.vulnspace.app.ui.components.*
import com.vulnspace.app.ui.theme.*

@Composable
fun BookmarksScreen(
    bookmarks: List<Content>,
    isLoading: Boolean,
    error: String?,
    searchQuery: String,
    onSearch: (String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onContentClick: (String) -> Unit,
    showEventsOnly: Boolean,
    showResourcesOnly: Boolean,
    onToggleEvents: () -> Unit,
    onToggleResources: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column {
            CyberTopBar(title = "Saved", subtitle = "${bookmarks.size} bookmarks")
            HorizontalDivider(color = BlueBorder)

            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Column(modifier = Modifier.padding(16.dp).background(WhiteSurface)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearch,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search bookmarks", color = TextDisabled) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = TextSecondary) },
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = showEventsOnly,
                                onClick = onToggleEvents,
                                label = { Text("Events") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LightBlue, selectedLabelColor = PrimaryBlue)
                            )
                            FilterChip(
                                selected = showResourcesOnly,
                                onClick = onToggleResources,
                                label = { Text("Resources") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LightBlue, selectedLabelColor = PrimaryBlue)
                            )
                        }
                    }
                    HorizontalDivider(color = BlueBorder)
                }

                if (isLoading) { item { LoadingState() }; return@LazyColumn }
                if (error != null) { item { ErrorState(error) }; return@LazyColumn }
                if (bookmarks.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.Bookmark,
                            title = "No bookmarks yet",
                            message = "Tap the bookmark icon on any event or resource to save it here."
                        )
                    }
                    return@LazyColumn
                }

                items(bookmarks, key = { it.id }) { content ->
                    if (content.contentType == "EVENT") {
                        val cat = try { ContentCategory.valueOf(content.category) } catch (_: Exception) { ContentCategory.CTF }
                        EventCard(
                            title = content.title,
                            category = cat,
                            organizer = content.organizer,
                            daysLeft = content.daysLeft,
                            safetyStatus = content.safetyStatus ?: "UNKNOWN",
                            isBookmarked = true,
                            onBookmark = { onRemoveBookmark(content.id) },
                            onClick = { onContentClick(content.id) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    } else {
                        val cat = try { ContentCategory.valueOf(content.category) } catch (_: Exception) { ContentCategory.OTHER_RESOURCE }
                        ResourceCard(
                            title = content.title,
                            category = cat,
                            description = content.description,
                            sourceDomain = content.sourceDomain ?: "",
                            safetyStatus = content.safetyStatus ?: "UNKNOWN",
                            isBookmarked = true,
                            onBookmark = { onRemoveBookmark(content.id) },
                            onClick = { onContentClick(content.id) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
