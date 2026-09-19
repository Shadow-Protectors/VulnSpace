package com.vulnspace.app.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vulnspace.app.domain.model.*
import com.vulnspace.app.presentation.viewmodel.AdminViewModel
import com.vulnspace.app.presentation.viewmodel.CommunitySetupViewModel
import com.vulnspace.app.presentation.viewmodel.HeadConsoleViewModel
import com.vulnspace.app.presentation.viewmodel.*
import com.vulnspace.app.data.supabase.SupabaseApi
import io.github.jan.supabase.gotrue.auth
import com.vulnspace.app.ui.components.BottomNavigationBar
import com.vulnspace.app.ui.screens.*
import com.vulnspace.app.ui.theme.PrimaryBlue

@Composable
fun VulnSpaceNavGraph(
    sessionViewModel: SessionViewModel = viewModel()
) {
    val sessionState by sessionViewModel.sessionState.collectAsStateWithLifecycle()

    when (val state = sessionState) {
        is SessionState.LoadingSession -> {
            // Full-screen loading while resolving role from Supabase
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
        }

        is SessionState.Unauthenticated,
        is SessionState.AnonymousMemberWithoutCommunity -> {
            AuthNavGraph(onSessionResolved = { sessionViewModel.resolveSession() })
        }

        is SessionState.HeadApplicationPending,
        is SessionState.PendingHeadApplication -> {
            AuthNavGraph(
                onSessionResolved = { sessionViewModel.resolveSession() },
                startDestination = Destinations.HEAD_APPLICATION_PENDING
            )
        }

        is SessionState.HeadApplicationRejected -> {
            AuthNavGraph(
                onSessionResolved = { sessionViewModel.resolveSession() },
                startDestination = Destinations.HEAD_APPLICATION_REJECTED
            )
        }

        is SessionState.HeadApprovedSetupRequired -> {
            AuthNavGraph(
                onSessionResolved = { sessionViewModel.resolveSession() },
                startDestination = Destinations.COMMUNITY_SETUP
            )
        }

        is SessionState.MustChangePassword -> {
            AuthNavGraph(
                onSessionResolved = { sessionViewModel.resolveSession() },
                startDestination = Destinations.CREATE_NEW_PASSWORD
            )
        }

        is SessionState.Member -> {
            MemberNavGraph(
                userId = state.userId,
                communityId = state.communityId,
                username = state.username,
                role = "MEMBER",
                onSignOut = { sessionViewModel.signOut() }
            )
        }

        is SessionState.CommunityHead -> {
            MemberNavGraph(
                userId = state.userId,
                communityId = state.communityId,
                username = state.username,
                role = "COMMUNITY_HEAD",
                onSignOut = { sessionViewModel.signOut() }
            )
        }

        is SessionState.PlatformAdmin -> {
            AdminNavGraph(
                userId = state.userId,
                onSignOut = { sessionViewModel.signOut() }
            )
        }

        is SessionState.SuspendedUser -> {
            SuspendedUserScreen(
                reason = state.reason,
                onSignOut = { sessionViewModel.signOut() }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Auth flow — shown to unauthenticated users
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AuthNavGraph(
    onSessionResolved: () -> Unit,
    startDestination: String = Destinations.WELCOME
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Destinations.WELCOME) {
            WelcomeScreen(
                onJoinCommunity = { navController.navigate(Destinations.JOIN_COMMUNITY) },
                onApplyAsHead = { navController.navigate(Destinations.HEAD_APPLICATION_FORM) },
                onHeadLogin = { navController.navigate(Destinations.COMMUNITY_HEAD_LOGIN) },
                onAdminLogin = { navController.navigate(Destinations.PLATFORM_ADMIN_LOGIN) }
            )
        }
        composable(Destinations.PLATFORM_ADMIN_LOGIN) {
            val adminLoginVm: PlatformAdminLoginViewModel = viewModel()
            val adminLoginState by adminLoginVm.uiState.collectAsStateWithLifecycle()
            PlatformAdminLoginScreen(
                state = adminLoginState,
                onEmailChange = adminLoginVm::onEmailChange,
                onPasswordChange = adminLoginVm::onPasswordChange,
                onSubmit = {
                    adminLoginVm.signInAsAdmin(onSuccess = onSessionResolved)
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Destinations.SIGN_IN) {
            val signInVm: SignInViewModel = viewModel()
            val signInState by signInVm.uiState.collectAsStateWithLifecycle()
            SignInScreen(
                state = signInState,
                onEmailChange = signInVm::onEmailChange,
                onPasswordChange = signInVm::onPasswordChange,
                onSubmit = {
                    signInVm.signIn(onSuccess = onSessionResolved)
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Destinations.JOIN_COMMUNITY) {
            var joinState by remember { mutableStateOf(JoinState()) }
            JoinCommunityScreen(
                state = joinState,
                onCodeChange = { joinState = joinState.copy(code = it, errorMessage = null) },
                onJoin = {
                    // On valid join → navigate to username setup
                    navController.navigate(Destinations.usernameSetup(joinState.code))
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Destinations.USERNAME_SETUP) { backStackEntry ->
            val inviteCode = backStackEntry.arguments?.getString("inviteCode") ?: ""
            var username by remember { mutableStateOf("") }
            val vm: UsernameSetupViewModel = viewModel()
            val isLoading by vm.isLoading.collectAsStateWithLifecycle()
            val error by vm.error.collectAsStateWithLifecycle()
            
            UsernameSetupScreen(
                inviteCode = inviteCode,
                username = username,
                onUsernameChange = { username = it },
                isLoading = isLoading,
                errorMessage = error,
                onSubmit = {
                    vm.submit(inviteCode, username, onSuccess = onSessionResolved)
                }
            )
        }
        composable(Destinations.HEAD_APPLICATION_FORM) {
            val appVm: HeadApplicationViewModel = viewModel()
            val formState by appVm.uiState.collectAsStateWithLifecycle()
            HeadApplicationFormScreen(
                state = formState,
                onFieldChange = appVm::onFieldChange,
                onSubmit = { 
                    appVm.submitApplication(onSuccess = onSessionResolved) 
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Destinations.HEAD_APPLICATION_PENDING) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Your head application is pending review.", style = MaterialTheme.typography.bodyLarge)
            }
        }
        composable(Destinations.HEAD_APPLICATION_REJECTED) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Your head application was rejected.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            }
        }
        composable(Destinations.COMMUNITY_SETUP) {
            val setupVm: CommunitySetupViewModel = viewModel()
            val isLoading by setupVm.isLoading.collectAsStateWithLifecycle()
            val error by setupVm.error.collectAsStateWithLifecycle()
            
            CommunitySetupScreen(
                isLoading = isLoading,
                errorMessage = error,
                onSubmit = { name, desc ->
                    setupVm.submitSetup(name, desc, onSuccess = onSessionResolved)
                }
            )
        }
        composable(Destinations.COMMUNITY_HEAD_LOGIN) {
            val loginVm: CommunityHeadLoginViewModel = viewModel()
            CommunityHeadLoginScreen(
                vm = loginVm,
                onSuccess = onSessionResolved,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Destinations.CREATE_NEW_PASSWORD) {
            // Note: The userId comes from the session state MustChangePassword
            // In a real app we could pass it down, but here it's already resolved in the session
            // For now, let's just pass a dummy or get it from Supabase client directly
            val userId = SupabaseApi.client.auth.currentUserOrNull()?.id ?: ""
            val pwdVm: CreateNewPasswordViewModel = viewModel()
            CreateNewPasswordScreen(
                userId = userId,
                vm = pwdVm,
                onSuccess = onSessionResolved
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Member flow — bottom nav with 5 tabs; head gets extra console tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MemberNavGraph(
    userId: String,
    communityId: String,
    username: String,
    role: String,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val feedViewModel: HomeFeedViewModel = viewModel()
    val submitVm: SubmitUrlViewModel = viewModel()
    val feedState by feedViewModel.uiState.collectAsStateWithLifecycle()
    val submitState by submitVm.uiState.collectAsStateWithLifecycle()

    // Build nav items based on role — resolved server-side, not from client claim
    val navItems = if (role == "COMMUNITY_HEAD") getHeadNavItems() else getMemberNavItems()

    Scaffold(
        bottomBar = {
            val isDetailScreen = currentRoute == Destinations.CONTENT_DETAIL ||
                currentRoute == Destinations.SUBMIT_URL ||
                currentRoute?.startsWith("invite") == true ||
                currentRoute?.startsWith("member") == true
            if (!isDetailScreen) {
                BottomNavigationBar(
                    items = navItems,
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destinations.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Destinations.HOME) {
                HomeFeedScreen(
                    state = feedState,
                    onSearch = feedViewModel::onSearchChange,
                    onCategorySelect = feedViewModel::onCategorySelect,
                    onToggleEvents = feedViewModel::toggleEventsOnly,
                    onToggleResources = feedViewModel::toggleResourcesOnly,
                    onRefresh = feedViewModel::refresh,
                    onContentClick = { id -> navController.navigate(Destinations.contentDetail(id)) },
                    onBookmark = { /* TODO: toggle bookmark */ },
                    communityName = "My Community"
                )
            }
            composable(Destinations.SUBMIT_URL) {
                SubmitUrlScreen(
                    state = submitState,
                    onUrlChange = submitVm::onUrlChange,
                    onCategoryChange = submitVm::onCategoryChange,
                    onSubmit = { submitVm.submit(communityId) },
                    onReset = submitVm::reset,
                    onMetadataChange = submitVm::onMetadataChange,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Destinations.CONTENT_DETAIL) { backStackEntry ->
                val contentId = backStackEntry.arguments?.getString("contentId") ?: ""
                ContentDetailScreen(
                    content = null, // TODO: load from VM
                    isLoading = false,
                    isBookmarked = false,
                    onBookmark = {},
                    onReport = {},
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Destinations.BOOKMARKS) {
                BookmarksScreen(
                    bookmarks = emptyList(),
                    isLoading = false,
                    error = null,
                    searchQuery = "",
                    onSearch = {},
                    onRemoveBookmark = {},
                    onContentClick = { id -> navController.navigate(Destinations.contentDetail(id)) },
                    showEventsOnly = false,
                    showResourcesOnly = false,
                    onToggleEvents = {},
                    onToggleResources = {}
                )
            }
            composable(Destinations.NOTIFICATIONS) {
                NotificationsScreen(notifications = emptyList(), isLoading = false)
            }
            composable(Destinations.PROFILE) {
                ProfileScreen(
                    username = username,
                    communityName = "My Community",
                    role = role,
                    onApplyAsHead = { navController.navigate(Destinations.HEAD_APPLICATION_FORM) },
                    onSignOut = onSignOut
                )
            }
            composable(Destinations.HEAD_APPLICATION_FORM) {
                var formState by remember { mutableStateOf(HeadApplicationFormState()) }
                HeadApplicationFormScreen(
                    state = formState,
                    onFieldChange = { formState = it },
                    onSubmit = { formState = formState.copy(isSubmitted = true) },
                    onBack = { navController.popBackStack() }
                )
            }
            // Head-only routes
            if (role == "COMMUNITY_HEAD") {
                composable(Destinations.HEAD_CONSOLE) {
                    HeadConsoleScreen(
                        communityName = "My Community",
                        onInviteCodesClick = { navController.navigate(Destinations.INVITE_CODES) },
                        onMembersClick = { navController.navigate(Destinations.MEMBER_MANAGEMENT) },
                        onContentClick = { navController.navigate(Destinations.CONTENT_MANAGEMENT) }
                    )
                }
                composable(Destinations.INVITE_CODES) {
                    InviteCodeManagementScreen(
                        codes = emptyList(),
                        isLoading = false,
                        onGenerateCode = {},
                        onRevokeCode = {},
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Destinations.MEMBER_MANAGEMENT) {
                    MemberManagementScreen(
                        members = emptyList(),
                        isLoading = false,
                        searchQuery = "",
                        onSearch = {},
                        onRemoveMember = {},
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Destinations.CONTENT_MANAGEMENT) {
                    // Reuses home feed UI — TODO: head-specific actions
                    HomeFeedScreen(
                        state = feedState,
                        onSearch = feedViewModel::onSearchChange,
                        onCategorySelect = feedViewModel::onCategorySelect,
                        onToggleEvents = feedViewModel::toggleEventsOnly,
                        onToggleResources = feedViewModel::toggleResourcesOnly,
                        onRefresh = feedViewModel::refresh,
                        onContentClick = {},
                        onBookmark = {},
                        communityName = "Manage Content"
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Admin flow — platform admin only
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AdminNavGraph(userId: String, onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val adminVm: AdminViewModel = viewModel()

    NavHost(navController = navController, startDestination = Destinations.ADMIN_DASHBOARD) {
        composable(Destinations.ADMIN_DASHBOARD) {
            val stats by adminVm.stats.collectAsStateWithLifecycle()
            val isLoading by adminVm.isStatsLoading.collectAsStateWithLifecycle()
            val errorMessage by adminVm.errorMessage.collectAsStateWithLifecycle()
            
            LaunchedEffect(Unit) {
                adminVm.loadDashboardStats()
            }

            AdminDashboardScreen(
                stats = stats,
                isLoading = isLoading,
                errorMessage = errorMessage,
                onRefresh = adminVm::loadDashboardStats,
                onApplicationsClick = { navController.navigate(Destinations.HEAD_APPLICATIONS) },
                onCommunitiesClick = { navController.navigate(Destinations.COMMUNITIES) },
                onAuditLogsClick = { navController.navigate(Destinations.AUDIT_LOGS) },
                onSignOut = onSignOut
            )
        }
        composable(Destinations.HEAD_APPLICATIONS) {
            val apps by adminVm.applications.collectAsStateWithLifecycle()
            val isLoading by adminVm.isApplicationsLoading.collectAsStateWithLifecycle()
            val errorMessage by adminVm.errorMessage.collectAsStateWithLifecycle()
            val actionMessage by adminVm.actionMessage.collectAsStateWithLifecycle()
            
            LaunchedEffect(Unit) {
                adminVm.loadApplications()
            }

            HeadApplicationsScreen(
                applications = apps,
                isLoading = isLoading,
                errorMessage = errorMessage,
                actionMessage = actionMessage,
                onRefresh = adminVm::loadApplications,
                onApprove = adminVm::approveApplication,
                onReject = adminVm::rejectApplication,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Destinations.COMMUNITIES) {
            val communities by adminVm.communities.collectAsStateWithLifecycle()
            val isLoading by adminVm.isCommunitiesLoading.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                adminVm.loadCommunities()
            }

            CommunitiesScreen(
                communities = communities,
                isLoading = isLoading,
                onSuspend = adminVm::suspendCommunity,
                onReactivate = adminVm::reactivateCommunity,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Destinations.AUDIT_LOGS) {
            val logs by adminVm.auditLogs.collectAsStateWithLifecycle()
            val isLoading by adminVm.isAuditLogsLoading.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                adminVm.loadAuditLogs()
            }

            AuditLogsScreen(
                logs = logs,
                isLoading = isLoading,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
