package com.vulnspace.app.domain.model

import kotlinx.serialization.Serializable

enum class ContentType { EVENT, RESOURCE }

@Serializable
data class Content(
    val id: String,
    val communityId: String,
    val title: String,
    val description: String? = null,
    val sourceUrl: String,
    val sourceDomain: String? = null,
    val contentType: String,          // "EVENT" | "RESOURCE"
    val category: String,             // ContentCategory enum value
    val status: String,               // SUBMITTED, PROCESSING, PUBLISHED, FAILED, BLOCKED
    val safetyStatus: String? = null, // LOW_RISK, NEEDS_REVIEW, BLOCKED
    val organizer: String? = null,
    val registrationDeadline: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val mode: String? = null,         // ONLINE, OFFLINE, HYBRID
    val location: String? = null,
    val tags: List<String> = emptyList(),
    val registrationLink: String? = null,
    val rulesLink: String? = null,
    val priority: Int = 0,
    val daysLeft: Int? = null,
    val submittedByUsername: String? = null,
    val isBookmarked: Boolean = false,
    val createdAt: String = ""
)
