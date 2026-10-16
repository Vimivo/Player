package dev.vimivo.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class SmartRuleField {
    TITLE,
    ARTIST,
    ALBUM,
    ALBUM_ARTIST,
    GENRE,
    DURATION_MS,
    PLAY_COUNT,
    IS_FAVORITE,
    MEDIA_KIND,
    RELEASE_YEAR,
    DATE_ADDED,
}

@Serializable
enum class SmartRuleOperator {
    EQUALS,
    CONTAINS,
    STARTS_WITH,
    GREATER_THAN,
    LESS_THAN,
    IS_TRUE,
    IS_FALSE,
}

@Serializable
data class SmartRuleCondition(
    val field: SmartRuleField,
    val operator: SmartRuleOperator,
    val value: String = "",
)

@Serializable
enum class SmartRuleMatchMode {
    ALL,
    ANY,
}

@Serializable
data class SmartPlaylistDefinition(
    val id: String,
    val name: String,
    val conditions: List<SmartRuleCondition>,
    val matchMode: SmartRuleMatchMode = SmartRuleMatchMode.ALL,
    val sortField: SmartRuleField = SmartRuleField.TITLE,
    val sortDescending: Boolean = false,
    val limit: Int? = null,
    val createdAtEpochMillis: Long = 0,
    val updatedAtEpochMillis: Long = 0,
)
