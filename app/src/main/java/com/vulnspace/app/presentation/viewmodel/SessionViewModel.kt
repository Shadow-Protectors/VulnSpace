package com.vulnspace.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.supabase.SupabaseApi
import com.vulnspace.app.presentation.navigation.SessionState
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class SessionViewModel : ViewModel() {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.LoadingSession)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

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

                // 2. Check community membership (active only)
                var memberResult = SupabaseApi.client.postgrest["community_members"]
                    .select { filter { eq("user_id", userId); eq("status", "ACTIVE") } }
                    .decodeList<JsonObject>()

                // 3. If no active community membership, check for an unclaimed grant via Edge Function
                if (memberResult.isEmpty()) {
                    try {
                        val claimResponse = SupabaseApi.client.functions.invoke(
                            function = "claim-head-grant"
                        )
                        val claimText = claimResponse.bodyAsText()
                        val isClaimed = claimText.contains("\"claimed\":true")
                        val alreadyHead = claimText.contains("\"already_head\":true")
                        val isPasswordConfigured = claimText.contains("\"password_configured\":true")

                        if (isClaimed || alreadyHead) {
                            if (!isPasswordConfigured) {
                                _sessionState.value = SessionState.MustChangePassword(userId = userId, email = email)
                                return@launch
                            }
                            // Re-fetch membership after claim
                            memberResult = SupabaseApi.client.postgrest["community_members"]
                                .select { filter { eq("user_id", userId); eq("status", "ACTIVE") } }
                                .decodeList<JsonObject>()
                        }
                    } catch (e: Exception) {
                        android.util.Log.d("SessionViewModel", "claim-head-grant check: ${e.message}")
                    }
                }

                if (memberResult.isEmpty()) {
                    // 4. Authenticated but not in a community. Check if they have a legacy Head Application.
                    val appResult = SupabaseApi.client.postgrest["head_applications"]
                        .select { filter { eq("applicant_user_id", userId) } }
                        .decodeList<JsonObject>()
                        
                    if (appResult.isNotEmpty()) {
                        val appRow = appResult.first()
                        val status = appRow["status"]?.jsonPrimitive?.content ?: "PENDING"
                        
                        when (status) {
                            "PENDING" -> _sessionState.value = SessionState.HeadApplicationPending
                            "REJECTED" -> _sessionState.value = SessionState.HeadApplicationRejected
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

                // 5. Check if community head
                if (role == "COMMUNITY_HEAD" || role == "HEAD") {
                    // Check if password_configured is false for this Community Head
                    try {
                        val profileResult = SupabaseApi.client.postgrest["profiles"]
                            .select { filter { eq("id", userId) } }
                            .decodeList<JsonObject>()

                        if (profileResult.isNotEmpty()) {
                            val profile = profileResult.first()
                            val isPasswordConfigured = profile["password_configured"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                            if (!isPasswordConfigured) {
                                _sessionState.value = SessionState.MustChangePassword(userId = userId, email = email)
                                return@launch
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore profile read errors
                    }

                    _sessionState.value = SessionState.CommunityHead(
                        userId = userId,
                        communityId = communityId,
                        username = username
                    )
                    return@launch
                }

                // 6. Regular member
                _sessionState.value = SessionState.Member(
                    userId = userId,
                    communityId = communityId,
                    username = username
                )

            } catch (e: Exception) {
                e.printStackTrace()
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
