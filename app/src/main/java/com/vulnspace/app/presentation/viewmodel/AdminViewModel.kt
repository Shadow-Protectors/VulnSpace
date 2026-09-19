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
     * JWT access tokens, or internal database exception details in UI or logs.
     */
    private fun sanitizeErrorMessage(e: Throwable, defaultMessage: String): String {
        val raw = e.message.orEmpty()
        val lower = raw.lowercase()
        return when {
            lower.contains("permission denied") || lower.contains("42501") ->
                "Access denied: Missing database permissions. Please verify platform admin status."
            lower.contains("network") || lower.contains("connect") || lower.contains("timeout") ->
                "Network error: Please check your connection and try again."
            lower.contains("bearer") || lower.contains("authorization") || lower.contains("apikey") ||
                    lower.contains("jwt") || lower.contains("token") || lower.contains("https://") ->
                defaultMessage
            raw.isNotBlank() && raw.length < 90 && !raw.contains("{") && !raw.contains("http") ->
                raw
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
                val response = SupabaseApi.client.functions.invoke(
                    "manage-head-application",
                    buildJsonObject {
                        put("applicationId", applicationId)
                        put("application_id", applicationId)
                        put("action", "APPROVE")
                    }
                )
                android.util.Log.d("AdminViewModel", "Approval function response received")

                // Eagerly remove the item from pending list
                _applications.value = _applications.value.filter { it.id != applicationId }

                // Check for email delivery warning in payload if returned
                val responseText = try { response.bodyAsText() } catch (_: Exception) { "" }
                if (responseText.contains("email delivery failed", ignoreCase = true) ||
                    responseText.contains("\"email_status\":\"FAILED\"", ignoreCase = true)
                ) {
                    _actionMessage.value = "Application approved, but email delivery failed."
                } else {
                    _actionMessage.value = "Application approved successfully"
                }

                // Refresh all related records fresh from database
                loadApplications()
                loadDashboardStats()
                loadAuditLogs()
                loadCommunities()
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Approve failed (sanitized)")
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
                SupabaseApi.client.functions.invoke(
                    "manage-head-application",
                    buildJsonObject {
                        put("applicationId", applicationId)
                        put("application_id", applicationId)
                        put("action", "REJECT")
                        put("reason", reason.trim())
                        put("rejection_reason", reason.trim())
                    }
                )
                android.util.Log.d("AdminViewModel", "Reject function response received")

                _applications.value = _applications.value.filter { it.id != applicationId }
                _actionMessage.value = "Application rejected"

                loadApplications()
                loadDashboardStats()
                loadAuditLogs()
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Reject failed (sanitized)")
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
