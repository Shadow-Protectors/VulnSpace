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
                
                val email = session.user?.email ?: ""

                // 0. Check if user must change password (for Community Heads on first login)
                try {
                    val profileResult = SupabaseApi.client.postgrest["profiles"]
                        .select { filter { eq("id", userId) } }
                        .decodeList<JsonObject>()
                        
                    if (profileResult.isNotEmpty()) {
                        val profile = profileResult.first()
                        val mustChange = profile["must_change_password"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                        if (mustChange) {
                            _sessionState.value = SessionState.MustChangePassword(userId = userId, email = email)
                            return@launch
                        }
                    }
                } catch (_: Exception) {
                    // Ignore profile read errors for admin accounts that may not have a profile row
                }

                // 1. Check if platform admin (most privileged — checked first)
                val isPlatformAdmin = try {
                    com.vulnspace.app.data.repository.AdminAuthorizationRepository().isPlatformAdmin()
                } catch (e: Exception) {
                    false
                }

                if (isPlatformAdmin) {
                    _sessionState.value = SessionState.PlatformAdmin(userId = userId)
                    return@launch
                }

                // 2. Check community membership
                val memberResult = SupabaseApi.client.postgrest["community_members"]
                    .select { filter { eq("user_id", userId) } }
                    .decodeList<JsonObject>()

                if (memberResult.isEmpty()) {
                    // 3. Authenticated but not in a community. Check if they have a Head Application.
                    val appResult = SupabaseApi.client.postgrest["head_applications"]
                        .select { filter { eq("applicant_user_id", userId) } }
                        .decodeList<JsonObject>()
                        
                    if (appResult.isNotEmpty()) {
                        val appRow = appResult.first()
                        val status = appRow["status"]?.jsonPrimitive?.content ?: "PENDING"
                        
                        when (status) {
                            "PENDING" -> _sessionState.value = SessionState.HeadApplicationPending
                            "REJECTED" -> _sessionState.value = SessionState.HeadApplicationRejected
                            // Note: APPROVED without membership is a transient state before community is fully set up,
                            // or they haven't logged in with the temp credential yet.
                            // If they are logged in and must change password, we already caught it in step 0.
                            // If they are somehow here, they have no membership.
                            else -> _sessionState.value = SessionState.AnonymousMemberWithoutCommunity
                        }
                        return@launch
                    }
                    
                    // Otherwise, just a regular authenticated user without a community
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

                // 4. Check if community head (supports both HEAD and COMMUNITY_HEAD)
                if (role == "COMMUNITY_HEAD" || role == "HEAD") {
                    _sessionState.value = SessionState.CommunityHead(
                        userId = userId,
                        communityId = communityId,
                        username = username
                    )
                    return@launch
                }

                // 5. Regular member
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
