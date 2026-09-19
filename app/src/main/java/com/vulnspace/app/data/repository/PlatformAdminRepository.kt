package com.vulnspace.app.data.repository

import com.vulnspace.app.data.supabase.SupabaseApi
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email

/**
 * Repository responsible for Platform Admin authentication and session management.
 * Verifies credentials against Supabase Auth and checks the platform_admins table before granting access.
 */
class PlatformAdminRepository(
    private val adminAuthRepository: AdminAuthorizationRepository = AdminAuthorizationRepository()
) {

    /**
     * Signs in with email and password via Supabase Auth, then verifies the user's UUID in platform_admins.
     * If the account is valid in Supabase Auth but lacks the platform admin role, signs out immediately
     * and returns a SecurityException.
     */
    suspend fun signInAsAdmin(email: String, password: String): Result<String> {
        return try {
            // 1. Authenticate with Supabase Auth
            SupabaseApi.client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            val user = SupabaseApi.client.auth.currentUserOrNull()
                ?: throw IllegalStateException("Authentication failed: No user session found.")

            // 2. Perform protected server-side authorization check
            val isAdmin = adminAuthRepository.isPlatformAdmin()
            if (!isAdmin) {
                // Deny access and sign out immediately
                try {
                    SupabaseApi.client.auth.signOut()
                } catch (_: Exception) { }
                return Result.failure(SecurityException("This account does not have platform administrator access"))
            }

            Result.success(user.id)
        } catch (se: SecurityException) {
            Result.failure(se)
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            val friendlyMessage = if (msg.contains("invalid login") || msg.contains("invalid credential") || msg.contains("user not found")) {
                "Invalid email or password"
            } else {
                e.message ?: "Authentication failed"
            }
            Result.failure(Exception(friendlyMessage))
        }
    }

    /**
     * Signs out the current admin user and clears the Supabase session.
     */
    suspend fun signOut() {
        try {
            SupabaseApi.client.auth.signOut()
        } catch (_: Exception) { }
    }
}
