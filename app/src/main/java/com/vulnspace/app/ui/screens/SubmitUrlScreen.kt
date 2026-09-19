package com.vulnspace.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vulnspace.app.presentation.viewmodel.*
import com.vulnspace.app.ui.components.*
import com.vulnspace.app.ui.theme.*

@Composable
fun SubmitUrlScreen(
    state: SubmitUrlUiState,
    onUrlChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onReset: () -> Unit,
    onMetadataChange: (ExtractedMetadata) -> Unit,
    onBack: () -> Unit
) {
    val clipboard: ClipboardManager = LocalClipboardManager.current
    val isAnalyzing = state.step != SubmitStep.IDLE && state.step != SubmitStep.PUBLISHED
        && state.step != SubmitStep.FAILED && state.step != SubmitStep.BLOCKED

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            CyberTopBar(
                title = "Submit URL",
                subtitle = "Paste a link — we handle the rest",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
            HorizontalDivider(color = BlueBorder)

            Column(modifier = Modifier.padding(16.dp)) {
                // URL input
                OutlinedCyberTextField(
                    value = state.url,
                    onValueChange = onUrlChange,
                    label = "Link URL",
                    placeholder = "https://ctftime.org/event/...",
                    leadingIcon = Icons.Filled.Link,
                    trailingContent = {
                        IconButton(onClick = {
                            val text = clipboard.getText()?.text ?: ""
                            if (text.isNotBlank()) onUrlChange(text)
                        }) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = "Paste", tint = PrimaryBlue)
                        }
                    },
                    keyboardType = KeyboardType.Uri,
                    isError = state.errorMessage != null,
                    errorMessage = state.errorMessage,
                    enabled = !isAnalyzing
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    "Paste a CTF, hackathon, internship, resource, or documentation link. " +
                    "The system will extract title, dates, organizer, and safety status automatically.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(20.dp))

                // Analysis progress
                if (isAnalyzing) {
                    AnalysisProgressCard(step = state.step)
                    Spacer(Modifier.height(16.dp))
                }

                // Blocked result
                if (state.step == SubmitStep.BLOCKED) {
                    BlockedUrlCard(onReset)
                    return@Column
                }

                // Extracted metadata preview
                if (state.step == SubmitStep.PUBLISHED && state.metadata != null) {
                    MetadataPreviewCard(
                        metadata = state.metadata,
                        onMetadataChange = onMetadataChange,
                        sourceUrl = state.url
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryCyberButton(
                        text = "Publish to Community",
                        onClick = { /* Confirm publish */ },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = Icons.Filled.Publish
                    )
                    Spacer(Modifier.height(8.dp))
                    SecondaryCyberButton(text = "Submit another link", onClick = onReset, modifier = Modifier.fillMaxWidth())
                } else if (!isAnalyzing) {
                    PrimaryCyberButton(
                        text = "Analyze Link",
                        onClick = onSubmit,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.isUrlValid,
                        leadingIcon = Icons.Filled.Search
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalysisProgressCard(step: SubmitStep) {
    val steps = listOf(
        SubmitStep.URL_RECEIVED to "URL received",
        SubmitStep.CHECKING_FORMAT to "Checking link format",
        SubmitStep.ANALYZING_PAGE to "Analyzing page",
        SubmitStep.EXTRACTING_DETAILS to "Extracting event details",
        SubmitStep.CHECKING_SAFETY to "Checking safety signals",
        SubmitStep.PREPARING_CARD to "Preparing content card"
    )
    val currentIdx = steps.indexOfFirst { it.first == step }

    CyberCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = CyanProcessing
                )
                Text("Analyzing your link...", style = MaterialTheme.typography.titleMedium)
            }
            steps.forEachIndexed { idx, (_, label) ->
                val isDone = idx < currentIdx
                val isCurrent = idx == currentIdx
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (isDone) Icons.Filled.CheckCircle else if (isCurrent) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isDone) SuccessGreen else if (isCurrent) CyanProcessing else TextDisabled,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrent) TextPrimary else if (isDone) SuccessGreen else TextDisabled
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockedUrlCard(onReset: () -> Unit) {
    CyberCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Block, contentDescription = null, tint = DangerRed, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text("Link cannot be published", style = MaterialTheme.typography.titleMedium, color = DangerRed)
            Spacer(Modifier.height(8.dp))
            Text(
                "This link has been flagged as potentially unsafe and cannot be shared in your community. " +
                "If you believe this is incorrect, you can report the issue.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            SecondaryCyberButton(text = "Try a different link", onClick = onReset, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MetadataPreviewCard(
    metadata: ExtractedMetadata,
    onMetadataChange: (ExtractedMetadata) -> Unit,
    sourceUrl: String
) {
    CyberCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                Text("Content extracted — review & edit", style = MaterialTheme.typography.titleMedium)
            }
            if (metadata.safetyStatus.isNotBlank()) {
                SafetyBadge(metadata.safetyStatus)
            }
            OutlinedCyberTextField(
                value = metadata.title,
                onValueChange = { onMetadataChange(metadata.copy(title = it)) },
                label = "Title *",
                leadingIcon = Icons.Filled.Title
            )
            OutlinedCyberTextField(
                value = metadata.description,
                onValueChange = { onMetadataChange(metadata.copy(description = it)) },
                label = "Description",
                singleLine = false,
                maxLines = 4
            )
            OutlinedCyberTextField(
                value = metadata.organizer,
                onValueChange = { onMetadataChange(metadata.copy(organizer = it)) },
                label = "Organizer",
                leadingIcon = Icons.Filled.Business
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedCyberTextField(
                    value = metadata.registrationDeadline,
                    onValueChange = { onMetadataChange(metadata.copy(registrationDeadline = it)) },
                    label = "Registration Deadline",
                    modifier = Modifier.weight(1f)
                )
                OutlinedCyberTextField(
                    value = metadata.startDate,
                    onValueChange = { onMetadataChange(metadata.copy(startDate = it)) },
                    label = "Start Date",
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedCyberTextField(
                value = sourceUrl,
                onValueChange = {},
                label = "Source URL",
                leadingIcon = Icons.Filled.Link,
                enabled = false
            )
        }
    }
}
