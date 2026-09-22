package com.vulnspace.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BookmarkBorder
// Outlined imports aliased so Kotlin can resolve the correct receiver
// Usage: Icons.Outlined.<Alias> — the alias replaces the property name on the receiver
import androidx.compose.material.icons.outlined.Home as HomeOutlined
import androidx.compose.material.icons.outlined.Link as LinkOutlined
import androidx.compose.material.icons.outlined.Notifications as NotificationsOutlined
import androidx.compose.material.icons.outlined.Person as PersonOutlined
import com.vulnspace.app.ui.components.NavItem

// Typed route destinations for Navigation Compose
object Destinations {
    // Auth / Onboarding
    const val WELCOME = "welcome"
    const val JOIN_COMMUNITY = "join_community"
    const val SIGN_IN = "sign_in"
    const val PLATFORM_ADMIN_LOGIN = "platform_admin_login"
    const val USERNAME_SETUP = "username_setup/{inviteCode}"
    fun usernameSetup(inviteCode: String) = "username_setup/$inviteCode"

    // Member
    const val HOME = "home"
    const val SUBMIT_URL = "submit_url"
    const val CONTENT_DETAIL = "content_detail/{contentId}"
    fun contentDetail(contentId: String) = "content_detail/$contentId"
    const val BOOKMARKS = "bookmarks"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"

    // Head Application
    const val HEAD_APPLICATION_FORM = "head_application_form"
    const val HEAD_APPLICATION_PENDING = "head_application_pending"
    const val HEAD_APPLICATION_REJECTED = "head_application_rejected"
    const val COMMUNITY_SETUP = "community_setup"
    
    // Head Authentication
    const val COMMUNITY_HEAD_LOGIN = "community_head_login"
    const val CREATE_HEAD_PASSWORD = "create_head_password"
    const val CREATE_NEW_PASSWORD = "create_new_password"

    // Community Head Console
    const val HEAD_CONSOLE = "head_console"
    const val INVITE_CODES = "invite_codes"
    const val MEMBER_MANAGEMENT = "member_management"
    const val CONTENT_MANAGEMENT = "content_management"

    // Platform Admin
    const val ADMIN_DASHBOARD = "admin_dashboard"
    const val HEAD_APPLICATIONS = "head_applications"
    const val COMMUNITIES = "communities"
    const val AUDIT_LOGS = "audit_logs"

    // Restricted
    const val SUSPENDED = "suspended"
}

/**
 * Returns bottom nav items for the member area.
 *
 * Kotlin import aliases for extension properties work as follows:
 *   `import X.Y as Z` makes Y accessible as `receiver.Z`
 * So `import ...outlined.Home as HomeOutlined` → use as `Icons.Outlined.HomeOutlined`
 */
fun getMemberNavItems(): List<NavItem> = listOf(
    NavItem(
        route = Destinations.HOME,
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.HomeOutlined
    ),
    NavItem(
        route = Destinations.SUBMIT_URL,
        label = "Submit",
        selectedIcon = Icons.Filled.Link,
        unselectedIcon = Icons.Outlined.LinkOutlined
    ),
    NavItem(
        route = Destinations.BOOKMARKS,
        label = "Saved",
        selectedIcon = Icons.Filled.Bookmark,
        unselectedIcon = Icons.Outlined.BookmarkBorder
    ),
    NavItem(
        route = Destinations.NOTIFICATIONS,
        label = "Alerts",
        selectedIcon = Icons.Filled.Notifications,
        unselectedIcon = Icons.Outlined.NotificationsOutlined
    ),
    NavItem(
        route = Destinations.PROFILE,
        label = "Profile",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.PersonOutlined
    )
)

fun getHeadNavItems(): List<NavItem> = getMemberNavItems() + listOf(
    NavItem(
        route = Destinations.HEAD_CONSOLE,
        label = "Console",
        selectedIcon = Icons.Filled.ManageAccounts,
        unselectedIcon = Icons.Filled.ManageAccounts
    )
)
