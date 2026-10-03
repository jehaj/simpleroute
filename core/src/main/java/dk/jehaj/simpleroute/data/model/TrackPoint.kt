package dk.jehaj.simpleroute.data.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val ele: Double = 0.0,
    val distanceMeters: Double = 0.0
) {
    /**
     * Computes great-circle distance in meters between this point and another point using the Haversine formula.
     */
    fun distanceTo(other: TrackPoint): Double {
        return distanceBetween(lat, lon, other.lat, other.lon)
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6371000.0

        fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val rLat1 = Math.toRadians(lat1)
            val rLat2 = Math.toRadians(lat2)

            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return EARTH_RADIUS_METERS * c
        }

        fun bearingBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val rLat1 = Math.toRadians(lat1)
            val rLat2 = Math.toRadians(lat2)
            val dLon = Math.toRadians(lon2 - lon1)

            val y = sin(dLon) * cos(rLat2)
            val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
            val initialBearing = Math.toDegrees(atan2(y, x))
            return ((initialBearing + 360) % 360).toFloat()
        }
    }
}
