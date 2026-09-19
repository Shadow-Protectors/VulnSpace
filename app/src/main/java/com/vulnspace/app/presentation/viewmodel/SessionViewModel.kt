package com.vulnspace.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.supabase.SupabaseApi
import com.vulnspace.app.presentation.navigation.SessionState
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class SessionViewModel : ViewModel() {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.LoadingSession)
    val sessionState: StateFlow<SessionState> = _sessionState

    init {
        resolveSession()
    }

    fun resolveSession() {
        viewModelScope.launch {
            _sessionState.value = SessionState.LoadingSession
            try {
                val session = SupabaseApi.client.auth.currentSessionOrNull()
                if (session == null) {
                    _sessionState.value = SessionState.Unauthenticated
                    return@launch
                }
                val userId = session.user?.id ?: run {
                    _sessionState.value = SessionState.Unauthenticated
                    return@launch
                }

                // 1. Check if platform admin (most privileged — checked first)
                val adminResult = SupabaseApi.client.postgrest["platform_admins"]
                    .select { filter { eq("user_id", userId) } }
                    .decodeList<JsonObject>()

                if (adminResult.isNotEmpty()) {
                    _sessionState.value = SessionState.PlatformAdmin(userId = userId)
                    return@launch
                }

                // 2. Check community membership
                val memberResult = SupabaseApi.client.postgrest["community_members"]
                    .select { filter { eq("user_id", userId) } }
                    .decodeList<JsonObject>()

                if (memberResult.isEmpty()) {
                    // Authenticated but not in any community
                    _sessionState.value = SessionState.AnonymousMemberWithoutCommunity
                    return@launch
                }

                val memberRow = memberResult.first()
                val communityId = memberRow["community_id"]?.jsonPrimitive?.content ?: ""
                val username = memberRow["username"]?.jsonPrimitive?.content ?: ""
                val role = memberRow["role"]?.jsonPrimitive?.content ?: "MEMBER"
                val status = memberRow["status"]?.jsonPrimitive?.content ?: "ACTIVE"

                if (status == "SUSPENDED") {
                    _sessionState.value = SessionState.SuspendedUser(userId = userId)
                    return@launch
                }

                // 3. Check if community head
                if (role == "COMMUNITY_HEAD") {
                    _sessionState.value = SessionState.CommunityHead(
                        userId = userId,
                        communityId = communityId,
                        username = username
                    )
                    return@launch
                }

                // 4. Regular member
                _sessionState.value = SessionState.Member(
                    userId = userId,
                    communityId = communityId,
                    username = username
                )

            } catch (e: Exception) {
                e.printStackTrace()
                // Resolve as unauthenticated on error to keep the user in a safe state
                _sessionState.value = SessionState.Unauthenticated
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                SupabaseApi.client.auth.signOut()
            } catch (_: Exception) { }
            _sessionState.value = SessionState.Unauthenticated
        }
    }
}
