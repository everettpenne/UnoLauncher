package com.jake.duolauncher

import android.location.LocationManager
import kotlin.math.roundToLong

/** How "use my location" for sunrise and sunset picks where to ask, kept pure so it is tested. */
internal object LocationChoice {
    /** The providers to try, best first, among those that are switched on. GrapheneOS and other Google-free systems usually have no
     * network or fused provider at all, so the phone's GPS (which needs the precise-location permission, used here once) is the one
     * that works; the passive provider cannot answer a one-off request, so it only serves cached fixes.
     */
    fun providers(enabled: Set<String>, preciseAllowed: Boolean): List<String> = buildList {
        if (LocationManager.FUSED_PROVIDER in enabled) add(LocationManager.FUSED_PROVIDER)
        if (LocationManager.NETWORK_PROVIDER in enabled) add(LocationManager.NETWORK_PROVIDER)
        if (preciseAllowed && LocationManager.GPS_PROVIDER in enabled) add(LocationManager.GPS_PROVIDER)
    }

    /** A GPS fix indoors can take a while; the others answer quickly. */
    fun timeoutMs(provider: String): Long = if (provider == LocationManager.GPS_PROVIDER) 45_000L else 12_000L

    /** Only a fix this recent is reused without asking again. */
    const val CACHE_MAX_AGE_MS = 6 * 60 * 60_000L

    /** Sunrise and sunset need a place only to about a tenth of a degree (around 11 km, a minute or two of daylight), so even a precise
     * fix is rounded to that before it is kept: the saved place is never more exact than the feature needs.
     */
    fun coarsen(degrees: Double): Double = (degrees * 10.0).roundToLong() / 10.0
}
