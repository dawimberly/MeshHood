package com.meshhood

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CoordinatorTest {

    @Before
    fun setUp() {
        Coordinator.reset()
    }

    @After
    fun tearDown() {
        Coordinator.reset()
    }

    @Test
    fun process_offerAndNeed_producesMatch() {
        assertTrue(
            Coordinator.process("Tariq", "I've got a generator. Can charge phones at 208 Oak.")
        )
        assertTrue(Coordinator.process("Maria", "My phone's at 12%."))

        val summary = Coordinator.summary()
        assertTrue(summary.contains("SUGGESTED MATCHES"))
        assertTrue(summary.contains("Maria"))
        assertTrue(summary.contains("Tariq"))
    }

    @Test
    fun process_emergency_detected() {
        assertTrue(
            Coordinator.process("Sue", "NEED HELP — tree fell on the car at 220 Oak!")
        )
        assertTrue(Coordinator.summary().contains("EMERGENCIES"))
        assertTrue(Coordinator.headline().contains("emergenc"))
    }

    @Test
    fun process_duplicateMessage_ignored() {
        val msg = "I have water bottles for anyone."
        assertTrue(Coordinator.process("Dev", msg))
        assertFalse(Coordinator.process("Dev", msg))
    }

    @Test
    fun process_noKeywords_returnsFalse() {
        assertFalse(Coordinator.process("Maria", "Power's out on our whole block. Everyone okay?"))
        assertFalse(Coordinator.hasContent())
    }

    @Test
    fun processParsed_offerRecorded() {
        assertTrue(
            Coordinator.processParsed(
                "Dev",
                "bringing cooler and ice",
                Coordinator.Intent.OFFER,
                setOf("cooling"),
                "212 Oak",
            )
        )
        assertTrue(Coordinator.summary().contains("AVAILABLE"))
    }

    @Test
    fun process_profileStyleOffer() {
        assertTrue(Coordinator.process("Dev", "offering propane stove, hot water"))
        assertTrue(Coordinator.summary().contains("AVAILABLE"))
    }
}
