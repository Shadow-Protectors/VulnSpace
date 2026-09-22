package com.vulnspace.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.supabase.SupabaseApi
import com.vulnspace.app.domain.model.AppNotification
import com.vulnspace.app.domain.model.ContentDates
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val notifications: List<AppNotification> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val unreadCount: Int = 0
)

class NotificationsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        load()
        subscribeToRealtime()
    }

    fun load() {
        val uid = SupabaseApi.client.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val result = SupabaseApi.client.postgrest["notifications"]
                    .select {
                        filter {
                            eq("recipient_user_id", uid)
                        }
                    }
                    .decodeList<AppNotification>()
                    .sortedByDescending { it.createdAt }

                val unread = result.count { !it.isRead }
                _uiState.update { it.copy(isLoading = false, notifications = result, unreadCount = unread) }
            } catch (e: Exception) {
                android.util.Log.e("NotificationsViewModel", "Failed to load notifications: ${e.message}", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = "Unable to load alerts. Tap refresh to try again.") }
            }
        }
    }

    fun markRead(notificationId: String) {
        val uid = SupabaseApi.client.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            try {
                val now = ContentDates.nowIso()
                SupabaseApi.client.postgrest["notifications"]
                    .update(mapOf("read_at" to now)) {
                        filter {
                            eq("id", notificationId)
                            eq("recipient_user_id", uid)
                        }
                    }
                _uiState.update { state ->
                    val updatedList = state.notifications.map { n ->
                        if (n.id == notificationId) n.copy(readAt = now) else n
                    }
                    state.copy(
                        notifications = updatedList,
                        unreadCount = updatedList.count { !it.isRead }
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("NotificationsViewModel", "Mark read failed: ${e.message}")
            }
        }
    }

    fun markAllRead() {
        val uid = SupabaseApi.client.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            val unreadIds = _uiState.value.notifications
                .filter { !it.isRead }
                .map { it.id }
            if (unreadIds.isEmpty()) return@launch

            try {
                val now = ContentDates.nowIso()
                SupabaseApi.client.postgrest["notifications"]
                    .update(mapOf("read_at" to now)) {
                        filter {
                            isIn("id", unreadIds)
                            eq("recipient_user_id", uid)
                        }
                    }
                _uiState.update { state ->
                    val updatedList = state.notifications.map { n ->
                        if (unreadIds.contains(n.id)) n.copy(readAt = now) else n
                    }
                    state.copy(
                        notifications = updatedList,
                        unreadCount = 0
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("NotificationsViewModel", "Mark all read failed: ${e.message}")
            }
        }
    }

    private fun subscribeToRealtime() {
        viewModelScope.launch {
            try {
                val uid = SupabaseApi.client.auth.currentUserOrNull()?.id ?: return@launch
                val channel = SupabaseApi.client.realtime.channel("user-notifications-$uid")
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "notifications"
                }.collect {
                    // Reload when notifications table changes for current user
                    load()
                }
            } catch (e: Exception) {
                android.util.Log.w("NotificationsViewModel", "Realtime channel inactive: ${e.message}")
            }
        }
    }
}
