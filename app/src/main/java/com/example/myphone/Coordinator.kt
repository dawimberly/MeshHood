package com.example.myphone

/**
 * The MeshHood "Coordinator" — the on-device brain that turns a noisy
 * neighborhood feed into an actionable picture during a disaster.
 *
 * It reads each broadcast and works out:
 *   - is this an OFFER ("I have a generator") or a NEED ("my phone's at 12%")?
 *   - what resource categories does it touch (power, water, medical, ...)?
 *   - where (best-effort location)?
 * Then it matches open needs to available offers and surfaces emergencies.
 *
 * This is a fast, fully-offline rule-based parser — no model download, instant,
 * private. It's deliberately structured so a real on-device LLM (e.g. Gemma via
 * MediaPipe) can later replace `classify()` / `categoriesIn()` for far better
 * language understanding, while the matching + summary stay the same.
 */
object Coordinator {

    enum class Intent { OFFER, NEED, NONE }

    data class Entry(
        val from: String,
        val text: String,
        val categories: Set<String>,
        val location: String?,
    )

    // category key -> trigger words
    private val categoryKeywords: Map<String, List<String>> = linkedMapOf(
        "power" to listOf("power", "generator", "charge", "charging", "battery", "outlet", "electric", "genny"),
        "water" to listOf("water", "hydration", "bottled"),
        "medical" to listOf("insulin", "medicine", "meds", "medication", "prescription", "doctor",
            "first aid", "bandage", "epipen", "inhaler", "oxygen", "diabetic"),
        "cooling" to listOf("ice", "cooler", "refrigerat", "fridge", "cold pack"),
        "food" to listOf("food", "meal", "hungry", "formula", "diaper", "snack", "canned", "groceries"),
        "fuel" to listOf("gas ", "fuel", "propane", "gasoline", "diesel", "kerosene"),
        "shelter" to listOf("shelter", "spare room", "place to stay", "roof", "somewhere to sleep"),
        "warmth" to listOf("blanket", "heater", "firewood", "warm clothes"),
        "transport" to listOf("ride", "car", "truck", "drive", "transport", "vehicle", "evacuate"),
        "tools" to listOf("crowbar", "chainsaw", "ladder", "tools", "shovel", "rope"),
    )

    private val categoryLabels: Map<String, String> = mapOf(
        "power" to "power/charging", "water" to "water", "medical" to "medical",
        "cooling" to "ice/cooling", "food" to "food", "fuel" to "fuel",
        "shelter" to "shelter", "warmth" to "warmth/heat", "transport" to "transport",
        "tools" to "tools",
    )

    private val offerMarkers = listOf(
        "i have", "i've got", "ive got", "i got", "have a", "have some", "have an",
        "have plenty", "got a", "got some", "offering", "can offer", "can provide",
        "can charge", "can share", "available", "bringing", "stocked", "plenty of",
        "happy to", "feel free",
    )
    private val needMarkers = listOf(
        "need", "needs", "looking for", "anyone have", "anyone got", "out of",
        "require", "low on", "running low", "could use", "anyone with",
        "desperate for", "short on", "ran out",
    )

    private val offers = mutableListOf<Entry>()
    private val needs = mutableListOf<Entry>()
    private val emergencies = mutableListOf<Entry>()
    private val signatures = HashSet<String>()

    fun reset() {
        offers.clear(); needs.clear(); emergencies.clear(); signatures.clear()
    }

    /** Process one broadcast message with the fast rule-based parser. */
    fun process(from: String, text: String): Boolean {
        val sig = "$from|$text"
        if (!signatures.add(sig)) return false

        val lower = text.lowercase()
        val isEmergency = lower.contains("need help") || lower.contains("🚨")
        val cats = categoriesIn(lower).toMutableSet()

        // "phone at 12%" style low-battery => an implicit power need.
        val pct = Regex("(\\d{1,3})\\s*%").find(lower)?.groupValues?.get(1)?.toIntOrNull()
        val lowBattery = pct != null && pct <= 25
        if (lowBattery) cats.add("power")

        val intent = when {
            offerMarkers.any { lower.contains(it) } -> Intent.OFFER
            needMarkers.any { lower.contains(it) } || lowBattery -> Intent.NEED
            else -> Intent.NONE
        }
        return record(from, text, intent, cats, extractLocation(text), isEmergency)
    }

    /**
     * Process one message using a classification produced by the on-device LLM.
     * Emergencies are still detected by keyword for reliability.
     */
    fun processParsed(
        from: String,
        text: String,
        intent: Intent,
        categories: Set<String>,
        location: String?,
    ): Boolean {
        val sig = "$from|$text"
        if (!signatures.add(sig)) return false
        val lower = text.lowercase()
        val isEmergency = lower.contains("need help") || lower.contains("🚨")
        val loc = location ?: extractLocation(text)
        return record(from, text, intent, categories.toSet(), loc, isEmergency)
    }

