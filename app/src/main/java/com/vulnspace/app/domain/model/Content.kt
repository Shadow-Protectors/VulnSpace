package com.vulnspace.app.domain.model

enum class ContentType {
    EVENT, RESOURCE
}

data class Content(
    val id: String,
    val title: String,
    val description: String?,
    val sourceUrl: String,
    val contentType: ContentType,
    val status: String,
    val priority: Int
)
