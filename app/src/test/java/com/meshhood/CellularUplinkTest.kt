package com.meshhood

import org.junit.Assert.assertEquals
import org.junit.Test

class CellularUplinkTest {

    @Test
    fun normalizeBaseUrl_trimsAndStripsTrailingSlash() {
        val uplink = CellularUplink(
            relayBaseUrl = { "" },
            relayToken = { "" },
            deviceId = { "dev" },
            isEnabled = { false },
            isDataReady = { false },
            onBytes = {},
            onActiveChanged = {},
        )
        assertEquals("https://relay.test", uplink.normalizeBaseUrl("  https://relay.test/  "))
        assertEquals("", uplink.normalizeBaseUrl("   "))
    }
}
