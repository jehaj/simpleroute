package dk.jehaj.simpleroute.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteBoundsTest {

    @Test
    fun testComputeIncludesTrackpointsAndWaypoints() {
        val trackPoints = listOf(
            TrackPoint(lat = 56.10, lon = 10.10),
            TrackPoint(lat = 56.20, lon = 10.20)
        )
        val waypoints = listOf(
            WayPoint(name = "Detour", lat = 56.30, lon = 10.05)
        )

        val bounds = RouteBounds.compute(trackPoints, waypoints)
        assertEquals(56.10, bounds.minLat, 0.0001)
        assertEquals(56.30, bounds.maxLat, 0.0001)
        assertEquals(10.05, bounds.minLon, 0.0001)
        assertEquals(10.20, bounds.maxLon, 0.0001)

        val projector = bounds.getProjector(canvasWidth = 400f, canvasHeight = 400f, paddingPx = 20f)
        val p1 = projector(56.10, 10.10)
        val p2 = projector(56.30, 10.05)

        // All points should stay within padded canvas bounds [20, 380]
        assertTrue(p1.x in 19f..381f)
        assertTrue(p1.y in 19f..381f)
        assertTrue(p2.x in 19f..381f)
        assertTrue(p2.y in 19f..381f)
    }

    @Test
    fun testZeroSpanOutAndBackSinglePoint() {
        // Out-and-back route or single coordinate test
        val trackPoints = listOf(
            TrackPoint(lat = 56.18, lon = 10.16),
            TrackPoint(lat = 56.18, lon = 10.16)
        )
        val bounds = RouteBounds.compute(trackPoints, emptyList())
        assertTrue("Span Lat should be non-zero", bounds.maxLat > bounds.minLat)
        assertTrue("Span Lon should be non-zero", bounds.maxLon > bounds.minLon)

        val projector = bounds.getProjector(canvasWidth = 300f, canvasHeight = 300f, paddingPx = 20f)
        val pt = projector(56.18, 10.16)
        assertEquals(150f, pt.x, 1f)
        assertEquals(150f, pt.y, 1f)
    }
}
