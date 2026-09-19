package com.vulnspace.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vulnspace.app.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// CyberTopBar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CyberTopBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        },
        navigationIcon = { navigationIcon?.invoke() },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = WhiteSurface,
            titleContentColor = TextPrimary,
            actionIconContentColor = PrimaryBlue
        )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// CyberCard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CyberCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else modifier

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = WhiteSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BlueBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// StatusChip
// ─────────────────────────────────────────────────────────────────────────────

enum class ContentStatus {
    LOW_RISK, NEEDS_REVIEW, BLOCKED, PROCESSING, PUBLISHED, EXPIRED, ARCHIVED
}

@Composable
fun StatusChip(status: ContentStatus) {
    val (label, bg, textColor) = when (status) {
        ContentStatus.LOW_RISK -> Triple("LOW RISK", SuccessBackground, SuccessGreen)
        ContentStatus.NEEDS_REVIEW -> Triple("NEEDS REVIEW", WarningBackground, WarningAmber)
        ContentStatus.BLOCKED -> Triple("BLOCKED", DangerBackground, DangerRed)
        ContentStatus.PROCESSING -> Triple("PROCESSING", CyanProcessingBg, CyanProcessing)
        ContentStatus.PUBLISHED -> Triple("PUBLISHED", SuccessBackground, SuccessGreen)
        ContentStatus.EXPIRED -> Triple("EXPIRED", MutedGrayBg, MutedGray)
        ContentStatus.ARCHIVED -> Triple("ARCHIVED", MutedGrayBg, MutedGray)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CategoryChip
// ─────────────────────────────────────────────────────────────────────────────

enum class ContentCategory(val label: String, val isEvent: Boolean) {
    CTF("CTF", true),
    HACKATHON("Hackathon", true),
    INTERNSHIP("Internship", true),
    CONFERENCE("Conference", true),
    WORKSHOP("Workshop", true),
    STUDY_MATERIAL("Study Material", false),
    TOOL("Tool", false),
    WRITEUP("Write-up", false),
    COURSE("Course", false),
    DOCUMENTATION("Docs", false),
    OTHER_RESOURCE("Resource", false)
}

@Composable
fun CategoryChip(category: ContentCategory) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(LightBlue)
            .border(1.dp, BlueBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = category.label,
            style = MaterialTheme.typography.labelSmall,
            color = PrimaryBlue,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SafetyBadge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SafetyBadge(safetyStatus: String) {
    val (icon, color, bg, label) = when (safetyStatus.uppercase()) {
        "LOW_RISK" -> Tuple4(Icons.Filled.VerifiedUser, SuccessGreen, SuccessBackground, "Safe")
        "NEEDS_REVIEW" -> Tuple4(Icons.Filled.Warning, WarningAmber, WarningBackground, "Review")
        "BLOCKED" -> Tuple4(Icons.Filled.Block, DangerRed, DangerBackground, "Blocked")
        else -> Tuple4(Icons.Filled.HelpOutline, TextDisabled, MutedGrayBg, "Unknown")
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(12.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
private operator fun <A, B, C, D> Tuple4<A, B, C, D>.component1() = a
private operator fun <A, B, C, D> Tuple4<A, B, C, D>.component2() = b
private operator fun <A, B, C, D> Tuple4<A, B, C, D>.component3() = c
private operator fun <A, B, C, D> Tuple4<A, B, C, D>.component4() = d

// ─────────────────────────────────────────────────────────────────────────────
// DeadlineBadge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DeadlineBadge(daysLeft: Int?) {
    val (label, bg, textColor) = when {
        daysLeft == null -> Triple("No deadline", MutedGrayBg, MutedGray)
        daysLeft < 0 -> Triple("Registration closed", DangerBackground, DangerRed)
        daysLeft == 0 -> Triple("Closes today", DangerBackground, DangerRed)
        daysLeft == 1 -> Triple("1 day left", WarningBackground, WarningAmber)
        daysLeft <= 7 -> Triple("$daysLeft days left", WarningBackground, WarningAmber)
        else -> Triple("$daysLeft days left", SuccessBackground, SuccessGreen)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            Icons.Filled.Schedule,
            contentDescription = "Deadline",
            tint = textColor,
            modifier = Modifier.size(12.dp)
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = textColor, fontWeight = FontWeight.SemiBold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Buttons
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PrimaryCyberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryBlue,
            contentColor = WhiteSurface,
            disabledContainerColor = TextDisabled,
            disabledContentColor = WhiteSurface
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = WhiteSurface,
                strokeWidth = 2.dp
            )
        } else {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SecondaryCyberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = PrimaryBlue,
            disabledContentColor = TextDisabled
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (enabled) PrimaryBlue else TextDisabled
        )
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OutlinedCyberTextField
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun OutlinedCyberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    isError: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder, color = TextDisabled) },
            leadingIcon = if (leadingIcon != null) {
                { Icon(leadingIcon, contentDescription = label, tint = if (isError) DangerRed else PrimaryBlue) }
            } else null,
            trailingIcon = trailingContent,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            maxLines = maxLines,
            isError = isError,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = BlueBorder,
                errorBorderColor = DangerRed,
                focusedLabelColor = PrimaryBlue,
                unfocusedLabelColor = TextSecondary,
                cursorColor = PrimaryBlue,
                focusedContainerColor = WhiteSurface,
                unfocusedContainerColor = WhiteSurface
            ),
            shape = RoundedCornerShape(10.dp)
        )
        if (isError && errorMessage != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = DangerRed
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// InviteCodeInput
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun InviteCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    onPaste: () -> Unit,
    isError: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.uppercase().trim()) },
            label = { Text("Invite Code") },
            placeholder = { Text("XXXX-XXXX-XXXX", color = TextDisabled, fontFamily = FontFamily.Monospace) },
            leadingIcon = {
                Icon(Icons.Filled.Key, contentDescription = "Invite Code", tint = PrimaryBlue)
            },
            trailingIcon = {
                IconButton(onClick = onPaste) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = "Paste", tint = PrimaryBlue)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = isError,
            enabled = enabled,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                letterSpacing = 2.sp
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = BlueBorder,
                errorBorderColor = DangerRed,
                focusedLabelColor = PrimaryBlue,
                unfocusedLabelColor = TextSecondary,
                cursorColor = PrimaryBlue,
                focusedContainerColor = WhiteSurface,
                unfocusedContainerColor = WhiteSurface
            ),
            shape = RoundedCornerShape(10.dp)
        )
        if (isError && errorMessage != null) {
            Spacer(Modifier.height(4.dp))
            Text(text = errorMessage, style = MaterialTheme.typography.bodyMedium, color = DangerRed)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EventCard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EventCard(
    title: String,
    category: ContentCategory,
    organizer: String?,
    daysLeft: Int?,
    safetyStatus: String,
    isBookmarked: Boolean,
    onBookmark: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CyberCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryChip(category)
                    SafetyBadge(safetyStatus)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (organizer != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "by $organizer",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(10.dp))
                DeadlineBadge(daysLeft)
            }
            IconButton(onClick = onBookmark) {
                Icon(
                    if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark",
                    tint = if (isBookmarked) PrimaryBlue else TextSecondary
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ResourceCard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ResourceCard(
    title: String,
    category: ContentCategory,
    description: String?,
    sourceDomain: String,
    safetyStatus: String,
    isBookmarked: Boolean,
    onBookmark: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CyberCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryChip(category)
                    SafetyBadge(safetyStatus)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (description != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Link, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(12.dp))
                    Text(text = sourceDomain, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
            }
            IconButton(onClick = onBookmark) {
                Icon(
                    if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark",
                    tint = if (isBookmarked) PrimaryBlue else TextSecondary
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EmptyState
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EmptyState(
    icon: ImageVector = Icons.Outlined.Inbox,
    title: String,
    message: String,
    action: Pair<String, () -> Unit>? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = TextDisabled,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            SecondaryCyberButton(text = action.first, onClick = action.second)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LoadingState / Skeleton Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BlueBorder.copy(alpha = shimmer))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ErrorState
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = DangerRed,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(text = "Something went wrong", style = MaterialTheme.typography.titleMedium, color = TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryCyberButton(text = "Retry", onClick = onRetry, leadingIcon = Icons.Filled.Refresh)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ConfirmationDialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    cancelText: String = "Cancel",
    isDangerous: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            if (isDangerous) Icon(Icons.Filled.Warning, contentDescription = null, tint = DangerRed)
        },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDangerous) DangerRed else PrimaryBlue
                )
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelText, color = TextSecondary) }
        },
        containerColor = WhiteSurface
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// BottomNavigationBar
// ─────────────────────────────────────────────────────────────────────────────

data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun BottomNavigationBar(
    items: List<NavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = WhiteSurface,
        tonalElevation = 0.dp,
        modifier = Modifier.border(
            width = 1.dp,
            color = BlueBorder,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        )
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Default),
                        fontSize = 11.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryBlue,
                    selectedTextColor = PrimaryBlue,
                    indicatorColor = LightBlue,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary
                )
            )
        }
    }
}
