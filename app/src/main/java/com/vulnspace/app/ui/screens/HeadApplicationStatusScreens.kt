package com.vulnspace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vulnspace.app.presentation.viewmodel.HeadApplicationStatusViewModel
import com.vulnspace.app.presentation.viewmodel.HeadApplicationTrackingState
import com.vulnspace.app.ui.components.CyberCard
import com.vulnspace.app.ui.components.CyberTopBar
import com.vulnspace.app.ui.components.ErrorState
import com.vulnspace.app.ui.components.PrimaryCyberButton
import com.vulnspace.app.ui.components.SecondaryCyberButton
import com.vulnspace.app.ui.theme.*

@Composable
fun HeadApplicationStatusScreen(
    onClaimSuccess: () -> Unit,
    onBack: () -> Unit,
    vm: HeadApplicationStatusViewModel = viewModel()
) {
    val trackingState by vm.trackingState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.restoreAndCheckStatus()
    }

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column {
            CyberTopBar(
                title = "Application Status",
                subtitle = "Track your Community Head application",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
            HorizontalDivider(color = BlueBorder)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (val state = trackingState) {
                    is HeadApplicationTrackingState.CheckingStatus -> {
                        CyberCard {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(48.dp))
                                Text(
                                    text = "Checking Application Status...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    is HeadApplicationTrackingState.Pending -> {
                        HeadApplicationPendingCard(
                            communityName = state.communityName,
                            onCheckStatus = vm::checkStatus,
                            onBack = onBack
                        )
                    }

                    is HeadApplicationTrackingState.ApprovedReadyToClaim -> {
                        HeadApplicationApprovedCard(
                            communityName = state.communityName,
                            onClaimWithGoogle = {
                                vm.claimWithGoogle(idToken = null, onSuccess = onClaimSuccess)
                            },
                            onBack = onBack
                        )
                    }

                    is HeadApplicationTrackingState.StartingGoogleSignIn,
                    is HeadApplicationTrackingState.Claiming -> {
                        CyberCard {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(48.dp))
                                Text(
                                    text = "Claiming Community...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Verifying your Google authentication and establishing your Community Head credentials.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    is HeadApplicationTrackingState.Claimed -> {
                        CyberCard {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(
                                    text = "Community Claimed!",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Your community '${state.communityName}' is now active. Open your console to manage members and content.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                PrimaryCyberButton(
                                    text = "Open Head Console",
                                    onClick = onClaimSuccess,
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = Icons.AutoMirrored.Filled.Login
                                )
                            }
                        }
                    }

                    is HeadApplicationTrackingState.Rejected -> {
                        HeadApplicationRejectedCard(
                            reason = state.reason,
                            onClearAndBack = {
                                vm.clearTrackedState()
                                onBack()
                            }
                        )
                    }

                    is HeadApplicationTrackingState.TrackingExpired -> {
                        CyberCard {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = WarningAmber,
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(
                                    text = "Tracking Token Expired",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Your device tracking token has expired or was revoked. Please submit a new application if you wish to create a community.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                PrimaryCyberButton(
                                    text = "Return to Welcome",
                                    onClick = {
                                        vm.clearTrackedState()
                                        onBack()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = Icons.AutoMirrored.Filled.ArrowBack
                                )
                            }
                        }
                    }

                    is HeadApplicationTrackingState.RecoverableError -> {
                        CyberCard {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ErrorState(state.message)
                                PrimaryCyberButton(
                                    text = "Try Again",
                                    onClick = vm::checkStatus,
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = Icons.Filled.Refresh
                                )
                                SecondaryCyberButton(
                                    text = "Back",
                                    onClick = onBack,
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = Icons.AutoMirrored.Filled.ArrowBack
                                )
                            }
                        }
                    }

                    is HeadApplicationTrackingState.NoTrackedApplication -> {
                        CyberCard {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No Application Found",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "No active application was found on this device. You can apply to become a Community Head from the welcome screen.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                PrimaryCyberButton(
                                    text = "Return to Welcome",
                                    onClick = onBack,
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = Icons.AutoMirrored.Filled.ArrowBack
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeadApplicationPendingScreen(
    onCheckStatus: () -> Unit,
    onSignOut: () -> Unit
) {
    HeadApplicationStatusScreen(
        onClaimSuccess = onCheckStatus,
        onBack = onSignOut
    )
}

@Composable
fun HeadApplicationApprovedScreen(
    communityName: String? = null,
    onLoginAsHead: () -> Unit,
    onSignOut: () -> Unit
) {
    HeadApplicationStatusScreen(
        onClaimSuccess = onLoginAsHead,
        onBack = onSignOut
    )
}

@Composable
fun HeadApplicationRejectedScreen(
    reason: String? = null,
    onSignOut: () -> Unit
) {
    HeadApplicationStatusScreen(
        onClaimSuccess = {},
        onBack = onSignOut
    )
}

@Composable
private fun HeadApplicationPendingCard(
    communityName: String,
    onCheckStatus: () -> Unit,
    onBack: () -> Unit
) {
    CyberCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Filled.HourglassTop,
                contentDescription = null,
                tint = WarningAmber,
                modifier = Modifier.size(56.dp)
            )

            Text(
                text = "Application Under Review",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Your application for '$communityName' has been submitted and is currently under review by Platform Admins.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Once approved, you will see the acceptance screen here and can claim your community with Google.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            PrimaryCyberButton(
                text = "Check Status",
                onClick = onCheckStatus,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Filled.Refresh
            )

            SecondaryCyberButton(
                text = "Return to Welcome Screen",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.AutoMirrored.Filled.ArrowBack
            )
        }
    }
}

@Composable
private fun HeadApplicationApprovedCard(
    communityName: String,
    onClaimWithGoogle: () -> Unit,
    onBack: () -> Unit
) {
    CyberCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(56.dp)
            )

            Text(
                text = "Application Accepted!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Congratulations! Your application to create the community '$communityName' has been approved by Platform Admins.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Tap below to authenticate with the Google account associated with your application and claim your Community Head console.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            PrimaryCyberButton(
                text = "Claim Community with Google",
                onClick = onClaimWithGoogle,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Filled.AccountCircle
            )

            SecondaryCyberButton(
                text = "Return to Welcome Screen",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.AutoMirrored.Filled.ArrowBack
            )
        }
    }
}

@Composable
private fun HeadApplicationRejectedCard(
    reason: String?,
    onClearAndBack: () -> Unit
) {
    CyberCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Filled.Cancel,
                contentDescription = null,
                tint = DangerRed,
                modifier = Modifier.size(56.dp)
            )

            Text(
                text = "Application Not Approved",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = reason ?: "Your application to start a community was not approved at this time.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            PrimaryCyberButton(
                text = "Return to Welcome Screen",
                onClick = onClearAndBack,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.AutoMirrored.Filled.ArrowBack
            )
        }
    }
}
