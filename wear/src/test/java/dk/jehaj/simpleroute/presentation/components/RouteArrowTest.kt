package dk.jehaj.simpleroute.presentation.components

import androidx.compose.ui.geometry.Offset
import dk.jehaj.simpleroute.data.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class RouteArrowTest {

    private val cx = 200f
    private val cy = 200f
    private val scale = 200f / 300f // 300m view radius
    private val metersPerDegLat = 111132.954
    private val metersPerDegLon = 111132.954 * kotlin.math.cos(Math.toRadians(56.0))

    private fun geoToScreen(lat: Double, lon: Double, centerLat: Double, centerLon: Double): Offset {
        val dx = ((lon - centerLon) * metersPerDegLon).toFloat()
        val dy = ((lat - centerLat) * metersPerDegLat).toFloat()
        return Offset(cx + dx * scale, cy - dy * scale)
    }

    @Test
    fun testArrowsHeadingNorthHaveUpwardVector() {
        // Track going North: 10 points spaced ~20m apart
        val baseLat = 56.0
        val baseLon = 10.0
        val points = (0..10).map { i ->
            TrackPoint(
                lat = baseLat + i * 0.0002,
                lon = baseLon,
                distanceMeters = i * 22.2
            )
        }

        val arrows = calculateRouteArrows(
            trackPoints = points,
            currentTrackIndex = 0,
            windowEnd = points.lastIndex,
            cx = cx,
            cy = cy,
            minDistanceFromCenter = 20f,
            maxDistanceFromCenter = 196f,
            arrowIntervalMeters = 40.0,
            maxLookaheadMeters = 300.0,
            geoToScreen = { lat, lon -> geoToScreen(lat, lon, baseLat, baseLon) }
        )

        assertFalse("Should generate route arrows", arrows.isEmpty())
        for (arrow in arrows) {
            // Heading North means uy < 0 (pointing towards top of screen)
            assertTrue("Arrow should point North (upwards on screen)", arrow.direction.y < -0.9f)
            assertEquals("Arrow horizontal component should be ~0", 0f, arrow.direction.x, 0.05f)
            val len = hypot(arrow.direction.x, arrow.direction.y)
            assertEquals("Direction vector must be normalized unit vector", 1f, len, 0.01f)
        }
    }

    @Test
    fun testArrowsHeadingEastHaveRightwardVector() {
        // Track going East: 10 points spaced ~20m apart
        val baseLat = 56.0
        val baseLon = 10.0
        val points = (0..10).map { i ->
            TrackPoint(
                lat = baseLat,
                lon = baseLon + i * 0.0003,
                distanceMeters = i * 20.0
            )
        }

        val arrows = calculateRouteArrows(
            trackPoints = points,
            currentTrackIndex = 0,
            windowEnd = points.lastIndex,
            cx = cx,
            cy = cy,
            minDistanceFromCenter = 20f,
            maxDistanceFromCenter = 196f,
            arrowIntervalMeters = 40.0,
            maxLookaheadMeters = 300.0,
            geoToScreen = { lat, lon -> geoToScreen(lat, lon, baseLat, baseLon) }
        )

        assertFalse("Should generate route arrows", arrows.isEmpty())
        for (arrow in arrows) {
            // Heading East means ux > 0 (pointing right on screen)
            assertTrue("Arrow should point East (rightwards on screen)", arrow.direction.x > 0.9f)
            assertEquals("Arrow vertical component should be ~0", 0f, arrow.direction.y, 0.05f)
            val len = hypot(arrow.direction.x, arrow.direction.y)
            assertEquals("Direction vector must be normalized unit vector", 1f, len, 0.01f)
        }
    }

    @Test
    fun testArrowsExcludedNearCenter() {
        val baseLat = 56.0
        val baseLon = 10.0
        val points = (0..10).map { i ->
            TrackPoint(
                lat = baseLat + i * 0.0002,
                lon = baseLon,
                distanceMeters = i * 22.2
            )
        }

        val minCenterDist = 50f
        val arrows = calculateRouteArrows(
            trackPoints = points,
            currentTrackIndex = 0,
            windowEnd = points.lastIndex,
            cx = cx,
            cy = cy,
            minDistanceFromCenter = minCenterDist,
            maxDistanceFromCenter = 196f,
            arrowIntervalMeters = 40.0,
            maxLookaheadMeters = 300.0,
            geoToScreen = { lat, lon -> geoToScreen(lat, lon, baseLat, baseLon) }
        )

        for (arrow in arrows) {
            val dist = hypot(arrow.position.x - cx, arrow.position.y - cy)
            assertTrue("Arrow must be outside minDistanceFromCenter", dist >= minCenterDist)
        }
    }

    @Test
    fun testMultipleArrowsInSingleLongSegment() {
        val baseLat = 56.0
        val baseLon = 10.0
        // Single 160m segment
        val points = listOf(
            TrackPoint(lat = baseLat, lon = baseLon, distanceMeters = 0.0),
            TrackPoint(lat = baseLat + 0.0015, lon = baseLon, distanceMeters = 160.0)
        )

        val arrows = calculateRouteArrows(
            trackPoints = points,
            currentTrackIndex = 0,
            windowEnd = 1,
            cx = cx,
            cy = cy,
            minDistanceFromCenter = 10f,
            maxDistanceFromCenter = 196f,
            arrowIntervalMeters = 40.0,
            maxLookaheadMeters = 300.0,
            geoToScreen = { lat, lon -> geoToScreen(lat, lon, baseLat, baseLon) }
        )

        // Expected targets: 40m, 80m, 120m, 160m -> at least 3 or 4 within bounds
        assertTrue("Long segment should contain multiple arrows", arrows.size >= 3)
    }

    @Test
    fun testEndOfRouteReturnsEmpty() {
        val points = listOf(
            TrackPoint(lat = 56.0, lon = 10.0, distanceMeters = 0.0),
            TrackPoint(lat = 56.01, lon = 10.0, distanceMeters = 100.0)
        )

        val arrows = calculateRouteArrows(
            trackPoints = points,
            currentTrackIndex = 1, // At last point
            windowEnd = 1,
            cx = cx,
            cy = cy,
            minDistanceFromCenter = 10f,
            maxDistanceFromCenter = 196f,
            geoToScreen = { lat, lon -> Offset(lat.toFloat(), lon.toFloat()) }
        )

        assertTrue("At end of route, no upcoming arrows should be returned", arrows.isEmpty())
    }

    @Test
    fun testDuplicateTrackPointsHandledGracefully() {
        val baseLat = 56.0
        val baseLon = 10.0
        val points = listOf(
            TrackPoint(lat = baseLat, lon = baseLon, distanceMeters = 0.0),
            TrackPoint(lat = baseLat, lon = baseLon, distanceMeters = 0.0),
            TrackPoint(lat = baseLat + 0.001, lon = baseLon, distanceMeters = 100.0)
        )

        val arrows = calculateRouteArrows(
            trackPoints = points,
            currentTrackIndex = 0,
            windowEnd = 2,
            cx = cx,
            cy = cy,
            minDistanceFromCenter = 10f,
            maxDistanceFromCenter = 196f,
            arrowIntervalMeters = 40.0,
            maxLookaheadMeters = 300.0,
            geoToScreen = { lat, lon -> geoToScreen(lat, lon, baseLat, baseLon) }
        )

        // Should not crash or infinite loop, and should produce arrows for valid segment
        assertTrue("Should process without errors and produce arrows", arrows.isNotEmpty())
    }
}
