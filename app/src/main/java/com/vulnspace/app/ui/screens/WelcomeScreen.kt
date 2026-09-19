package com.vulnspace.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vulnspace.app.ui.components.PrimaryCyberButton
import com.vulnspace.app.ui.components.SecondaryCyberButton
import com.vulnspace.app.ui.theme.*

@Composable
fun WelcomeScreen(
    onJoinCommunity: () -> Unit,
    onApplyAsHead: () -> Unit,
    onSignIn: () -> Unit
) {
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

            // Logo mark
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(PrimaryBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Security,
                    contentDescription = "VulnSpace",
                    tint = WhiteSurface,
                    modifier = Modifier.size(48.dp)
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

            Spacer(Modifier.height(48.dp))

            // Feature pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                FeaturePill(icon = Icons.Filled.Key, text = "Invite-only")
                FeaturePill(icon = Icons.Filled.VerifiedUser, text = "Safety-checked")
                FeaturePill(icon = Icons.Filled.Bolt, text = "Real-time")
            }

            Spacer(Modifier.height(48.dp))

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

            Spacer(Modifier.height(32.dp))

            // Sign in link for admins/heads
            TextButton(onClick = onSignIn) {
                Text(
                    text = "Already a member or head? Sign in",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PrimaryBlue
                )
            }

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
