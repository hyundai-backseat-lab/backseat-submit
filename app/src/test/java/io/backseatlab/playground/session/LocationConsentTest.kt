package io.backseatlab.playground.session

import io.backseatlab.playground.pleos.CarLocation
import io.backseatlab.playground.pleos.fake.FakePleosClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationConsentTest {
    @Test
    fun `동의 전에는 위치를 묻지 않고 동의하면 저장하고 위치를 준다`() =
        runTest {
            val here = CarLocation(37.57, 126.97, 180.0)
            val pleos = FakePleosClient().apply { currentLocation = here }
            val saved = mutableListOf<Boolean>()
            val consent = LocationConsent(false) { saved += it }

            assertNull(consent.locate(pleos))
            assertEquals(0, pleos.locationAsked)
            consent.grant()
            assertEquals(here, consent.locate(pleos))
            assertEquals(listOf(true), saved)
        }
}
