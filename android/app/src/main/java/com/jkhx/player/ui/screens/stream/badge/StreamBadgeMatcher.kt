package com.jkhx.player.ui.screens.stream.badge

import com.jkhx.player.ui.screens.stream.UnifiedStreamItem

object StreamBadgeMatcher {

    @Volatile
    private var cachedRules: StreamBadgeRules? = null

    @Volatile
    private var cachedFilters: List<CompiledStreamBadgeFilter> = emptyList()

    fun getCompiledFilters(rules: StreamBadgeRules): List<CompiledStreamBadgeFilter> {
        val normalized = rules.normalized()
        val currentCachedRules = cachedRules
        if (currentCachedRules != null && currentCachedRules == normalized) {
            return cachedFilters
        }
        synchronized(this) {
            if (cachedRules == normalized) return cachedFilters
            val compiled = compile(normalized)
            cachedFilters = compiled
            cachedRules = normalized
            return compiled
        }
    }

    private fun compile(rules: StreamBadgeRules): List<CompiledStreamBadgeFilter> {
        if (!rules.hasImport) return emptyList()
        val active = rules.activeImport ?: return emptyList()
        return active.filters.mapNotNull { filter ->
            if (!filter.isEnabled || filter.name.isBlank() || filter.pattern.isBlank()) {
                return@mapNotNull null
            }
            val regex = runCatching { Regex(filter.pattern) }.getOrNull() ?: return@mapNotNull null
            CompiledStreamBadgeFilter(
                name = filter.name,
                badge = StreamBadge(
                    name = filter.name,
                    imageURL = filter.imageURL,
                    tagColor = filter.tagColor,
                    tagStyle = filter.tagStyle,
                    textColor = filter.textColor,
                    borderColor = filter.borderColor,
                ),
                regex = regex,
            )
        }
    }

    fun matchBadges(item: UnifiedStreamItem, filters: List<CompiledStreamBadgeFilter>): List<StreamBadge> {
        if (filters.isEmpty()) return emptyList()
        val candidates = extractCandidates(item)
        if (candidates.isEmpty()) return emptyList()

        val matched = linkedMapOf<String, StreamBadge>()
        filters.forEach { filter ->
            if (candidates.any { candidate -> filter.regex.containsMatchIn(candidate) }) {
                val key = filter.badge.dedupeKey()
                if (key !in matched) {
                    matched[key] = filter.badge
                }
            }
        }
        return matched.values.toList()
    }

    private fun extractCandidates(item: UnifiedStreamItem): List<String> {
        val list = listOfNotNull(
            item.serverName,
            item.title,
            item.techInfo,
            item.providerName,
            item.groupName,
            item.qualityBadge
        )
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        return if (list.size <= 1) {
            list
        } else {
            list + list.joinToString(" ")
        }
    }
}

