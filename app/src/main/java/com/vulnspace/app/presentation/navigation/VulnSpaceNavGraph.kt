package com.vulnspace.app.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
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
import com.vulnspace.app.presentation.viewmodel.*
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

        is SessionState.PendingHeadApplication -> {
            MemberNavGraph(
                userId = state.userId,
                communityId = "",
                username = "",
                role = "PENDING_HEAD",
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
private fun AuthNavGraph(onSessionResolved: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Destinations.WELCOME) {
        composable(Destinations.WELCOME) {
            WelcomeScreen(
                onJoinCommunity = { navController.navigate(Destinations.JOIN_COMMUNITY) },
                onApplyAsHead = { navController.navigate(Destinations.HEAD_APPLICATION_FORM) },
                onSignIn = { navController.navigate(Destinations.SIGN_IN) }
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
    NavHost(navController = navController, startDestination = Destinations.ADMIN_DASHBOARD) {
        composable(Destinations.ADMIN_DASHBOARD) {
            AdminDashboardScreen(
                stats = AdminStats(),
                isLoading = false,
                onApplicationsClick = { navController.navigate(Destinations.HEAD_APPLICATIONS) },
                onCommunitiesClick = { navController.navigate(Destinations.COMMUNITIES) },
                onAuditLogsClick = { navController.navigate(Destinations.AUDIT_LOGS) }
            )
        }
        composable(Destinations.HEAD_APPLICATIONS) {
            HeadApplicationsScreen(
                applications = emptyList(),
                isLoading = false,
                onApprove = {},
                onReject = { _, _ -> },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Destinations.COMMUNITIES) {
            CommunitiesScreen(
                communities = emptyList(),
                isLoading = false,
                onSuspend = {},
                onReactivate = {},
                onBack = { navController.popBackStack() }
            )
        }
        composable(Destinations.AUDIT_LOGS) {
            AuditLogsScreen(
                logs = emptyList(),
                isLoading = false,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
