package dk.jehaj.simpleroute.data.model

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

data class RouteBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
) {
    val centerLat: Double get() = (minLat + maxLat) / 2.0
    val centerLon: Double get() = (minLon + maxLon) / 2.0

    /**
     * Converts a geo coordinate (lat, lon) to a screen [Offset] within a canvas of [canvasWidth] x [canvasHeight],
     * preserving geographic aspect ratio and leaving [paddingPx] padding around the edges.
     */
    fun getProjector(
        canvasWidth: Float,
        canvasHeight: Float,
        paddingPx: Float
    ): (lat: Double, lon: Double) -> Offset {
        val usableWidth = max(10f, canvasWidth - 2 * paddingPx)
        val usableHeight = max(10f, canvasHeight - 2 * paddingPx)

        val midLatRad = Math.toRadians(centerLat)
        val cosLat = max(0.01, cos(midLatRad))
        val metersPerDegLat = 111132.954
        val metersPerDegLon = 111132.954 * cosLat

        // Clamping min span to ~100m to avoid division by zero or extreme zoom on tiny/zero routes
        val minSpanMeters = 100.0
        val rawWidthMeters = (maxLon - minLon) * metersPerDegLon
        val rawHeightMeters = (maxLat - minLat) * metersPerDegLat

        val routeWidthMeters = max(minSpanMeters, rawWidthMeters)
        val routeHeightMeters = max(minSpanMeters, rawHeightMeters)

        val scaleX = usableWidth / routeWidthMeters.toFloat()
        val scaleY = usableHeight / routeHeightMeters.toFloat()
        val scale = min(scaleX, scaleY)

        val cx = canvasWidth / 2f
        val cy = canvasHeight / 2f

        val cLon = centerLon
        val cLat = centerLat

        return { lat: Double, lon: Double ->
            val dx = ((lon - cLon) * metersPerDegLon).toFloat()
            val dy = ((lat - cLat) * metersPerDegLat).toFloat()
            // Y increases downwards on screen
            Offset(cx + dx * scale, cy - dy * scale)
        }
    }

    companion object {
        private const val DEFAULT_MIN_SPAN_DEG = 0.001 // ~110m

        fun compute(
            trackPoints: List<TrackPoint>,
            waypoints: List<WayPoint> = emptyList()
        ): RouteBounds {
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE
            var maxLon = -Double.MAX_VALUE

            fun include(lat: Double, lon: Double) {
                if (lat < minLat) minLat = lat
                if (lat > maxLat) maxLat = lat
                if (lon < minLon) minLon = lon
                if (lon > maxLon) maxLon = lon
            }

            for (tp in trackPoints) {
                include(tp.lat, tp.lon)
            }
            for (wp in waypoints) {
                include(wp.lat, wp.lon)
            }

            if (minLat > maxLat || minLon > maxLon) {
                // Empty fallback (center around 0,0)
                return RouteBounds(
                    minLat = -DEFAULT_MIN_SPAN_DEG / 2,
                    maxLat = DEFAULT_MIN_SPAN_DEG / 2,
                    minLon = -DEFAULT_MIN_SPAN_DEG / 2,
                    maxLon = DEFAULT_MIN_SPAN_DEG / 2
                )
            }

            // Ensure bounding box has a non-zero minimum span
            val spanLat = maxLat - minLat
            val spanLon = maxLon - minLon

            if (spanLat < DEFAULT_MIN_SPAN_DEG) {
                val diff = (DEFAULT_MIN_SPAN_DEG - spanLat) / 2
                minLat -= diff
                maxLat += diff
            }
            if (spanLon < DEFAULT_MIN_SPAN_DEG) {
                val diff = (DEFAULT_MIN_SPAN_DEG - spanLon) / 2
                minLon -= diff
                maxLon += diff
            }

            return RouteBounds(
                minLat = minLat,
                maxLat = maxLat,
                minLon = minLon,
                maxLon = maxLon
            )
        }
    }
}
