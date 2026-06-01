package com.meshhood

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapsHelperTest {

    @Test
    fun hasUsableCoords_rejectsNullAndOrigin() {
        assertFalse(MapsHelper.hasUsableCoords(null, -119.8))
        assertFalse(MapsHelper.hasUsableCoords(34.4, null))
        assertFalse(MapsHelper.hasUsableCoords(0.0, 0.0))
    }

    @Test
    fun hasUsableCoords_acceptsGoleta() {
        assertTrue(MapsHelper.hasUsableCoords(34.4358, -119.8276))
    }

    @Test
    fun parseCoordsFromText_findsEmbeddedPair() {
        val parsed = MapsHelper.parseCoordsFromText("NEED HELP at 34.43580, -119.82764")
        assertTrue(parsed != null)
        assertTrue(parsed!!.first in 34.0..35.0)
        assertTrue(parsed.second < -119.0)
    }

    @Test
    fun offlineMapsCenterUrl_centersAtGoletaWithCityZoom() {
        val url = MapsHelper.offlineMapsCenterUrl(34.4358, -119.8276)
        assertTrue(url.contains("map_action=map"))
        assertTrue(url.contains("center=34.4358,-119.8276"))
        assertTrue(url.contains("zoom=14"))
        assertTrue(url.contains("api=1"))
    }
}
