package dk.jehaj.simpleroute.presentation.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dk.jehaj.simpleroute.data.model.TrackPoint
import kotlin.math.ceil
import kotlin.math.hypot

data class RouteArrow(
    val position: Offset,
    val direction: Offset // Unit vector (ux, uy) in screen coordinate space
)

/**
 * Calculates directional arrows along the upcoming route polyline.
 *
 * Arrows are placed at fixed cumulative distance milestones along the route so that they
 * remain geographically anchored to the path as the rider moves. Arrows within [minDistanceFromCenter]
 * (to avoid overlapping the rider chevron) and outside [maxDistanceFromCenter] (off-screen)
 * are excluded.
 */
fun calculateRouteArrows(
    trackPoints: List<TrackPoint>,
    currentTrackIndex: Int,
    windowEnd: Int,
    cx: Float,
    cy: Float,
    minDistanceFromCenter: Float,
    maxDistanceFromCenter: Float,
    arrowIntervalMeters: Double = 70.0,
    maxLookaheadMeters: Double = 900.0,
    geoToScreen: (lat: Double, lon: Double) -> Offset
): List<RouteArrow> {
    if (trackPoints.isEmpty() || currentTrackIndex >= windowEnd) return emptyList()

    val currentDist = trackPoints.getOrNull(currentTrackIndex)?.distanceMeters ?: return emptyList()
    val firstTargetDist = ceil(currentDist / arrowIntervalMeters) * arrowIntervalMeters
    val maxTargetDist = currentDist + maxLookaheadMeters

    val arrows = mutableListOf<RouteArrow>()
    var targetDist = firstTargetDist
    var i = currentTrackIndex

    while (i < windowEnd && targetDist <= maxTargetDist) {
        val p1 = trackPoints[i]
        val p2 = trackPoints[i + 1]
        val d1 = p1.distanceMeters
        val d2 = p2.distanceMeters

        if (d2 <= d1) {
            i++
            continue
        }

        if (targetDist < d1) {
            targetDist += arrowIntervalMeters
            continue
        }

        if (targetDist <= d2) {
            val t = ((targetDist - d1) / (d2 - d1)).toFloat().coerceIn(0f, 1f)
            val lat = p1.lat + t * (p2.lat - p1.lat)
            val lon = p1.lon + t * (p2.lon - p1.lon)
            val sArrow = geoToScreen(lat, lon)

            val distFromCenter = hypot(sArrow.x - cx, sArrow.y - cy)
            if (distFromCenter in minDistanceFromCenter..maxDistanceFromCenter) {
                val s1 = geoToScreen(p1.lat, p1.lon)
                val s2 = geoToScreen(p2.lat, p2.lon)
                val dx = s2.x - s1.x
                val dy = s2.y - s1.y
                val segLen = hypot(dx, dy)

                if (segLen > 0.001f) {
                    val ux = dx / segLen
                    val uy = dy / segLen
                    arrows.add(RouteArrow(sArrow, Offset(ux, uy)))
                }
            }

            targetDist += arrowIntervalMeters
        } else {
            i++
        }
    }

    return arrows
}

/**
 * Draws directional chevrons along the upcoming route polyline in a single batched draw call.
 */
fun DrawScope.drawRouteArrows(
    arrows: List<RouteArrow>,
    isAmbient: Boolean
) {
    if (arrows.isEmpty()) return

    val arrowSize = if (isAmbient) 5.dp.toPx() else 5.8.dp.toPx()
    val path = Path()

    for (arrow in arrows) {
        val px = arrow.position.x
        val py = arrow.position.y
        val ux = arrow.direction.x
        val uy = arrow.direction.y
        val nx = -uy
        val ny = ux

        val tipX = px + ux * arrowSize
        val tipY = py + uy * arrowSize
        val leftX = px - ux * (0.55f * arrowSize) + nx * (0.75f * arrowSize)
        val leftY = py - uy * (0.55f * arrowSize) + ny * (0.75f * arrowSize)
        val notchX = px - ux * (0.15f * arrowSize)
        val notchY = py - uy * (0.15f * arrowSize)
        val rightX = px - ux * (0.55f * arrowSize) - nx * (0.75f * arrowSize)
        val rightY = py - uy * (0.55f * arrowSize) - ny * (0.75f * arrowSize)

        path.moveTo(tipX, tipY)
        path.lineTo(leftX, leftY)
        path.lineTo(notchX, notchY)
        path.lineTo(rightX, rightY)
        path.close()
    }

    if (!isAmbient) {
        // Active mode: high-contrast solid white fill with deep dark teal/black outline
        drawPath(path = path, color = Color.White)
        drawPath(
            path = path,
            color = Color(0xFF001F2B),
            style = Stroke(width = 1.2.dp.toPx(), join = StrokeJoin.Round)
        )
    } else {
        // Ambient mode: power-efficient black fill cutting into the white trail, outlined in white
        drawPath(path = path, color = Color.Black)
        drawPath(
            path = path,
            color = Color.White,
            style = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round)
        )
    }
}
