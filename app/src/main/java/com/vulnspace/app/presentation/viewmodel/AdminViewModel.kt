package com.vulnspace.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.supabase.SupabaseApi
import com.vulnspace.app.domain.model.AuditLog
import com.vulnspace.app.domain.model.Community
import com.vulnspace.app.domain.model.HeadApplication
import com.vulnspace.app.ui.screens.AdminStats
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AdminViewModel : ViewModel() {

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

    fun loadDashboardStats() {
        viewModelScope.launch {
            _isStatsLoading.value = true
            _errorMessage.value = null
            try {
                android.util.Log.d("AdminViewModel", "Fetching dashboard stats from Supabase...")
                // 1. Pending applications count
                val pendingApps = SupabaseApi.client.postgrest["head_applications"]
                    .select {
                        filter {
                            eq("status", "PENDING")
                        }
                    }
                    .decodeList<JsonObject>()
                val pendingCount = pendingApps.size
                android.util.Log.d("AdminViewModel", "Fetched pending applications count: $pendingCount")

                // 2. Active communities count
                val activeCommunities = SupabaseApi.client.postgrest["communities"]
                    .select {
                        filter {
                            eq("status", "ACTIVE")
                        }
                    }
                    .decodeList<JsonObject>()
                val communitiesCount = activeCommunities.size

                // 3. Active Community Heads count
                val activeHeads = SupabaseApi.client.postgrest["community_members"]
                    .select {
                        filter {
                            isIn("role", listOf("COMMUNITY_HEAD", "HEAD"))
                            eq("status", "ACTIVE")
                        }
                    }
                    .decodeList<JsonObject>()
                val headsCount = activeHeads.size

                // 4. Total active members count
                val allMembers = SupabaseApi.client.postgrest["community_members"]
                    .select {
                        filter {
                            eq("status", "ACTIVE")
                        }
                    }
                    .decodeList<JsonObject>()
                val membersCount = allMembers.size

                _stats.value = AdminStats(
                    pendingApplications = pendingCount,
                    activeCommunities = communitiesCount,
                    activeHeads = headsCount,
                    totalMembers = membersCount
                )
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Dashboard stats query error: ${e.message}", e)
                _errorMessage.value = "Stats query failed: ${e.message}"
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
                android.util.Log.e("AdminViewModel", "Applications query error: ${e.message}", e)
                _errorMessage.value = "Applications query failed: ${e.message}"
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
                android.util.Log.e("AdminViewModel", "Audit logs query error: ${e.message}", e)
                _errorMessage.value = "Audit logs error: ${e.message}"
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
                e.printStackTrace()
                _errorMessage.value = "Failed to load communities"
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
                        put("application_id", applicationId)
                        put("action", "APPROVE")
                    }
                )
                android.util.Log.d("AdminViewModel", "Approval function response: $response")
                _actionMessage.value = "Application approved successfully"
                // Refresh all related views immediately
                loadApplications()
                loadDashboardStats()
                loadAuditLogs()
                loadCommunities()
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Approve failed: ${e.message}", e)
                _errorMessage.value = "Approval failed: ${e.message}"
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
                val response = SupabaseApi.client.functions.invoke(
                    "manage-head-application",
                    buildJsonObject {
                        put("application_id", applicationId)
                        put("action", "REJECT")
                        put("rejection_reason", reason)
                    }
                )
                android.util.Log.d("AdminViewModel", "Reject response: $response")
                _actionMessage.value = "Application rejected"
                loadApplications()
                loadDashboardStats()
                loadAuditLogs()
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Reject failed: ${e.message}", e)
                _errorMessage.value = "Reject failed: ${e.message}"
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
                e.printStackTrace()
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
                e.printStackTrace()
            }
        }
    }

    private fun subscribeToRealtimeUpdates() {
        viewModelScope.launch {
            try {
                val channel = SupabaseApi.client.realtime.channel("admin-dashboard-channel")
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "head_applications"
                }.collect {
                    loadDashboardStats()
                    loadApplications()
                }
            } catch (e: Exception) {
                // Realtime subscription is best-effort and should not crash the ViewModel
                e.printStackTrace()
            }
        }
    }
}
