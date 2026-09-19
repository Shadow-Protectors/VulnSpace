package com.vulnspace.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Community(
    val id: String,
    val name: String,
    val description: String? = null,
    val status: String = "ACTIVE", // ACTIVE, SUSPENDED, ARCHIVED
    val headUserId: String? = null,
    val memberCount: Int = 0,
    val createdAt: String = ""
)

@Serializable
data class Member(
    val id: String,
    val userId: String,
    val communityId: String,
    val username: String,
    val role: String = "MEMBER", // MEMBER, COMMUNITY_HEAD
    val joinedAt: String = ""
)

@Serializable
data class InviteLink(
    val id: String,
    val communityId: String,
    val code: String,
    val status: String = "ACTIVE", // ACTIVE, REVOKED, EXPIRED
    val maxUses: Int? = null,
    val usedCount: Int = 0,
    val expiresAt: String? = null,
    val createdAt: String = ""
)

@Serializable
data class AppNotification(
    val id: String,
    val userId: String,
    val title: String,
    val body: String,
    val isRead: Boolean = false,
    val createdAt: String = ""
)

@Serializable
data class AuditLog(
    val id: String,
    val actorUserId: String,
    val actorUsername: String? = null,
    val action: String,
    val target: String? = null,
    val metadata: String? = null,
    val createdAt: String = ""
)
