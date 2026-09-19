package com.vulnspace.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class HeadApplication(
    val id: String,
    val applicant_user_id: String,
    val full_name: String,
    val email: String,
    val phone: String? = null,
    val organization: String,
    val proposed_community_name: String,
    val proposed_description: String,
    val reason: String,
    val status: String, // PENDING, APPROVED, REJECTED, SUSPENDED
    val rejection_reason: String? = null,
    val created_at: String
)
