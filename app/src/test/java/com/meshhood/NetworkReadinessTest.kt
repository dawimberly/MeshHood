package com.meshhood

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkReadinessTest {

    private fun ts(
        neighbors: Int = 0,
        lan: ChannelState = ChannelState.OFF,
        ble: ChannelState = ChannelState.OFF,
    ) = TransportState(
        ble = ble,
        wifiDirect = ChannelState.OFF,
        lan = lan,
        cellular = ChannelState.OFF,
        neighborCount = neighbors,
        meshBars = neighbors.coerceAtMost(4),
    )

    @Test
    fun readyWhenNeighborsPresent() {
        assertEquals(
            NetworkReadiness.Ready,
            NetworkReadiness.compute(ts(neighbors = 1), "", false),
        )
    }

    @Test
    fun limitedWhenLanActiveNoNeighbors() {
        assertEquals(
            NetworkReadiness.Limited,
            NetworkReadiness.compute(ts(lan = ChannelState.ACTIVE), "", false),
        )
    }

    @Test
    fun limitedWhenLanStatusLinked() {
        assertEquals(
            NetworkReadiness.Limited,
            NetworkReadiness.compute(ts(), "WiFi LAN: 1 linked", false),
        )
    }

    @Test
    fun searchingWhenRadiosActiveNoInfra() {
        assertEquals(
            NetworkReadiness.Searching,
            NetworkReadiness.compute(ts(ble = ChannelState.SEARCHING), "", false),
        )
    }

    @Test
    fun offlineWhenNothingActive() {
        assertEquals(
            NetworkReadiness.Offline,
            NetworkReadiness.compute(ts(), "", false),
        )
    }
}
