package com.jkhx.player.ui.screens.stream.badge

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val STREAM_BADGE_IMPORT_LIMIT = 3

enum class StreamBadgePlacement(val key: String) {
    BOTTOM("bottom"),
    TOP("top");

    companion object {
        fun fromKey(key: String?): StreamBadgePlacement =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: BOTTOM
    }
}

@Serializable
data class StreamBadgeRules(
    val imports: List<StreamBadgeImport> = emptyList(),
) {
    val hasImport: Boolean
        get() = imports.isNotEmpty()

    val activeImport: StreamBadgeImport?
        get() = imports.firstOrNull { it.isActive } ?: imports.firstOrNull()

    val enabledFilterCount: Int
        get() = activeImport?.enabledFilterCount ?: 0

    fun normalized(): StreamBadgeRules {
        val normalizedImports = mutableListOf<StreamBadgeImport>()
        imports.forEach { import ->
            val normalizedUrl = import.sourceUrl.trim()
            if (normalizedUrl.isBlank() || import.filters.isEmpty()) return@forEach
            val normalizedImport = import.copy(sourceUrl = normalizedUrl)
            val existingIndex = normalizedImports.indexOfFirst { it.sourceUrl.equals(normalizedUrl, ignoreCase = true) }
            if (existingIndex >= 0) {
                normalizedImports[existingIndex] = normalizedImport
            } else if (normalizedImports.size < STREAM_BADGE_IMPORT_LIMIT) {
                normalizedImports += normalizedImport
            }
        }
        if (normalizedImports.isEmpty()) return copy(imports = emptyList())
        val activeIndex = normalizedImports.indexOfFirst { it.isActive }.takeIf { it >= 0 } ?: 0
        return copy(
            imports = normalizedImports.mapIndexed { index, import ->
                import.copy(isActive = index == activeIndex)
            }
        )
    }

    fun upsert(import: StreamBadgeImport, activate: Boolean = true): StreamBadgeRules {
        val normalizedUrl = import.sourceUrl.trim()
        if (normalizedUrl.isBlank()) return normalized()
        val normalizedImport = import.copy(sourceUrl = normalizedUrl, isActive = activate)
        val replaced = imports.map { existing ->
            if (existing.sourceUrl.equals(normalizedUrl, ignoreCase = true)) normalizedImport else existing
        }
        val nextImports = if (imports.any { it.sourceUrl.equals(normalizedUrl, ignoreCase = true) }) {
            replaced
        } else {
            imports + normalizedImport
        }
        val activeImports = if (activate) {
            nextImports.map { existing ->
                existing.copy(isActive = existing.sourceUrl.equals(normalizedUrl, ignoreCase = true))
            }
        } else {
            nextImports
        }
        return copy(imports = activeImports).normalized()
    }

    fun setActiveSource(sourceUrl: String): StreamBadgeRules {
        val normalizedUrl = sourceUrl.trim()
        if (normalizedUrl.isBlank() || imports.none { it.sourceUrl.equals(normalizedUrl, ignoreCase = true) }) {
            return normalized()
        }
        return copy(
            imports = imports.map { import ->
                import.copy(isActive = import.sourceUrl.equals(normalizedUrl, ignoreCase = true))
            }
        ).normalized()
    }

    fun removeSource(sourceUrl: String): StreamBadgeRules =
        copy(imports = imports.filterNot { it.sourceUrl.equals(sourceUrl.trim(), ignoreCase = true) }).normalized()
}

@Serializable
data class StreamBadgeImport(
    val sourceUrl: String = "",
    val filters: List<StreamBadgeFilter> = emptyList(),
    val groups: List<StreamBadgeGroup> = emptyList(),
    val isActive: Boolean = true,
) {
    val enabledFilterCount: Int
        get() = filters.count { it.isEnabled }
}

