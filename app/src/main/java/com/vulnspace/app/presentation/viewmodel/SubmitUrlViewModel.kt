package com.vulnspace.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vulnspace.app.data.supabase.SupabaseApi
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.gotrue.auth
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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
        val url = _uiState.value.url.trim()
        if (!_uiState.value.isUrlValid) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid URL starting with https://") }
            return
        }
        if (communityId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Community is still loading — try again in a moment.") }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(step = SubmitStep.URL_RECEIVED, errorMessage = null) }
                delay(300)
                _uiState.update { it.copy(step = SubmitStep.CHECKING_FORMAT) }
                delay(300)
                _uiState.update { it.copy(step = SubmitStep.ANALYZING_PAGE) }

                val token = SupabaseApi.client.auth.currentAccessTokenOrNull()
                val response = SupabaseApi.client.functions.invoke(
                    function = "submit-content-url",
                    body = buildJsonObject {
                        put("community_id", communityId)
                        put("url", url)
                        if (_uiState.value.selectedCategory.isNotBlank()) {
                            put("category", _uiState.value.selectedCategory)
                        }
                    },
                    headers = io.ktor.http.Headers.build {
                        if (!token.isNullOrBlank()) {
                            append(io.ktor.http.HttpHeaders.Authorization, "Bearer $token")
                        }
                    }
                )

                val responseText = try { response.bodyAsText() } catch (_: Exception) { "" }

                if (response.status.value !in 200..299) {
                    val serverMsg = Regex("\"(?:error|message)\"\\s*:\\s*\"([^\"]+)\"")
                        .find(responseText)?.groupValues?.get(1)
                    _uiState.update {
                        it.copy(
                            step = SubmitStep.FAILED,
                            errorMessage = serverMsg ?: "Could not process the link (HTTP ${response.status.value})."
                        )
                    }
                    return@launch
                }

                _uiState.update { it.copy(step = SubmitStep.EXTRACTING_DETAILS) }
                delay(200)
                _uiState.update { it.copy(step = SubmitStep.CHECKING_SAFETY) }
                delay(200)

                val json = try { Json.parseToJsonElement(responseText).jsonObject } catch (_: Exception) { null }
                val resultStatus = json?.get("status")?.jsonPrimitive?.contentOrNull ?: "PUBLISHED"
                val meta = json?.get("metadata")
                val metaObj = try { meta?.jsonObject } catch (_: Exception) { null }
                fun metaText(key: String) = metaObj?.get(key)?.jsonPrimitive?.contentOrNull ?: ""

                _uiState.update { it.copy(step = SubmitStep.PREPARING_CARD) }
                delay(200)

                val metadata = ExtractedMetadata(
                    title = metaText("title"),
                    description = metaText("description"),
                    category = metaText("category"),
                    organizer = metaText("organizer"),
                    safetyStatus = metaText("safety_status")
                )

                val finalStep = when {
                    resultStatus == "BLOCKED" -> SubmitStep.BLOCKED
                    metaText("safety_status") == "NEEDS_REVIEW" -> SubmitStep.NEEDS_REVIEW
                    else -> SubmitStep.PUBLISHED
                }

                _uiState.update {
                    it.copy(
                        step = finalStep,
                        metadata = metadata,
                        errorMessage = if (finalStep == SubmitStep.BLOCKED)
                            "This link was blocked by the safety check." else null
                    )
                }
            } catch (e: Exception) {
                val raw = e.message.orEmpty()
                _uiState.update {
                    it.copy(
                        step = SubmitStep.FAILED,
                        errorMessage = if (raw.contains("network", true) || raw.contains("connect", true))
                            "Network error. Check your connection and try again."
                        else "Could not process the link. Please try again."
                    )
                }
            }
        }
    }

    fun reset() = _uiState.update { SubmitUrlUiState() }
}
