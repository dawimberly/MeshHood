package com.meshhood

enum class ChannelState { OFF, SEARCHING, ACTIVE, ERROR }

data class TransportState(
    val ble: ChannelState,
    val wifiDirect: ChannelState,
    val lan: ChannelState,
    val cellular: ChannelState,
    val neighborCount: Int,
    /** 0–4 bars from neighbor count + active transports. */
    val meshBars: Int,
)

enum class FeedKind { NEIGHBOR, SELF, SYSTEM, ICE, EMERGENCY, AGENCY }

enum class FeedSort { RECENT, NEARBY }

data class FeedDisplayParts(
    val time: String,
    val sender: String,
    val text: String,
)

data class FeedLine(
    val time: String,
    val sender: String,
    val text: String,
    val kind: FeedKind,
    val mapLat: Double? = null,
    val mapLon: Double? = null,
) {
    fun hasMapCoords(): Boolean = mapLat != null && mapLon != null
    fun displayParts(): FeedDisplayParts {
        val cleanTime = time.trim().removePrefix("[").removeSuffix("]")
        return FeedDisplayParts(
            time = cleanTime,
            sender = sender.trim(),
            text = text.trim(),
        )
    }

    fun displayLine(): String = if (time.isBlank()) {
        "$sender: $text"
    } else {
        "[$time] $sender: $text"
    }

    companion object {
        private val SYSTEM_SENDERS = setOf(
            "Group", "Status", "Photo", "Profile", "Reputation", "Admin",
            "Vouch", "EMERGENCY", "📌 Pin", "✓ Verified",
        )

        fun classify(sender: String, text: String, emergency: Boolean, agency: Boolean = false): FeedKind = when {
            agency -> FeedKind.AGENCY
            emergency -> FeedKind.EMERGENCY
            sender.contains("medical", ignoreCase = true) ||
                text.contains("Blood ", ignoreCase = true) ||
                text.contains("ICE contact", ignoreCase = true) -> FeedKind.ICE
            sender == "You" || sender.startsWith("You ") -> FeedKind.SELF
            sender in SYSTEM_SENDERS ||
                sender.startsWith("DM from") ||
                sender.startsWith("🩺") -> FeedKind.SYSTEM
            else -> FeedKind.NEIGHBOR
        }
    }
}
