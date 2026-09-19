package com.vulnspace.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.supabase.SupabaseApi
import com.vulnspace.app.domain.model.AppNotification
import com.vulnspace.app.domain.model.ContentDates
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val notifications: List<AppNotification> = emptyList(),
    val isLoading: Boolean = false
)

class NotificationsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private var loadedOnce = false

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val result = SupabaseApi.client.postgrest["notifications"]
                    .select()
                    .decodeList<AppNotification>()
                    .sortedByDescending { it.createdAt }

                _uiState.update { it.copy(isLoading = false, notifications = result) }

                // Mark personal alerts as read the first time the inbox is opened
                if (!loadedOnce) {
                    loadedOnce = true
                    markAllRead(result)
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /** Sets read_at on the recipient's own unread alerts (RLS-scoped policy). */
    private suspend fun markAllRead(current: List<AppNotification>) {
        val uid = SupabaseApi.client.auth.currentUserOrNull()?.id ?: return
        val unreadIds = current
            .filter { !it.isRead && it.userId.isNotBlank() }
            .map { it.id }
        if (unreadIds.isEmpty()) return
        try {
            val now = ContentDates.nowIso()
            SupabaseApi.client.postgrest["notifications"]
                .update(mapOf("read_at" to now)) {
                    filter { isIn("id", unreadIds) }
                }
            _uiState.update { state ->
                state.copy(
                    notifications = state.notifications.map { n ->
                        if (unreadIds.contains(n.id)) n.copy(readAt = now) else n
                    }
                )
            }
        } catch (_: Exception) {
            // Read receipts are cosmetic — ignore failures
        }
    }
}
