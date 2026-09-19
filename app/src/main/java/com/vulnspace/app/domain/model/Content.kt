package com.vulnspace.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class ContentType { EVENT, RESOURCE }

/**
 * Minimal, API-24-safe date helpers (no java.time, which needs API 26+).
 */
object ContentDates {
    /** Whole days until the given ISO-8601 timestamp; null when unknown/past. */
    fun daysUntil(isoTimestamp: String?): Int? {
        if (isoTimestamp.isNullOrBlank()) return null
        return try {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val target = fmt.parse(isoTimestamp.take(10)) ?: return null
            val today = fmt.parse(fmt.format(java.util.Date())) ?: return null
            val days = ((target.time - today.time) / 86_400_000L).toInt()
            if (days < 0) null else days
        } catch (_: Exception) {
            null
        }
    }

    /** Current time as a Postgres-compatible ISO-8601 UTC string. */
    fun nowIso(): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return fmt.format(java.util.Date())
    }
}

/**
 * Matches the public.content table (snake_case columns) — extra UI-only fields
 * carry defaults so they don't need to exist server-side.
 */
@Serializable
data class Content(
    val id: String,
    @SerialName("community_id") val communityId: String = "",
    @SerialName("submitted_by") val submittedBy: String = "",
    val title: String = "",
    val description: String? = null,
    @SerialName("source_url") val sourceUrl: String = "",
    @SerialName("source_domain") val sourceDomain: String? = null,
    @SerialName("content_type") val contentType: String = "",   // "EVENT" | "RESOURCE"
    val category: String = "",                                  // content_category enum value
    val status: String = "",                                    // PROCESSING, PUBLISHED, PROCESSING_FAILED, EXPIRED, ARCHIVED, REMOVED
    @SerialName("safety_status") val safetyStatus: String? = null, // LOW_RISK, NEEDS_REVIEW, BLOCKED, UNKNOWN
    val organizer: String? = null,
    @SerialName("registration_deadline") val registrationDeadline: String? = null,
    @SerialName("start_at") val startDate: String? = null,
    @SerialName("end_at") val endDate: String? = null,
    val location: String? = null,
    @SerialName("is_online") val isOnline: Boolean? = null,
    @SerialName("registration_url") val registrationLink: String? = null,
    @SerialName("rules_url") val rulesLink: String? = null,
    val tags: List<String> = emptyList(),
    val priority: Int = 100,
    @SerialName("created_at") val createdAt: String = "",
    // ── UI-only (populated client-side, never read from the server) ──────────
    val daysLeft: Int? = null,
    val submittedByUsername: String? = null,
    val isBookmarked: Boolean = false
) {
    /** Derived from the is_online flag — the DB has no mode column. */
    val mode: String?
        get() = isOnline?.let { if (it) "ONLINE" else "OFFLINE" }
}