    private fun record(
        from: String,
        text: String,
        intent: Intent,
        cats: Set<String>,
        location: String?,
        isEmergency: Boolean,
    ): Boolean {
        if (isEmergency) {
            emergencies.add(Entry(from, text, cats, location))
            return true
        }
        if (cats.isEmpty()) return false
        return when (intent) {
            Intent.OFFER -> { offers.add(Entry(from, text, cats, location)); true }
            Intent.NEED -> { needs.add(Entry(from, text, cats, location)); true }
            Intent.NONE -> false
        }
    }

    private fun categoriesIn(lower: String): Set<String> {
        val found = linkedSetOf<String>()
        for ((cat, words) in categoryKeywords) {
            if (words.any { lower.contains(it) }) found.add(cat)
        }
        return found
    }

    private fun extractLocation(text: String): String? {
        // e.g. "212 Oak", "220 Oak St", "Maple Ave"
        Regex("\\b\\d{1,5}\\s+[A-Z][a-zA-Z]+(\\s(St|Ave|Rd|Blvd|Ln|Dr|Street|Avenue))?")
            .find(text)?.let { return it.value.trim() }
        // "at <Place>"
        Regex("\\bat\\s+([A-Z][a-zA-Z0-9 ]{2,20})").find(text)?.let { return it.groupValues[1].trim() }
        return null
    }

    private fun labels(cats: Set<String>): String =
        cats.mapNotNull { categoryLabels[it] }.joinToString(", ").ifEmpty { "general" }

    /** True if there's anything worth showing. */
    fun hasContent(): Boolean = offers.isNotEmpty() || needs.isNotEmpty() || emergencies.isNotEmpty()

    /** A human-readable coordination summary for the UI / notifications. */
    fun summary(): String {
        if (!hasContent()) {
            return "No needs or offers detected yet.\n\nAs neighbors post things like " +
                "\"I have a generator\" or \"need insulin\", MeshHood will match them here."
        }
        val sb = StringBuilder()

        if (emergencies.isNotEmpty()) {
            sb.append("🚨 EMERGENCIES (${emergencies.size})\n")
            for (e in emergencies.takeLast(5)) {
                val where = e.location?.let { " — $it" } ?: ""
                sb.append("  • ${e.from}: ${e.text.removePrefix("🚨").trim()}$where\n")
            }
            sb.append("\n")
        }

        val matches = matches()
        if (matches.isNotEmpty()) {
            sb.append("✅ SUGGESTED MATCHES (${matches.size})\n")
            for (m in matches) {
                sb.append("  • ${m.need.from} needs ${labels(m.shared)} ⇄ ${m.offer.from} has it")
                m.offer.location?.let { sb.append(" ($it)") }
                sb.append("\n")
            }
            sb.append("\n")
        }

        val openNeeds = needs.filter { n -> matches.none { it.need === n } }
        if (openNeeds.isNotEmpty()) {
            sb.append("🔴 OPEN NEEDS (${openNeeds.size})\n")
            for (n in openNeeds.takeLast(8)) {
                sb.append("  • ${n.from}: ${labels(n.categories)}")
                n.location?.let { sb.append(" @ $it") }
                sb.append("\n")
            }
            sb.append("\n")
        }

        if (offers.isNotEmpty()) {
            sb.append("🟢 AVAILABLE (${offers.size})\n")
            for (o in offers.takeLast(8)) {
                sb.append("  • ${o.from}: ${labels(o.categories)}")
                o.location?.let { sb.append(" @ $it") }
                sb.append("\n")
            }
        }
        return sb.toString().trimEnd()
    }

    data class Match(val need: Entry, val offer: Entry, val shared: Set<String>)

    private fun matches(): List<Match> {
        val result = mutableListOf<Match>()
        val usedOffers = HashSet<Entry>()
        for (n in needs) {
            for (o in offers) {
                if (o.from == n.from || o in usedOffers) continue
                val shared = n.categories intersect o.categories
                if (shared.isNotEmpty()) {
                    result.add(Match(n, o, shared))
                    usedOffers.add(o)
                    break
                }
            }
        }
        return result
    }

    fun counts(): Triple<Int, Int, Int> = Triple(needs.size, offers.size, emergencies.size)

    /** A short one-line status for the triage notification. */
    fun headline(): String {
        val ms = matches()
        val open = needs.count { n -> ms.none { it.need === n } }
        val emer = emergencies.size
        val parts = mutableListOf<String>()
        if (emer > 0) parts.add("🚨 $emer emergenc${if (emer > 1) "ies" else "y"}")
        if (ms.isNotEmpty()) parts.add("✅ ${ms.size} match${if (ms.size > 1) "es" else ""} ready")
        if (open > 0) parts.add("🔴 $open unmet need${if (open > 1) "s" else ""}")
        if (offers.isNotEmpty()) parts.add("🟢 ${offers.size} offering")
        return if (parts.isEmpty()) "No needs yet" else parts.joinToString("  ·  ")
    }
}
