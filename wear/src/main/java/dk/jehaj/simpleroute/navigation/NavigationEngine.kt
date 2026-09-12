package dk.jehaj.simpleroute.navigation

import dk.jehaj.simpleroute.data.model.Route
import dk.jehaj.simpleroute.data.model.TrackPoint
import dk.jehaj.simpleroute.data.model.TurnCue
import kotlin.math.max
import kotlin.math.min

data class AlertEvent(
    val cue: TurnCue,
    val triggerDistanceMeters: Double,
    val timestamp: Long = System.currentTimeMillis()
)

data class NavigationState(
    val route: Route? = null,
    val currentTrackIndex: Int = 0,
    val currentLatitude: Double = 0.0,
    val currentLongitude: Double = 0.0,
    val currentElevation: Double = 0.0,
    val currentSpeedMps: Float = 0.0f,
    val currentBearingDeg: Float = 0.0f,
    val distanceRemainingMeters: Double = 0.0,
    val distanceTraveledMeters: Double = 0.0,
    val offRouteDistanceMeters: Double = 0.0,
    val nextCue: TurnCue? = null,
    val distanceToNextCueMeters: Double = 0.0,
    val triggerDistanceMeters: Double = 25.0,
    val lastAlertedCueOffset: Int? = null,
    val latestAlertEvent: AlertEvent? = null,
    val isNavigating: Boolean = false
)

class NavigationEngine(initialRoute: Route? = null) {

    var route: Route? = initialRoute
        private set

    private var currentIndex = 0
    private var lastAlertedOffset: Int? = null

    val currentPointIndex: Int get() = currentIndex

    fun reset() {
        currentIndex = 0
        lastAlertedOffset = null
    }

    fun loadRoute(newRoute: Route) {
        route = newRoute
        currentIndex = 0
        lastAlertedOffset = null
    }

    /**
     * Updates navigation state with a new GPS location.
     * Returns the updated NavigationState and an optional AlertEvent if a turn threshold was crossed.
     */
    fun processLocation(
        lat: Double,
        lon: Double,
        speedMps: Float,
        bearingDeg: Float,
        elevation: Double = 0.0
    ): Pair<NavigationState, AlertEvent?> {
        val currentRoute = route ?: return NavigationState() to null
        val trackPoints = currentRoute.trackPoints

        if (trackPoints.isEmpty()) {
            return NavigationState(route = currentRoute, isNavigating = true) to null
        }

        // 1. Sliding window index of rider's position using closest Euclidean distance
        val newIndex = findClosestTrackPointIndex(lat, lon, trackPoints, currentIndex)
        currentIndex = newIndex

        val matchedPoint = trackPoints[currentIndex]
        val offRouteDistance = TrackPoint.distanceBetween(lat, lon, matchedPoint.lat, matchedPoint.lon)

        // 2. Compute route progress
        val distanceTraveled = matchedPoint.distanceMeters
        val distanceRemaining = max(0.0, currentRoute.totalDistanceMeters - distanceTraveled)

        // 3. Find next cue (cue with offset > currentIndex)
        val nextCue = currentRoute.turnCues.firstOrNull { it.offset > currentIndex }
        val distanceToNextCue = if (nextCue != null) {
            max(0.0, nextCue.distanceFromStartMeters - distanceTraveled)
        } else {
            0.0
        }

        // 4. 10-Second Dynamic Alert Rule:
        // Speed sampling: if moving under 1.0 m/s, default to 4.5 m/s (~16 km/h)
        val effectiveSpeed = if (speedMps < 1.0f) 4.5f else speedMps
        val triggerDistance = (effectiveSpeed * 10.0).coerceIn(25.0, 90.0)

        var newAlertEvent: AlertEvent? = null

        if (nextCue != null) {
            // Check if distance to cue is within trigger threshold
            if (distanceToNextCue <= triggerDistance) {
                // Fire once per turn offset, resetting only when current location advances past turn offset
                if (lastAlertedOffset != nextCue.offset) {
                    lastAlertedOffset = nextCue.offset
                    newAlertEvent = AlertEvent(nextCue, triggerDistance)
                }
            }
        }

        val state = NavigationState(
            route = currentRoute,
            currentTrackIndex = currentIndex,
            currentLatitude = lat,
            currentLongitude = lon,
            currentElevation = if (elevation != 0.0) elevation else matchedPoint.ele,
            currentSpeedMps = speedMps,
            currentBearingDeg = bearingDeg,
            distanceRemainingMeters = distanceRemaining,
            distanceTraveledMeters = distanceTraveled,
            offRouteDistanceMeters = offRouteDistance,
            nextCue = nextCue,
            distanceToNextCueMeters = distanceToNextCue,
            triggerDistanceMeters = triggerDistance,
            lastAlertedCueOffset = lastAlertedOffset,
            latestAlertEvent = newAlertEvent,
            isNavigating = true
        )

        return state to newAlertEvent
    }

    private fun findClosestTrackPointIndex(
        lat: Double,
        lon: Double,
        trackPoints: List<TrackPoint>,
        lastIndex: Int
    ): Int {
        val windowStart = max(0, lastIndex - 10)
        val windowEnd = min(trackPoints.lastIndex, lastIndex + 40)

        var bestIndex = lastIndex
        var minDistance = Double.MAX_VALUE

        for (i in windowStart..windowEnd) {
            val pt = trackPoints[i]
            val d = TrackPoint.distanceBetween(lat, lon, pt.lat, pt.lon)
            if (d < minDistance) {
                minDistance = d
                bestIndex = i
            }
        }

        // If off-route or far from window, check full route to allow route re-entry
        if (minDistance > 120.0 && trackPoints.size > windowEnd) {
            for (i in trackPoints.indices) {
                val pt = trackPoints[i]
                val d = TrackPoint.distanceBetween(lat, lon, pt.lat, pt.lon)
                if (d < minDistance) {
                    minDistance = d
                    bestIndex = i
                }
            }
        }

        return bestIndex
    }
}
