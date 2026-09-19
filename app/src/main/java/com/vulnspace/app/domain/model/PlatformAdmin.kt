package com.vulnspace.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PlatformAdmin(
    val id: String,
    val user_id: String,
    val created_at: String
)
