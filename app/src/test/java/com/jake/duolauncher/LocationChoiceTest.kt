package com.jake.duolauncher

import android.location.LocationManager
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationChoiceTest {
    private val all = setOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)

    @Test fun networkAndFusedComeBeforeGps() {
        assertEquals(listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER), LocationChoice.providers(all, true))
    }

    @Test fun gpsIsOnlyUsedWithPreciseLocationAllowed() {
        assertEquals(listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER), LocationChoice.providers(all, false))
        assertEquals(listOf(LocationManager.GPS_PROVIDER), LocationChoice.providers(setOf(LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER), true))
    }

    @Test fun aGoogleFreePhoneWithoutPreciseHasNothingToAsk() {
        assertEquals(emptyList<String>(), LocationChoice.providers(setOf(LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER), false))
        assertEquals(emptyList<String>(), LocationChoice.providers(emptySet(), true))
    }

    @Test fun gpsGetsALongerWait() {
        assertEquals(true, LocationChoice.timeoutMs(LocationManager.GPS_PROVIDER) > LocationChoice.timeoutMs(LocationManager.NETWORK_PROVIDER))
    }

    @Test fun placesAreRoundedToATenthOfADegree() {
        assertEquals(51.5, LocationChoice.coarsen(51.50741), 1e-9)
        assertEquals(-0.1, LocationChoice.coarsen(-0.12776), 1e-9)
        assertEquals(40.7, LocationChoice.coarsen(40.74), 1e-9)
        assertEquals(40.8, LocationChoice.coarsen(40.75), 1e-9)
    }
}
