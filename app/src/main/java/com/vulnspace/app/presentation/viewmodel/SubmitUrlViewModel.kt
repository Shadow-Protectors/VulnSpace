package com.vulnspace.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SubmitStep {
    IDLE, URL_RECEIVED, CHECKING_FORMAT, ANALYZING_PAGE,
    EXTRACTING_DETAILS, CHECKING_SAFETY, PREPARING_CARD,
    PUBLISHED, FAILED, BLOCKED, NEEDS_REVIEW
}

data class ExtractedMetadata(
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val organizer: String = "",
    val registrationDeadline: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val mode: String = "",
    val location: String = "",
    val tags: String = "",
    val safetyStatus: String = ""
)

data class SubmitUrlUiState(
    val url: String = "",
    val isUrlValid: Boolean = false,
    val step: SubmitStep = SubmitStep.IDLE,
    val metadata: ExtractedMetadata? = null,
    val errorMessage: String? = null,
    val selectedCategory: String = ""
)

class SubmitUrlViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SubmitUrlUiState())
    val uiState: StateFlow<SubmitUrlUiState> = _uiState.asStateFlow()

    fun onUrlChange(url: String) {
        val valid = url.startsWith("http://") || url.startsWith("https://")
        _uiState.update { it.copy(url = url, isUrlValid = valid, errorMessage = null) }
    }

    fun onCategoryChange(cat: String) = _uiState.update { it.copy(selectedCategory = cat) }

    fun onMetadataChange(metadata: ExtractedMetadata) = _uiState.update { it.copy(metadata = metadata) }

    fun submit(communityId: String) {
        val url = _uiState.value.url
        if (!_uiState.value.isUrlValid) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid URL starting with https://") }
            return
        }
        viewModelScope.launch {
            try {
                // Progress simulation — replace with real Edge Function call
                val steps = listOf(
                    SubmitStep.URL_RECEIVED,
                    SubmitStep.CHECKING_FORMAT,
                    SubmitStep.ANALYZING_PAGE,
                    SubmitStep.EXTRACTING_DETAILS,
                    SubmitStep.CHECKING_SAFETY,
                    SubmitStep.PREPARING_CARD
                )
                for (step in steps) {
                    _uiState.update { it.copy(step = step) }
                    delay(600)
                }
                // TODO: Call Supabase Edge Function submit-content-url
                // val response = SupabaseApi.client.functions.invoke("submit-content-url", body = ...)
                // Parse response and populate metadata
                _uiState.update {
                    it.copy(
                        step = SubmitStep.PUBLISHED,
                        metadata = ExtractedMetadata(
                            title = "Extracted title will appear here",
                            safetyStatus = "LOW_RISK"
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(step = SubmitStep.FAILED, errorMessage = "Could not process the link. Please try again.") }
            }
        }
    }

    fun reset() = _uiState.update { SubmitUrlUiState() }
}
