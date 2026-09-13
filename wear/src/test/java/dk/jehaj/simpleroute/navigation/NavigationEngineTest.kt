package dk.jehaj.simpleroute.navigation

import dk.jehaj.simpleroute.data.model.Route
import dk.jehaj.simpleroute.data.model.TrackPoint
import dk.jehaj.simpleroute.data.model.TurnCue
import dk.jehaj.simpleroute.data.model.TurnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationEngineTest {

    private fun createSyntheticRoute(): Route {
        // 10 trackpoints spaced roughly 10 meters apart heading North
        val baseLat = 56.0
        val baseLon = 10.0
        val points = (0..20).map { i ->
            TrackPoint(
                lat = baseLat + i * 0.0001,
                lon = baseLon,
                ele = 50.0 + i,
                distanceMeters = i * 11.1
            )
        }
        val cues = listOf(
            TurnCue(
                turn = TurnType.TR,
                turnAngle = 90f,
                offset = 10,
                description = "Turn right",
                lat = points[10].lat,
                lon = points[10].lon,
                distanceFromStartMeters = points[10].distanceMeters
            ),
            TurnCue(
                turn = TurnType.TL,
                turnAngle = -90f,
                offset = 18,
                description = "Turn left",
                lat = points[18].lat,
                lon = points[18].lon,
                distanceFromStartMeters = points[18].distanceMeters
            )
        )
        return Route(
            name = "Test Route",
            fileName = "test.gpx",
            trackPoints = points,
            turnCues = cues,
            totalDistanceMeters = points.last().distanceMeters
        )
    }

    @Test
    fun testAlertRuleSpeedSamplingAndClamping() {
        val route = createSyntheticRoute()
        val engine = NavigationEngine(route)

        // Point 0: stopped (speed 0.0) -> effective speed 4.5 -> trigger distance 45.0m
        val (state0, _) = engine.processLocation(route.trackPoints[0].lat, route.trackPoints[0].lon, 0.0f, 0f)
        assertEquals(45.0, state0.triggerDistanceMeters, 0.01)

        // Low speed (0.5 m/s) -> effective speed 4.5 -> trigger distance 45.0m
        val (stateLow, _) = engine.processLocation(route.trackPoints[0].lat, route.trackPoints[0].lon, 0.5f, 0f)
        assertEquals(45.0, stateLow.triggerDistanceMeters, 0.01)

        // Slow cycling (2.0 m/s) -> 20m, clamped to min 25m
        val (stateSlow, _) = engine.processLocation(route.trackPoints[0].lat, route.trackPoints[0].lon, 2.0f, 0f)
        assertEquals(25.0, stateSlow.triggerDistanceMeters, 0.01)

        // Fast descent (12.0 m/s) -> 120m, clamped to max 90m
        val (stateFast, _) = engine.processLocation(route.trackPoints[0].lat, route.trackPoints[0].lon, 12.0f, 0f)
        assertEquals(90.0, stateFast.triggerDistanceMeters, 0.01)
    }

    @Test
    fun testAlertFiresOncePerTurnOffset() {
        val route = createSyntheticRoute()
        val engine = NavigationEngine(route)

        // Cue 1 is at offset 10 (dist ~111m)
        // At point 0 (dist 0m): dist to cue is 111m > 45m trigger -> no alert
        val (_, alert0) = engine.processLocation(route.trackPoints[0].lat, route.trackPoints[0].lon, 4.5f, 0f)
        assertNull("Should not alert far from turn", alert0)

        // At point 5 (dist ~55.5m): dist to cue is ~55.5m > 45m -> no alert
        val (_, alert5) = engine.processLocation(route.trackPoints[5].lat, route.trackPoints[5].lon, 4.5f, 0f)
        assertNull("Should not alert at point 5", alert5)

        // At point 7 (dist ~77.7m): dist to cue is ~33.3m <= 45m trigger -> ALERT!
        val (_, alert7) = engine.processLocation(route.trackPoints[7].lat, route.trackPoints[7].lon, 4.5f, 0f)
        assertNotNull("Should alert at point 7", alert7)
        assertEquals(10, alert7!!.cue.offset)
        assertEquals(TurnType.TR, alert7.cue.turn)

        // At point 8 (dist ~88.8m): dist to cue is ~22.2m <= 45m -> should NOT alert again!
        val (state8, alert8) = engine.processLocation(route.trackPoints[8].lat, route.trackPoints[8].lon, 4.5f, 0f)
        assertNull("Should NOT alert twice for same cue", alert8)
        assertEquals(10, state8.lastAlertedCueOffset)

        // At point 10 (at turn junction): pass turn
        val (_, alert10) = engine.processLocation(route.trackPoints[10].lat, route.trackPoints[10].lon, 4.5f, 0f)
        assertNull("No alert at turn junction", alert10)

        // Advance to point 11: now next cue is cue 2 (offset 18)
        val (state11, _) = engine.processLocation(route.trackPoints[11].lat, route.trackPoints[11].lon, 4.5f, 0f)
        assertEquals(18, state11.nextCue?.offset)

        // Approach cue 2 at point 15 (offset 18 is dist 199.8m, pt 15 is 166.5m -> dist to cue 33.3m <= 45m) -> ALERT for cue 2!
        val (_, alert15) = engine.processLocation(route.trackPoints[15].lat, route.trackPoints[15].lon, 4.5f, 0f)
        assertNotNull("Should alert for cue 2", alert15)
        assertEquals(18, alert15!!.cue.offset)
        assertEquals(TurnType.TL, alert15.cue.turn)
    }
}
