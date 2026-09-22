package com.vulnspace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vulnspace.app.ui.components.PrimaryCyberButton
import com.vulnspace.app.ui.components.SecondaryCyberButton
import com.vulnspace.app.ui.theme.*

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import com.vulnspace.app.data.local.EncryptedTokenStorage
import com.vulnspace.app.ui.components.CyberCard

@Composable
fun WelcomeScreen(
    onJoinCommunity: () -> Unit,
    onApplyAsHead: () -> Unit,
    onHeadLogin: () -> Unit,
    onAdminLogin: () -> Unit,
    onCheckStatus: () -> Unit = {}
) {
    val context = LocalContext.current
    val tokenStorage = remember(context) { EncryptedTokenStorage(context) }
    val hasTrackedApplication = remember(context) { tokenStorage.hasTrackedApplication() }
    val trackedCommunityName = remember(context) { tokenStorage.getCommunityName() ?: "Your Community" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))

            // Logo mark (Double-click to access hidden Platform Admin Login)
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { onAdminLogin() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.vulnspace.app.R.drawable.app_logo),
                    contentDescription = "VulnSpace Logo",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "VulnSpace",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Private cybersecurity communities",
                style = MaterialTheme.typography.bodyLarge,
                color = PrimaryBlue,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Discover CTFs, hackathons, internships, and essential resources — curated by your community, available only to members.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(36.dp))

            // Feature pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                FeaturePill(icon = Icons.Filled.Key, text = "Invite-only")
                FeaturePill(icon = Icons.Filled.VerifiedUser, text = "Safety-checked")
                FeaturePill(icon = Icons.Filled.Bolt, text = "Real-time")
            }

            Spacer(Modifier.height(36.dp))

            // Dedicated Status Tracker (Visible ONLY for applied Community Heads on this device)
            if (hasTrackedApplication) {
                CyberCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCheckStatus() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(WarningAmber.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.HourglassTop,
                                    contentDescription = null,
                                    tint = WarningAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Track Application Status",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = trackedCommunityName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PrimaryBlue
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Check Status",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            // Primary CTA
            PrimaryCyberButton(
                text = "Join a Community",
                onClick = onJoinCommunity,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Filled.GroupAdd
            )

            Spacer(Modifier.height(12.dp))

            SecondaryCyberButton(
                text = "Apply to Start a Community",
                onClick = onApplyAsHead,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Filled.AdminPanelSettings
            )
            
            Spacer(Modifier.height(12.dp))
            
            SecondaryCyberButton(
                text = "Community Head Login",
                onClick = onHeadLogin,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Filled.Login
            )

            Spacer(Modifier.height(32.dp))

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FeaturePill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(LightBlue)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
    }
}