@Serializable
data class StreamBadgeFilter(
    val id: String = "",
    val groupId: String = "",
    val name: String = "",
    val pattern: String = "",
    val imageURL: String = "",
    val isEnabled: Boolean = true,
    val tagColor: String = "",
    val tagStyle: String = "",
    val textColor: String = "",
    val borderColor: String = "",
)

@Serializable
data class StreamBadgeGroup(
    val id: String = "",
    val name: String = "",
    val color: String = "",
    val borderColor: String = "",
    val isExpanded: Boolean = true,
)

@Serializable
data class StreamBadge(
    val name: String,
    val imageURL: String,
    val tagColor: String = "",
    val tagStyle: String = "",
    val textColor: String = "",
    val borderColor: String = "",
) {
    fun dedupeKey(): String = (imageURL.takeIf { it.isNotBlank() } ?: name).lowercase()
}

data class CompiledStreamBadgeFilter(
    val name: String,
    val badge: StreamBadge,
    val regex: Regex,
)

internal object StreamBadgeJsonHelper {
    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        encodeDefaults = true
    }

    fun parsePayload(sourceUrl: String, payload: String): StreamBadgeImport {
        val decoded = try {
            json.decodeFromString<StreamBadgePayload>(payload)
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid badge JSON: ${error.message.orEmpty()}")
        }

        val filters = decoded.filters.mapNotNull { filter ->
            val name = filter.name.orEmpty().trim()
            val pattern = filter.pattern.orEmpty().trim()
            if (name.isBlank() || pattern.isBlank()) {
                return@mapNotNull null
            }

            StreamBadgeFilter(
                id = filter.id.orEmpty(),
                groupId = filter.groupId.orEmpty(),
                name = name,
                pattern = pattern,
                imageURL = filter.imageURL.orEmpty(),
                isEnabled = filter.isEnabled ?: true,
                tagColor = filter.tagColor.orEmpty(),
                tagStyle = filter.tagStyle.orEmpty(),
                textColor = filter.textColor.orEmpty(),
                borderColor = filter.borderColor.orEmpty(),
            )
        }

        if (filters.isEmpty()) {
            throw IllegalArgumentException("Badge import không chứa bộ lọc nào hợp lệ.")
        }

        val groups = decoded.groups.map { group ->
            StreamBadgeGroup(
                id = group.id.orEmpty(),
                name = group.name.orEmpty(),
                color = group.color.orEmpty(),
                borderColor = group.borderColor.orEmpty(),
                isExpanded = group.isExpanded ?: true,
            )
        }

        return StreamBadgeImport(
            sourceUrl = sourceUrl.trim(),
            filters = filters,
            groups = groups,
            isActive = true
        )
    }

    fun encodeRules(rules: StreamBadgeRules): String {
        return json.encodeToString(rules)
    }

    fun decodeRules(jsonStr: String): StreamBadgeRules {
        if (jsonStr.isBlank()) return StreamBadgeRules()
        return try {
            json.decodeFromString<StreamBadgeRules>(jsonStr).normalized()
        } catch (_: Exception) {
            StreamBadgeRules()
        }
    }
}

@Serializable
private data class StreamBadgePayload(
    val filters: List<StreamBadgeFilterPayload> = emptyList(),
    val groups: List<StreamBadgeGroupPayload> = emptyList(),
)

@Serializable
private data class StreamBadgeFilterPayload(
    val id: String? = null,
    val groupId: String? = null,
    val name: String? = null,
    val pattern: String? = null,
    val imageURL: String? = null,
    val isEnabled: Boolean? = null,
    val tagColor: String? = null,
    val tagStyle: String? = null,
    val textColor: String? = null,
    val borderColor: String? = null,
    val type: String? = null,
)

@Serializable
private data class StreamBadgeGroupPayload(
    val id: String? = null,
    val name: String? = null,
    val color: String? = null,
    val borderColor: String? = null,
    val isExpanded: Boolean? = null,
)

