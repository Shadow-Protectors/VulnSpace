package com.vulnspace.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.supabase.SupabaseApi
import com.vulnspace.app.domain.model.AuditLog
import com.vulnspace.app.domain.model.Community
import com.vulnspace.app.domain.model.HeadApplication
import com.vulnspace.app.ui.screens.AdminStats
import com.vulnspace.app.ui.screens.DashboardState
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class AdminViewModel : ViewModel() {

    private val _dashboardState = MutableStateFlow<DashboardState>(DashboardState.Loading)
    val dashboardState: StateFlow<DashboardState> = _dashboardState.asStateFlow()

    private val _stats = MutableStateFlow(AdminStats())
    val stats: StateFlow<AdminStats> = _stats.asStateFlow()

    private val _isStatsLoading = MutableStateFlow(false)
    val isStatsLoading: StateFlow<Boolean> = _isStatsLoading.asStateFlow()

    private val _applications = MutableStateFlow<List<HeadApplication>>(emptyList())
    val applications: StateFlow<List<HeadApplication>> = _applications.asStateFlow()

    private val _isApplicationsLoading = MutableStateFlow(false)
    val isApplicationsLoading: StateFlow<Boolean> = _isApplicationsLoading.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditLog>>(emptyList())
    val auditLogs: StateFlow<List<AuditLog>> = _auditLogs.asStateFlow()

    private val _isAuditLogsLoading = MutableStateFlow(false)
    val isAuditLogsLoading: StateFlow<Boolean> = _isAuditLogsLoading.asStateFlow()

    private val _communities = MutableStateFlow<List<Community>>(emptyList())
    val communities: StateFlow<List<Community>> = _communities.asStateFlow()

    private val _isCommunitiesLoading = MutableStateFlow(false)
    val isCommunitiesLoading: StateFlow<Boolean> = _isCommunitiesLoading.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadDashboardStats()
        subscribeToRealtimeUpdates()
    }

    fun refreshAll() {
        loadDashboardStats()
        loadApplications()
        loadAuditLogs()
        loadCommunities()
    }

    /**
     * Sanitizes errors to strictly prevent exposing URLs, Bearer tokens,
     * JWT access tokens, or internal database secrets, while preserving
     * meaningful server error messages (e.g. from Edge Functions or PostgREST).
     */
    private fun sanitizeErrorMessage(e: Throwable, defaultMessage: String): String {
        val raw = e.message.orEmpty()

        // Extract clean error message from JSON response payload {"error":"..."} or {"message":"..."}
        val serverError = Regex("\"(?:error|message)\"\\s*:\\s*\"([^\"]+)\"")
            .find(raw)?.groupValues?.get(1)

        val target = serverError ?: raw
        val lower = target.lowercase()

        return when {
            // Hide actual secrets/URLs — not harmless words like "token".
            // Edge Function {"error": ...} payloads are operator-curated and safe.
            lower.contains("bearer ") || lower.contains("apikey") ||
                    lower.contains("eyj") || lower.contains("http") ->
                defaultMessage
            lower.contains("permission denied") || lower.contains("42501") ->
                "Access denied: Missing database permissions. Run the latest SQL migration and verify your admin status."
            lower.contains("failed to fetch") || lower.contains("network") ||
                    lower.contains("connect") || lower.contains("timeout") ->
                "Network error: Please check your connection and try again."
            target.isNotBlank() && target.length < 160 && !target.contains("{") ->
                target
            else ->
                defaultMessage
        }
    }

    fun loadDashboardStats() {
        viewModelScope.launch {
            _isStatsLoading.value = true
            _errorMessage.value = null
            _dashboardState.value = DashboardState.Loading

            try {
                android.util.Log.d("AdminViewModel", "Dashboard stats request started")
                val currentUserId = SupabaseApi.client.auth.currentUserOrNull()?.id
                android.util.Log.d("AdminViewModel", "Current authenticated user UUID: $currentUserId")

                // 1. Pending applications count: status = PENDING
                val pendingApps = SupabaseApi.client.postgrest["head_applications"]
                    .select {
                        filter {
                            eq("status", "PENDING")
                        }
                    }
                    .decodeList<JsonObject>()
                val pendingCount = pendingApps.size

                // 2. Active communities count: status = ACTIVE
                val activeCommunities = SupabaseApi.client.postgrest["communities"]
                    .select {
                        filter {
                            eq("status", "ACTIVE")
                        }
                    }
                    .decodeList<JsonObject>()
                val communitiesCount = activeCommunities.size

                // 3. Active Community Heads count: role = COMMUNITY_HEAD (or HEAD) and status = ACTIVE
                val activeHeads = SupabaseApi.client.postgrest["community_members"]
                    .select {
                        filter {
                            isIn("role", listOf("COMMUNITY_HEAD", "HEAD"))
                            eq("status", "ACTIVE")
                        }
                    }
                    .decodeList<JsonObject>()
                val headsCount = activeHeads.size

                // 4. Total active members count: status = ACTIVE
                val allMembers = SupabaseApi.client.postgrest["community_members"]
                    .select {
                        filter {
                            eq("status", "ACTIVE")
                        }
                    }
                    .decodeList<JsonObject>()
                val membersCount = allMembers.size

                val currentStats = AdminStats(
                    pendingApplications = pendingCount,
                    activeCommunities = communitiesCount,
                    activeHeads = headsCount,
                    totalMembers = membersCount
                )
                _stats.value = currentStats

                _dashboardState.value = if (pendingCount == 0 && communitiesCount == 0 && headsCount == 0 && membersCount == 0) {
                    DashboardState.Empty
                } else {
                    DashboardState.Loaded(currentStats)
                }

                android.util.Log.d(
                    "AdminViewModel",
                    "Stats request succeeded: pending=$pendingCount, communities=$communitiesCount, heads=$headsCount, members=$membersCount"
                )
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Stats request failed (sanitized)")
                val safeMessage = sanitizeErrorMessage(e, "Unable to load dashboard statistics. Please try again.")
                _errorMessage.value = safeMessage
                _dashboardState.value = DashboardState.Error(safeMessage)
            } finally {
                _isStatsLoading.value = false
            }
        }
    }

    fun loadApplications() {
        viewModelScope.launch {
            _isApplicationsLoading.value = true
            _errorMessage.value = null
            try {
                android.util.Log.d("AdminViewModel", "Querying pending head_applications...")
                val result = SupabaseApi.client.postgrest["head_applications"]
                    .select {
                        filter {
                            eq("status", "PENDING")
                        }
                    }
                    .decodeList<HeadApplication>()

                android.util.Log.d("AdminViewModel", "Pending applications returned: ${result.size}")
                _applications.value = result.sortedByDescending { it.created_at }
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Applications query failed (sanitized)")
                _errorMessage.value = sanitizeErrorMessage(e, "Unable to load applications. Please try again.")
            } finally {
                _isApplicationsLoading.value = false
            }
        }
    }

    fun loadAuditLogs() {
        viewModelScope.launch {
            _isAuditLogsLoading.value = true
            try {
                val result = SupabaseApi.client.postgrest["audit_logs"]
                    .select()
                    .decodeList<AuditLog>()
                _auditLogs.value = result.sortedByDescending { it.createdAt }
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Audit logs query failed (sanitized)")
                _errorMessage.value = sanitizeErrorMessage(e, "Unable to load audit logs.")
            } finally {
                _isAuditLogsLoading.value = false
            }
        }
    }

    fun loadCommunities() {
        viewModelScope.launch {
            _isCommunitiesLoading.value = true
            try {
                val result = SupabaseApi.client.postgrest["communities"]
                    .select()
                    .decodeList<Community>()
                _communities.value = result.sortedByDescending { it.createdAt }
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Communities query failed (sanitized)")
                _errorMessage.value = sanitizeErrorMessage(e, "Unable to load communities.")
            } finally {
                _isCommunitiesLoading.value = false
            }
        }
    }

    fun approveApplication(applicationId: String) {
        viewModelScope.launch {
            _isApplicationsLoading.value = true
            _actionMessage.value = null
            _errorMessage.value = null
            try {
                android.util.Log.d("AdminViewModel", "Invoking manage-head-application for APPROVE: $applicationId")
                val token = SupabaseApi.client.auth.currentAccessTokenOrNull()
                val response = SupabaseApi.client.functions.invoke(
                    function = "manage-head-application",
                    body = buildJsonObject {
                        put("applicationId", applicationId)
                        put("application_id", applicationId)
                        put("action", "APPROVE")
                    },
                    headers = io.ktor.http.Headers.build {
                        if (!token.isNullOrBlank()) {
                            append(io.ktor.http.HttpHeaders.Authorization, "Bearer $token")
                        }
                    }
                )
                android.util.Log.d("AdminViewModel", "Approval function response received")

                val responseText = try { response.bodyAsText() } catch (_: Exception) { "" }

                // Non-2xx from the function -> surface the server's real message
                if (response.status.value !in 200..299) {
                    val serverMsg = Regex("\"(?:error|message)\"\\s*:\\s*\"([^\"]+)\"")
                        .find(responseText)?.groupValues?.get(1)
                    throw Exception(serverMsg ?: "Server returned HTTP ${response.status.value}")
                }

                // Eagerly remove the item from pending list
                _applications.value = _applications.value.filter { it.id != applicationId }

                // If the email provider is not configured/failed, the function returns the
                // applicant's one-time password to the admin for manual handover.
                val manualOtp = Regex("\"one_time_password\"\\s*:\\s*\"([^\"]+)\"")
                    .find(responseText)?.groupValues?.get(1)

                _actionMessage.value = when {
                    manualOtp != null ->
                        "Approved. Email was NOT delivered — share this one-time password with the applicant: $manualOtp"
                    responseText.contains("\"email_status\":\"FAILED\"", ignoreCase = true) ||
                        responseText.contains("email delivery failed", ignoreCase = true) ->
                        "Application approved, but email delivery failed."
                    else ->
                        "Application approved. Sign-in instructions emailed to the applicant."
                }

                // Refresh all related records fresh from database
                loadApplications()
                loadDashboardStats()
                loadAuditLogs()
                loadCommunities()
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Approve failed: ${e.message}", e)
                _errorMessage.value = sanitizeErrorMessage(e, "Approval failed. Please try again.")
            } finally {
                _isApplicationsLoading.value = false
            }
        }
    }

    fun rejectApplication(applicationId: String, reason: String) {
        viewModelScope.launch {
            _isApplicationsLoading.value = true
            _actionMessage.value = null
            _errorMessage.value = null
            try {
                android.util.Log.d("AdminViewModel", "Invoking manage-head-application for REJECT: $applicationId")
                val token = SupabaseApi.client.auth.currentAccessTokenOrNull()
                val response = SupabaseApi.client.functions.invoke(
                    function = "manage-head-application",
                    body = buildJsonObject {
                        put("applicationId", applicationId)
                        put("application_id", applicationId)
                        put("action", "REJECT")
                        put("reason", reason.trim())
                        put("rejection_reason", reason.trim())
                    },
                    headers = io.ktor.http.Headers.build {
                        if (!token.isNullOrBlank()) {
                            append(io.ktor.http.HttpHeaders.Authorization, "Bearer $token")
                        }
                    }
                )
                android.util.Log.d("AdminViewModel", "Reject function response received")

                if (response.status.value !in 200..299) {
                    val responseText = try { response.bodyAsText() } catch (_: Exception) { "" }
                    val serverMsg = Regex("\"(?:error|message)\"\\s*:\\s*\"([^\"]+)\"")
                        .find(responseText)?.groupValues?.get(1)
                    throw Exception(serverMsg ?: "Server returned HTTP ${response.status.value}")
                }

                _applications.value = _applications.value.filter { it.id != applicationId }
                _actionMessage.value = "Application rejected"

                loadApplications()
                loadDashboardStats()
                loadAuditLogs()
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Reject failed: ${e.message}", e)
                _errorMessage.value = sanitizeErrorMessage(e, "Reject failed. Please try again.")
            } finally {
                _isApplicationsLoading.value = false
            }
        }
    }

    fun suspendCommunity(communityId: String) {
        viewModelScope.launch {
            try {
                SupabaseApi.client.postgrest["communities"]
                    .update(buildJsonObject { put("status", "SUSPENDED") }) {
                        filter { eq("id", communityId) }
                    }
                loadCommunities()
                loadDashboardStats()
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Suspend community failed (sanitized)")
                _errorMessage.value = sanitizeErrorMessage(e, "Failed to update community status.")
            }
        }
    }

    fun reactivateCommunity(communityId: String) {
        viewModelScope.launch {
            try {
                SupabaseApi.client.postgrest["communities"]
                    .update(buildJsonObject { put("status", "ACTIVE") }) {
                        filter { eq("id", communityId) }
                    }
                loadCommunities()
                loadDashboardStats()
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Reactivate community failed (sanitized)")
                _errorMessage.value = sanitizeErrorMessage(e, "Failed to update community status.")
            }
        }
    }

    private fun subscribeToRealtimeUpdates() {
        viewModelScope.launch {
            try {
                val user = SupabaseApi.client.auth.currentUserOrNull()
                if (user != null) {
                    val channel = SupabaseApi.client.realtime.channel("admin-dashboard-channel")
                    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "head_applications"
                    }.collect {
                        loadDashboardStats()
                        loadApplications()
                        loadAuditLogs()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("AdminViewModel", "Realtime subscription inactive or disconnected")
            }
        }
    }
}
