package com.meshhood

import org.json.JSONObject

/**
 * Geographic hierarchy for the public mesh area feed.
 * Broad → narrow: nation → national region → state → region → postal → local.
 */
enum class ZoneLevel(val key: String) {
    NATION("nation"),
    NATIONAL_REGION("national"),
    STATE("state"),
    REGION("region"),
    POSTAL("postal"),
    LOCAL("local");

    companion object {
        fun fromKey(key: String): ZoneLevel? = entries.firstOrNull { it.key == key }
    }
}

data class MeshZone(
    val nation: String = "",
    val nationalRegion: String = "",
    val state: String = "",
    val region: String = "",
    val postal: String = "",
    val local: String = "",
) {
    fun value(level: ZoneLevel): String = when (level) {
        ZoneLevel.NATION -> nation
        ZoneLevel.NATIONAL_REGION -> nationalRegion
        ZoneLevel.STATE -> state
        ZoneLevel.REGION -> region
        ZoneLevel.POSTAL -> postal
        ZoneLevel.LOCAL -> local
    }

    fun hasAny(): Boolean = ZoneLevel.entries.any { value(it).isNotBlank() }

    /** Finest configured level (most specific place the user has set). */
    fun finestLevel(): ZoneLevel? =
        ZoneLevel.entries.lastOrNull { value(it).isNotBlank() }

    fun scopeId(level: ZoneLevel): String {
        val v = value(level).trim()
        return "$SCOPE_PREFIX${level.key}:$v"
    }

    /** Default feed view: the most specific area the user configured. */
    fun defaultViewScope(): String = finestLevel()?.let { scopeId(it) } ?: MeshService.SCOPE_EVERYONE

    /** Dropdown options from broad to narrow (only filled levels). */
    fun options(): List<Pair<String, String>> =
        ZoneLevel.entries.mapNotNull { level ->
            val v = value(level).trim()
            if (v.isEmpty()) null else scopeId(level) to displayLabel(level)
        }

    /** Picker order: most specific locality first (ZIP/local → … → nation). */
    fun optionsMostSpecificFirst(): List<Pair<String, String>> = options().asReversed()

    fun displayLabel(level: ZoneLevel): String {
        val v = value(level).trim()
        return when (level) {
            ZoneLevel.NATION -> "Nation · $v"
            ZoneLevel.NATIONAL_REGION -> "National region · $v"
            ZoneLevel.STATE -> "State · $v"
            ZoneLevel.REGION -> "Region · $v"
            ZoneLevel.POSTAL -> "ZIP · $v"
            ZoneLevel.LOCAL -> "Local · $v"
        }
    }

    /** Compact header: human place name only (no "Local ·" prefix). */
    fun shortLabelForScope(scope: String): String {
        if (!isZoneScope(scope)) return scope
        val level = levelFromScope(scope) ?: return scope
        val v = valueFromScope(scope).ifBlank { value(level).trim() }
        if (v.isEmpty()) return scope
        return v
    }

    /** Header display — strips accidental "Local" prefixes and title-cases. */
    fun headerNameForScope(scope: String): String {
        if (!isZoneScope(scope)) return scope
        val level = levelFromScope(scope) ?: return scope
        val v = valueFromScope(scope).ifBlank { value(level).trim() }
        if (v.isEmpty()) return scope
        if (level == ZoneLevel.STATE) {
            return UsStates.displayName(v).ifBlank { formatHeaderWords(v) }
        }
        return formatHeaderWords(v)
    }

    /** Area picker row — short labels without redundant scope prefixes. */
    fun pickerLabelForScope(scope: String): String {
        if (!isZoneScope(scope)) return scope
        val level = levelFromScope(scope) ?: return scope
        val v = valueFromScope(scope).ifBlank { value(level).trim() }
        if (v.isEmpty()) return scope
        return when (level) {
            ZoneLevel.LOCAL -> formatHeaderWords(v)
            ZoneLevel.POSTAL -> "ZIP $v"
            ZoneLevel.STATE -> UsStates.displayName(v).ifBlank { formatHeaderWords(v) }
            ZoneLevel.REGION -> formatHeaderWords(v)
            ZoneLevel.NATIONAL_REGION -> formatHeaderWords(v)
            ZoneLevel.NATION -> formatHeaderWords(v)
        }
    }

    private fun formatHeaderWords(raw: String): String {
        val cleaned = raw
            .replace(Regex("(?i)^local\\s*[·.]?\\s*"), "")
            .trim()
            .ifBlank { raw }
        return cleaned.split("\\s+".toRegex())
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.titlecase() }
            }
    }

    fun labelForScope(scope: String): String {
        if (!isZoneScope(scope)) return scope
        val level = levelFromScope(scope) ?: return scope
        val v = valueFromScope(scope).ifBlank { value(level).trim() }
        if (v.isEmpty()) return scope
        return when (level) {
            ZoneLevel.NATION -> "Nation · $v"
            ZoneLevel.NATIONAL_REGION -> "National region · $v"
            ZoneLevel.STATE -> "State · $v"
            ZoneLevel.REGION -> "Region · $v"
            ZoneLevel.POSTAL -> "ZIP · $v"
            ZoneLevel.LOCAL -> "Local · $v"
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("nation", nation)
        put("national", nationalRegion)
        put("state", state)
        put("region", region)
        put("postal", postal)
        put("local", local)
    }

    companion object {
        const val SCOPE_PREFIX = "zone:"

        fun isZoneScope(scope: String): Boolean = scope.startsWith(SCOPE_PREFIX)

        fun levelFromScope(scope: String): ZoneLevel? {
            if (!isZoneScope(scope)) return null
            val body = scope.removePrefix(SCOPE_PREFIX)
            val levelKey = body.substringBefore(":", "")
            return ZoneLevel.fromKey(levelKey)
        }

        fun valueFromScope(scope: String): String {
            if (!isZoneScope(scope)) return ""
            return scope.removePrefix(SCOPE_PREFIX).substringAfter(":", "")
        }

        /** True if [scope] is a strictly broader geographic level than [finest]. */
        fun isBroaderThan(scope: String, finest: String): Boolean {
            if (!isZoneScope(scope) || !isZoneScope(finest)) return false
            val scopeLevel = levelFromScope(scope) ?: return false
            val finestLevel = levelFromScope(finest) ?: return false
            return scopeLevel.ordinal < finestLevel.ordinal
        }

        fun fromJson(obj: JSONObject?): MeshZone {
            if (obj == null) return MeshZone()
            return MeshZone(
                nation = obj.optString("nation", ""),
                nationalRegion = obj.optString("national", ""),
                state = obj.optString("state", ""),
                region = obj.optString("region", ""),
                postal = obj.optString("postal", ""),
                local = obj.optString("local", ""),
            )
        }

        /**
         * Whether [entryScope] should appear when viewing [viewScope].
         * Legacy everyone messages show in all area views; zone tags sort by proximity.
         */
        fun visibleInView(entryScope: String, viewScope: String): Boolean {
            return when {
                viewScope == MeshService.SCOPE_EVERYONE ->
                    entryScope == MeshService.SCOPE_EVERYONE || isZoneScope(entryScope)
                isZoneScope(viewScope) ->
                    entryScope == MeshService.SCOPE_EVERYONE || isZoneScope(entryScope)
                else -> entryScope == viewScope
            }
        }

        /** Lower rank = closer / more relevant (0 = exact view match). */
        fun proximityRank(entryScope: String, viewScope: String, home: MeshZone): Int {
            if (entryScope == MeshService.SCOPE_EVERYONE) return 50
            if (!isZoneScope(entryScope)) return 40
            if (isZoneScope(viewScope) && entryScope == viewScope) return 0
            val entryLevel = levelFromScope(entryScope) ?: return 35
            val viewLevel = when {
                isZoneScope(viewScope) -> levelFromScope(viewScope)
                else -> home.finestLevel()
            } ?: return 25
            val entryVal = valueFromScope(entryScope)
            val viewVal = if (isZoneScope(viewScope)) valueFromScope(viewScope) else ""
            if (entryLevel == viewLevel && entryVal == viewVal) return 0
            if (entryLevel.ordinal > viewLevel.ordinal) return 20 + (entryLevel.ordinal - viewLevel.ordinal) * 6
            return 8 + (viewLevel.ordinal - entryLevel.ordinal) * 6
        }
    }
}
