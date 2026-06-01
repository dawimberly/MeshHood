package com.meshhood

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AgencyTrustTest {

    private val sig = "QCo0f0vCk+JrXeANAdwUWuj8tZieoxT80b4X5p3oloOdEAxMt03PrMpiipQ886w3Ig4XZxkZ94TX4MxiGqtLBA=="
    private val text = "Shelter open at City Hall until 8pm"
    private val ts = 1700000000000L

    @Before
    fun seedTrust() {
        AgencyTrust.setAgenciesForTest(
            mapOf(
                "demo-county-em" to AgencyTrust.Agency(
                    id = "demo-county-em",
                    label = "Demo County EM",
                    pubkey = "/ENwY9J05qfmm/rF5ioRpo+83qe4+COF1dI4yAJzZN0=",
                ),
            ),
        )
    }

    @Test
    fun validSignatureAccepted() {
        val agency = AgencyTrust.verifyMessage("demo-county-em", sig, text, ts)
        assertEquals("demo-county-em", agency?.id)
        assertEquals("Demo County EM", agency?.label)
    }

    @Test
    fun tamperedTextRejected() {
        assertNull(AgencyTrust.verifyMessage("demo-county-em", sig, "Evacuate immediately", ts))
    }
}
