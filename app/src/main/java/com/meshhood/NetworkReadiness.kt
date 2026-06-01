package com.meshhood

/**
 * Network Readiness Score for the transport strip: mesh neighbors vs infrastructure only.
 */
enum class NetworkReadiness {
    Ready,
    Limited,
    Searching,
    Offline,
    ;

    companion object {
        fun compute(
            transport: TransportState,
            lanStatusLine: String,
            gatewayMode: Boolean,
        ): NetworkReadiness {
            if (transport.neighborCount > 0) return Ready
            if (gatewayConnected(transport, lanStatusLine, gatewayMode)) return Limited
            if (radiosSearching(transport)) return Searching
            return Offline
        }

        /** LAN hub or linked WiFi path without named mesh neighbors yet. */
        fun gatewayConnected(
            transport: TransportState,
            lanStatusLine: String,
            gatewayMode: Boolean,
        ): Boolean {
            if (transport.lan == ChannelState.ACTIVE) return true
            val lower = lanStatusLine.lowercase()
            if (lower.contains("linked")) return true
            if (lower.contains("gateway") && lower.contains("linked")) return true
            if (gatewayMode && transport.lan == ChannelState.SEARCHING) return true
            return false
        }

        private fun radiosSearching(transport: TransportState): Boolean =
            transport.ble == ChannelState.SEARCHING ||
                transport.wifiDirect == ChannelState.SEARCHING ||
                transport.lan == ChannelState.SEARCHING ||
                transport.cellular == ChannelState.SEARCHING
    }
}

fun NetworkReadiness.colorRes(): Int = when (this) {
    NetworkReadiness.Ready -> R.color.mesh_channel_active
    NetworkReadiness.Limited -> R.color.mesh_amber
    NetworkReadiness.Searching -> R.color.mesh_channel_search
    NetworkReadiness.Offline -> R.color.mesh_channel_error
}

fun NetworkReadiness.labelRes(): Int = when (this) {
    NetworkReadiness.Ready -> R.string.network_readiness_ready
    NetworkReadiness.Limited -> R.string.network_readiness_limited
    NetworkReadiness.Searching -> R.string.network_readiness_searching
    NetworkReadiness.Offline -> R.string.network_readiness_offline
}
