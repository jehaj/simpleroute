package dk.jehaj.simpleroute.data.parser

import dk.jehaj.simpleroute.data.model.TurnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class GpxParserTest {

    @Test
    fun testParseBrendstrupAarhus() {
        val file = File("../../Brendstrup - Aarhus.gpx").let {
            if (it.exists()) it else File("Brendstrup - Aarhus.gpx").let { f ->
                if (f.exists()) f else File("../Brendstrup - Aarhus.gpx")
            }
        }
        assertTrue("Test file must exist at ${file.absolutePath}", file.exists())

        val parser = GpxParser()
        val route = FileInputStream(file).use { stream ->
            parser.parse(stream, file.name)
        }

        assertNotNull(route)
        println("Route: ${route.name}")
        println("Track points: ${route.trackPoints.size}")
        println("Turn cues: ${route.turnCues.size}")
        println("Total distance: ${route.totalDistanceMeters} m")
        println("Ascent: ${route.totalAscentMeters} m, Descent: ${route.totalDescentMeters} m")

        assertTrue("Trackpoints should be > 500", route.trackPoints.size > 500)
        assertTrue("Turn cues should be > 10", route.turnCues.size > 10)

        // The GPX comment says track-length = 29406 m
        assertTrue("Total distance should be around 29.4 km", route.totalDistanceMeters in 28000.0..31000.0)

        // First cue with turn in the file:
        // <rtept lat="56.182159" lon="10.164952"><desc>right</desc><extensions><turn>TR</turn><turn-angle>90</turn-angle><offset>3</offset></extensions></rtept>
        val trCue = route.turnCues.firstOrNull { it.turn == TurnType.TR }
        assertNotNull("Should have TR cue", trCue)
        assertEquals(3, trCue!!.offset)
        assertEquals(90f, trCue.turnAngle, 0.1f)
        assertEquals("right", trCue.description)
        assertTrue(trCue.turn.isRight)

        // Monotonic distances
        for (i in 1 until route.trackPoints.size) {
            assertTrue(route.trackPoints[i].distanceMeters >= route.trackPoints[i - 1].distanceMeters)
        }
    }
}
