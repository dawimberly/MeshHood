package com.meshhood

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class AgencySignerTest {

    private val privB64 = "/qYKxWEA2+QNx0m+s+lsQODpEJ8+Vd0XUmPYIcFxsTs="

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
    fun signAlertVerifiesWithAgencyTrust() {
        val agencyId = "demo-county-em"
        val text = "Shelter open at City Hall until 8pm"
        val ts = 1700000000000L
        val sig = AgencySigner.signAgencyMessage(agencyId, text, privB64, ts)
        assertNotNull(sig)
        val agency = AgencyTrust.verifyMessage(agencyId, sig!!, text, ts)
        assertEquals(agencyId, agency?.id)
        assertEquals(text, text)
    }
}
