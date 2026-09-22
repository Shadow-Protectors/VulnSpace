package com.vulnspace.app.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class HeadApplicationSubmissionRequest(
    val fullName: String,
    val email: String,
    val phone: String? = null,
    val organization: String,
    val proposedCommunityName: String,
    val proposedDescription: String,
    val reason: String
)

@Serializable
data class HeadApplicationSubmissionResponse(
    val applicationId: String,
    val status: String,
    val trackingToken: String,
    val expiresAt: String,
    val communityName: String? = null
)

@Serializable
data class HeadApplicationStatusCheckRequest(
    val trackingToken: String
)

@Serializable
data class HeadApplicationStatusCheckResponse(
    val status: String,
    val applicationId: String? = null,
    val communityName: String? = null,
    val nextAction: String? = null,
    val reason: String? = null,
    val error: String? = null
)

@Serializable
data class ClaimHeadGrantResponse(
    val claimed: Boolean = false,
    @SerialName("already_head") val alreadyHead: Boolean = false,
    @SerialName("community_id") val communityId: String? = null,
    @SerialName("community_name") val communityName: String? = null,
    val username: String? = null,
    val message: String? = null,
    val error: String? = null
)
