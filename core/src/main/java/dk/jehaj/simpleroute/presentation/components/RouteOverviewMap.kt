package dk.jehaj.simpleroute.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dk.jehaj.simpleroute.data.model.RouteBounds
import dk.jehaj.simpleroute.data.model.TrackPoint
import dk.jehaj.simpleroute.data.model.WayPoint
import kotlin.math.hypot

@Composable
fun RouteOverviewMap(
    trackPoints: List<TrackPoint>,
    waypoints: List<WayPoint> = emptyList(),
    modifier: Modifier = Modifier,
    isAmbient: Boolean = false,
    routeColor: Color = Color(0xFF00E5FF),
    startColor: Color = Color(0xFF00E676),
    endColor: Color = Color(0xFFFF1744),
    waypointColor: Color = Color(0xFFFFB300),
    padding: Dp = 22.dp
) {
    if (trackPoints.isEmpty()) return

    val bounds = remember(trackPoints, waypoints) {
        RouteBounds.compute(trackPoints, waypoints)
    }

    // Identify start, end, and intermediate waypoints
    val (startWpt, endWpt, intermediateWpts) = remember(waypoints, trackPoints) {
        resolveWaypoints(waypoints, trackPoints)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val paddingPx = padding.toPx()
        val projector = bounds.getProjector(size.width, size.height, paddingPx)

        // 1. Build and draw route polyline
        val routePath = Path()
        var pathStarted = false
        val screenPoints = ArrayList<Offset>(trackPoints.size)

        for (pt in trackPoints) {
            val offset = projector(pt.lat, pt.lon)
            screenPoints.add(offset)
            if (!pathStarted) {
                routePath.moveTo(offset.x, offset.y)
                pathStarted = true
            } else {
                routePath.lineTo(offset.x, offset.y)
            }
        }

        // Background shadow / casing for route trail
        if (!isAmbient && screenPoints.size >= 2) {
            drawPath(
                path = routePath,
                color = Color(0xFF00363A),
                style = Stroke(
                    width = 5.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // Main route trail
        drawPath(
            path = routePath,
            color = if (isAmbient) Color.White else routeColor,
            style = Stroke(
                width = if (isAmbient) 2.5.dp.toPx() else 3.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 2. Draw directional arrows along the path
        drawOverviewDirectionArrows(screenPoints, isAmbient)

        // 3. Draw intermediate waypoints
        for (wp in intermediateWpts) {
            val wpOffset = projector(wp.lat, wp.lon)
            drawIntermediateWaypoint(wpOffset, waypointColor, isAmbient)
        }

        // 4. Draw Start and End Markers
        val rawStartOffset = projector(startWpt.lat, startWpt.lon)
        val rawEndOffset = projector(endWpt.lat, endWpt.lon)

        // Check if start and end are close together (e.g. out-and-back route)
        val distBetweenStartAndEnd = hypot(rawEndOffset.x - rawStartOffset.x, rawEndOffset.y - rawStartOffset.y)
        val minOverlapDistancePx = 22.dp.toPx()

        if (distBetweenStartAndEnd < minOverlapDistancePx) {
            // Offset start slightly to the left/up and end slightly to the right/down so both are distinctly visible
            val shift = 10.dp.toPx()
            val adjustedStart = Offset(rawStartOffset.x - shift, rawStartOffset.y - shift)
            val adjustedEnd = Offset(rawEndOffset.x + shift, rawEndOffset.y + shift)

            // Draw link line between the co-located spot and displaced badges
            drawLine(
                color = Color.Gray,
                start = adjustedStart,
                end = adjustedEnd,
                strokeWidth = 1.dp.toPx()
            )

            drawStartMarker(adjustedStart, startColor, isAmbient)
            drawEndMarker(adjustedEnd, endColor, isAmbient)
        } else {
            drawStartMarker(rawStartOffset, startColor, isAmbient)
            drawEndMarker(rawEndOffset, endColor, isAmbient)
        }
    }
}

private data class ResolvedPoints(
    val start: WayPoint,
    val end: WayPoint,
    val intermediates: List<WayPoint>
)

private fun resolveWaypoints(
    waypoints: List<WayPoint>,
    trackPoints: List<TrackPoint>
): ResolvedPoints {
    val defaultStart = trackPoints.firstOrNull()?.let {
        WayPoint(name = "Start", lat = it.lat, lon = it.lon, type = "from")
    } ?: WayPoint(name = "Start", lat = 0.0, lon = 0.0)

    val defaultEnd = trackPoints.lastOrNull()?.let {
        WayPoint(name = "Finish", lat = it.lat, lon = it.lon, type = "to")
    } ?: WayPoint(name = "Finish", lat = 0.0, lon = 0.0)

    if (waypoints.isEmpty()) {
        return ResolvedPoints(defaultStart, defaultEnd, emptyList())
    }

    val startCandidate = waypoints.firstOrNull { it.isStart } ?: waypoints.first()
    val endCandidate = waypoints.lastOrNull { it.isEnd } ?: waypoints.last()

    val intermediates = waypoints.filter { it !== startCandidate && it !== endCandidate }

    return ResolvedPoints(startCandidate, endCandidate, intermediates)
}

private fun DrawScope.drawStartMarker(center: Offset, color: Color, isAmbient: Boolean) {
    val outerRadius = 7.dp.toPx()
    val innerRadius = 4.dp.toPx()

    if (!isAmbient) {
        // Outer glow aura
        drawCircle(
            color = color.copy(alpha = 0.35f),
            radius = outerRadius * 1.5f,
            center = center
        )
        // Outer ring
        drawCircle(
            color = Color.Black,
            radius = outerRadius,
            center = center
        )
        drawCircle(
            color = color,
            radius = outerRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        // Inner fill
        drawCircle(
            color = color,
            radius = innerRadius,
            center = center
        )
        // Center white core
        drawCircle(
            color = Color.White,
            radius = 2.dp.toPx(),
            center = center
        )
    } else {
        // Ambient high-contrast marker
        drawCircle(
            color = Color.White,
            radius = outerRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = innerRadius,
            center = center
        )
    }
}

private fun DrawScope.drawEndMarker(center: Offset, color: Color, isAmbient: Boolean) {
    val outerRadius = 7.dp.toPx()
    val innerRadius = 4.dp.toPx()

    if (!isAmbient) {
        // Outer glow aura
        drawCircle(
            color = color.copy(alpha = 0.35f),
            radius = outerRadius * 1.5f,
            center = center
        )
        // Dark backing
        drawCircle(
            color = Color.Black,
            radius = outerRadius,
            center = center
        )
        drawCircle(
            color = color,
            radius = outerRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        // Inner fill
        drawCircle(
            color = color,
            radius = innerRadius,
            center = center
        )
        // Center white dot
        drawCircle(
            color = Color.White,
            radius = 2.dp.toPx(),
            center = center
        )
    } else {
        // Ambient outline + cross/dot
        drawCircle(
            color = Color.White,
            radius = outerRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = 2.dp.toPx(),
            center = center
        )
    }
}

private fun DrawScope.drawIntermediateWaypoint(center: Offset, color: Color, isAmbient: Boolean) {
    val radius = 4.5.dp.toPx()

    if (!isAmbient) {
        drawCircle(
            color = Color.Black,
            radius = radius + 1.5.dp.toPx(),
            center = center
        )
        drawCircle(
            color = color,
            radius = radius,
            center = center
        )
        drawCircle(
            color = Color.White,
            radius = radius,
            center = center,
            style = Stroke(width = 1.2.dp.toPx())
        )
    } else {
        drawCircle(
            color = Color.White,
            radius = radius,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

private fun DrawScope.drawOverviewDirectionArrows(
    screenPoints: List<Offset>,
    isAmbient: Boolean
) {
    if (screenPoints.size < 4) return

    val arrowIntervalPx = 90.dp.toPx()
    val arrowSize = 5.dp.toPx()
    var accumulatedPx = 0f
    var nextArrowDistPx = arrowIntervalPx

    for (i in 0 until screenPoints.size - 1) {
        val p1 = screenPoints[i]
        val p2 = screenPoints[i + 1]
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val segLen = hypot(dx, dy)

        if (segLen <= 0.001f) continue

        while (accumulatedPx + segLen >= nextArrowDistPx) {
            val t = (nextArrowDistPx - accumulatedPx) / segLen
            val ax = p1.x + t * dx
            val ay = p1.y + t * dy

            val ux = dx / segLen
            val uy = dy / segLen
            val nx = -uy
            val ny = ux

            val path = Path().apply {
                moveTo(ax + ux * arrowSize, ay + uy * arrowSize)
                lineTo(ax - ux * 0.5f * arrowSize + nx * 0.7f * arrowSize, ay - uy * 0.5f * arrowSize + ny * 0.7f * arrowSize)
                lineTo(ax, ay)
                lineTo(ax - ux * 0.5f * arrowSize - nx * 0.7f * arrowSize, ay - uy * 0.5f * arrowSize - ny * 0.7f * arrowSize)
                close()
            }

            drawPath(
                path = path,
                color = if (isAmbient) Color.White else Color.White
            )

            nextArrowDistPx += arrowIntervalPx
        }

        accumulatedPx += segLen
    }
}
