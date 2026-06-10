package com.meshhood

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class JunctionBoxStoreTest {

    @Before
    fun reset() {
        JunctionBoxStore.setBoxesForTest(emptyList())
    }

    @Test
    fun store_exposesLoadedBoxes() {
        JunctionBoxStore.setBoxesForTest(
            listOf(
                JunctionBoxStore.JunctionBox(
                    id = "goleta-urban",
                    name = "Goleta urban gateway",
                    lat = 34.4358,
                    lon = -119.8276,
                    tier = JunctionBoxStore.Tier.URBAN,
                    status = JunctionBoxStore.Status.ACTIVE,
                    notes = "test",
                ),
                JunctionBoxStore.JunctionBox(
                    id = "sb-urban",
                    name = "Santa Barbara urban",
                    lat = 34.4208,
                    lon = -119.6982,
                    tier = JunctionBoxStore.Tier.URBAN,
                    status = JunctionBoxStore.Status.PLANNED,
                ),
            ),
        )
        assertEquals(2, JunctionBoxStore.count())
        assertEquals(JunctionBoxStore.Tier.URBAN, JunctionBoxStore.all()[0].tier)
        assertEquals(JunctionBoxStore.Status.ACTIVE, JunctionBoxStore.all()[0].status)
    }

    @Test
    fun productionAsset_hasAtLeastFiveSbCountyBoxes() {
        val text = loadProductionAssetText()
        val ids = Regex(""""id"\s*:\s*"([^"]+)"""").findAll(text).map { it.groupValues[1] }.toList()
        assertTrue("expected at least 5 junction boxes", ids.size >= 5)

        val lats = Regex(""""lat"\s*:\s*([\d.]+)""").findAll(text).map { it.groupValues[1].toDouble() }.toList()
        val lons = Regex(""""lon"\s*:\s*(-[\d.]+)""").findAll(text).map { it.groupValues[1].toDouble() }.toList()
        assertEquals(ids.size, lats.size)
        assertEquals(ids.size, lons.size)

        for (i in lats.indices) {
            assertTrue(JunctionBoxStore.isInSantaBarbaraCounty(lats[i], lons[i]))
            assertTrue(MapsHelper.hasUsableCoords(lats[i], lons[i]))
        }
    }

    @Test
    fun findNearest_returnsClosestBox() {
        JunctionBoxStore.setBoxesForTest(
            listOf(
                JunctionBoxStore.JunctionBox(
                    id = "goleta-urban",
                    name = "Goleta",
                    lat = 34.4358,
                    lon = -119.8276,
                    tier = JunctionBoxStore.Tier.URBAN,
                    status = JunctionBoxStore.Status.ACTIVE,
                ),
                JunctionBoxStore.JunctionBox(
                    id = "sb-urban",
                    name = "Santa Barbara",
                    lat = 34.4208,
                    lon = -119.6982,
                    tier = JunctionBoxStore.Tier.URBAN,
                    status = JunctionBoxStore.Status.PLANNED,
                ),
            ),
        )
        val nearest = JunctionBoxStore.findNearest(34.43, -119.83)
        assertNotNull(nearest)
        assertEquals("goleta-urban", nearest!!.id)
    }

    @Test
    fun distanceMeters_ordersBoxesByProximity() {
        val goletaLat = 34.4358
        val goletaLon = -119.8276
        val sbLat = 34.4208
        val sbLon = -119.6982
        val userLat = 34.43
        val userLon = -119.83
        val goletaDist = MapsHelper.distanceMeters(userLat, userLon, goletaLat, goletaLon)
        val sbDist = MapsHelper.distanceMeters(userLat, userLon, sbLat, sbLon)
        assertTrue(goletaDist < sbDist)
    }

    private fun loadProductionAssetText(): String {
        val path = Paths.get("src/main/assets/junction_boxes.json")
        if (Files.exists(path)) {
            return String(Files.readAllBytes(path))
        }
        val alt = Paths.get("app/src/main/assets/junction_boxes.json")
        assertTrue("junction_boxes.json missing", Files.exists(alt))
        return String(Files.readAllBytes(alt))
    }
}
