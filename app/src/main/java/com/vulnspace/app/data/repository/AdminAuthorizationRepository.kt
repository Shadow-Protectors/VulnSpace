package com.vulnspace.app.data.repository

import com.vulnspace.app.data.supabase.SupabaseApi
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.JsonObject

/**
 * Repository responsible for verifying platform administrator authorization.
 * All checks are performed server-side using the authenticated user's session.
 */
class AdminAuthorizationRepository {

    /**
     * Checks if the currently authenticated Supabase user has platform admin authorization.
     * Evaluates using the secure is_platform_admin() SQL function, falling back to a direct
     * query on the RLS-protected platform_admins table.
     */
    suspend fun isPlatformAdmin(): Boolean {
        return try {
            val user = SupabaseApi.client.auth.currentUserOrNull() ?: return false

            // Primary check: Call protected RPC function is_platform_admin()
            try {
                val isDefinerAdmin = SupabaseApi.client.postgrest.rpc("is_platform_admin")
                    .decodeAs<Boolean>()
                if (isDefinerAdmin) return true
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Fallback check: Direct select from platform_admins where user_id = auth.uid()
            try {
                val adminRecords = SupabaseApi.client.postgrest["platform_admins"]
                    .select { filter { eq("user_id", user.id) } }
                    .decodeList<JsonObject>()
                if (adminRecords.isNotEmpty()) return true
            } catch (e: Exception) {
                e.printStackTrace()
            }

            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Checks if a specific user ID is registered in platform_admins.
     */
    suspend fun isPlatformAdmin(userId: String): Boolean {
        return try {
            val adminRecords = SupabaseApi.client.postgrest["platform_admins"]
                .select { filter { eq("user_id", userId) } }
                .decodeList<JsonObject>()
            adminRecords.isNotEmpty()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
