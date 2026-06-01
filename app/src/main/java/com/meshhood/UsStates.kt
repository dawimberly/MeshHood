package com.meshhood

/** US states and DC for area profile spinners (stored as two-letter abbreviations). */
object UsStates {
    fun abbrFromName(name: String): String {
        val idx = indexOf(name)
        return if (idx > 0) abbrAt(idx) else ""
    }

    fun displayName(abbr: String): String {
        val raw = abbr.trim()
        if (raw.isEmpty()) return ""
        return entries.firstOrNull { it.first.equals(raw, ignoreCase = true) }?.second ?: raw
    }

    private val entries = listOf(
        "AL" to "Alabama",
        "AK" to "Alaska",
        "AZ" to "Arizona",
        "AR" to "Arkansas",
        "CA" to "California",
        "CO" to "Colorado",
        "CT" to "Connecticut",
        "DE" to "Delaware",
        "DC" to "District of Columbia",
        "FL" to "Florida",
        "GA" to "Georgia",
        "HI" to "Hawaii",
        "ID" to "Idaho",
        "IL" to "Illinois",
        "IN" to "Indiana",
        "IA" to "Iowa",
        "KS" to "Kansas",
        "KY" to "Kentucky",
        "LA" to "Louisiana",
        "ME" to "Maine",
        "MD" to "Maryland",
        "MA" to "Massachusetts",
        "MI" to "Michigan",
        "MN" to "Minnesota",
        "MS" to "Mississippi",
        "MO" to "Missouri",
        "MT" to "Montana",
        "NE" to "Nebraska",
        "NV" to "Nevada",
        "NH" to "New Hampshire",
        "NJ" to "New Jersey",
        "NM" to "New Mexico",
        "NY" to "New York",
        "NC" to "North Carolina",
        "ND" to "North Dakota",
        "OH" to "Ohio",
        "OK" to "Oklahoma",
        "OR" to "Oregon",
        "PA" to "Pennsylvania",
        "RI" to "Rhode Island",
        "SC" to "South Carolina",
        "SD" to "South Dakota",
        "TN" to "Tennessee",
        "TX" to "Texas",
        "UT" to "Utah",
        "VT" to "Vermont",
        "VA" to "Virginia",
        "WA" to "Washington",
        "WV" to "West Virginia",
        "WI" to "Wisconsin",
        "WY" to "Wyoming",
    )

    const val PLACEHOLDER = "Select state..."

    fun labels(): List<String> =
        listOf(PLACEHOLDER) + entries.map { (abbr, name) -> "$name ($abbr)" }

    fun abbrAt(index: Int): String {
        if (index <= 0) return ""
        return entries.getOrNull(index - 1)?.first ?: ""
    }

    fun indexOf(abbrOrName: String): Int {
        val raw = abbrOrName.trim()
        if (raw.isEmpty()) return 0
        entries.forEachIndexed { i, (abbr, name) ->
            if (abbr.equals(raw, ignoreCase = true) ||
                name.equals(raw, ignoreCase = true) ||
                raw.equals("$name ($abbr)", ignoreCase = true)
            ) {
                return i + 1
            }
        }
        return 0
    }
}
