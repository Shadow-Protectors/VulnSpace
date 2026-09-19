package com.vulnspace.app.presentation.navigation

// Sealed class representing all possible app states based on resolved session + role
sealed class SessionState {
    object LoadingSession : SessionState()
    object Unauthenticated : SessionState()
    object AnonymousMemberWithoutCommunity : SessionState()
    data class Member(val userId: String, val communityId: String, val username: String) : SessionState()
    data class CommunityHead(val userId: String, val communityId: String, val username: String) : SessionState()
    data class PlatformAdmin(val userId: String) : SessionState()
    data class PendingHeadApplication(val userId: String, val applicationId: String) : SessionState()
    data class SuspendedUser(val userId: String, val reason: String? = null) : SessionState()
}
