package com.meshhood

import org.junit.Assert.assertEquals
import org.junit.Test

class GatewayHeadlessKeysTest {
    @Test
    fun prefsAlignWithMeshServiceStore() {
        assertEquals("meshhood_store", GatewayHeadlessKeys.PREFS_NAME)
        assertEquals("gateway_headless", GatewayHeadlessKeys.KEY_HEADLESS)
    }
}
