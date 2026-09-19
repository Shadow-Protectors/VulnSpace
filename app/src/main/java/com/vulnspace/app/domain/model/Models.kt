package com.vulnspace.app.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement

@Serializable
data class Community(
    val id: String,
    val name: String,
    val description: String? = null,
    val status: String = "ACTIVE", // ACTIVE, SUSPENDED, ARCHIVED
    @SerialName("created_by") val headUserId: String? = null,
    val memberCount: Int = 0,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class Member(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("community_id") val communityId: String,
    val username: String,
    val role: String = "MEMBER", // MEMBER, HEAD, COMMUNITY_HEAD
    @SerialName("joined_at") val joinedAt: String = ""
)

@Serializable
data class InviteLink(
    val id: String,
    @SerialName("community_id") val communityId: String,
    @SerialName("token_hash") val tokenHash: String,
    @SerialName("max_uses") val maxUses: Int? = null,
    @SerialName("uses") val usedCount: Int = 0,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("revoked_at") val revokedAt: String? = null,
    @SerialName("created_at") val createdAt: String = ""
) {
    // invite_links has no `status` column — derive from revocation/expiry/uses
    val isActive: Boolean
        get() = revokedAt == null && (maxUses == null || usedCount < maxUses)
}

@Serializable
data class AppNotification(
    val id: String,
    @SerialName("recipient_user_id") val userId: String = "",
    val title: String,
    val body: String,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String = ""
) {
    val isRead: Boolean get() = readAt != null
}

@Serializable
data class AuditLog(
    val id: String,
    @SerialName("actor_id") val actorUserId: String = "",
    val actorUsername: String? = null,
    val action: String,
    @SerialName("target_type") val target: String? = null,
    val metadata: JsonElement? = null,
    @SerialName("created_at") val createdAt: String = ""
)
