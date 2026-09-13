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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import dk.jehaj.simpleroute.data.model.TrackPoint
import dk.jehaj.simpleroute.data.model.TurnCue
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

@Composable
fun BreadcrumbMapView(
    trackPoints: List<TrackPoint>,
    currentTrackIndex: Int,
    currentLat: Double,
    currentLon: Double,
    currentBearingDeg: Float,
    turnCues: List<TurnCue>,
    isAmbient: Boolean,
    modifier: Modifier = Modifier
) {
    // Window of trackpoints around current index for efficiency and clarity
    val windowStart = max(0, currentTrackIndex - 30)
    val windowEnd = min(trackPoints.lastIndex, currentTrackIndex + 120)

    val visiblePoints = remember(trackPoints, currentTrackIndex) {
        if (trackPoints.isEmpty()) emptyList()
        else trackPoints.subList(windowStart, windowEnd + 1)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f

        // 300 meters from center to edge of display
        val viewRadiusMeters = 300f
        val scale = (size.width / 2f) / viewRadiusMeters

        val cosLat = cos(Math.toRadians(currentLat))
        val metersPerDegLat = 111132.954
        val metersPerDegLon = 111132.954 * cosLat

        fun geoToScreen(lat: Double, lon: Double): Offset {
            val dx = ((lon - currentLon) * metersPerDegLon).toFloat()
            val dy = ((lat - currentLat) * metersPerDegLat).toFloat()
            // Y increases downwards on screen
            return Offset(cx + dx * scale, cy - dy * scale)
        }

        // Rotate canvas according to current GPS heading (bearing-up mode)
        rotate(degrees = -currentBearingDeg, pivot = Offset(cx, cy)) {
            if (visiblePoints.size >= 2) {
                val pastPath = Path()
                val aheadPath = Path()

                var pastStarted = false
                var aheadStarted = false

                for (i in visiblePoints.indices) {
                    val globalIdx = windowStart + i
                    val pt = visiblePoints[i]
                    val screenOffset = geoToScreen(pt.lat, pt.lon)

                    if (globalIdx <= currentTrackIndex) {
                        if (!pastStarted) {
                            pastPath.moveTo(screenOffset.x, screenOffset.y)
                            pastStarted = true
                        } else {
                            pastPath.lineTo(screenOffset.x, screenOffset.y)
                        }
                    }

                    if (globalIdx >= currentTrackIndex) {
                        if (!aheadStarted) {
                            aheadPath.moveTo(screenOffset.x, screenOffset.y)
                            aheadStarted = true
                        } else {
                            aheadPath.lineTo(screenOffset.x, screenOffset.y)
                        }
                    }
                }

                // Draw completed / past trail (dimmer)
                if (pastStarted && !isAmbient) {
                    drawPath(
                        path = pastPath,
                        color = Color(0xFF455A64),
                        style = Stroke(
                            width = 4.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // Draw upcoming trail (high contrast)
                if (aheadStarted) {
                    drawPath(
                        path = aheadPath,
                        color = if (isAmbient) Color.White else Color(0xFF00E5FF),
                        style = Stroke(
                            width = if (isAmbient) 2.5.dp.toPx() else 4.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Draw route direction arrows along upcoming trail to show travel direction
                    val arrows = calculateRouteArrows(
                        trackPoints = trackPoints,
                        currentTrackIndex = currentTrackIndex,
                        windowEnd = windowEnd,
                        cx = cx,
                        cy = cy,
                        minDistanceFromCenter = 20.dp.toPx(),
                        maxDistanceFromCenter = (size.width / 2f) * 0.98f,
                        arrowIntervalMeters = 40.0,
                        geoToScreen = ::geoToScreen
                    )
                    drawRouteArrows(arrows, isAmbient)
                }

                // Draw turn cues along the visible track
                for (cue in turnCues) {
                    if (cue.offset in windowStart..windowEnd) {
                        val pt = trackPoints.getOrNull(cue.offset)
                        if (pt != null) {
                            val cueOffset = geoToScreen(pt.lat, pt.lon)
                            drawCircle(
                                color = if (isAmbient) Color.White else Color(0xFFFFD54F),
                                radius = 4.dp.toPx(),
                                center = cueOffset
                            )
                        }
                    }
                }
            }
        }

        // Fixed Rider Indicator at center (cx, cy)
        // Points straight UP because bearing-up mode rotates world around rider
        drawRiderChevron(cx, cy, isAmbient)
    }
}

private fun DrawScope.drawRiderChevron(cx: Float, cy: Float, isAmbient: Boolean) {
    val sizePx = 14.dp.toPx()
    val chevron = Path().apply {
        moveTo(cx, cy - sizePx) // Tip pointing up
        lineTo(cx - sizePx * 0.7f, cy + sizePx * 0.6f)
        lineTo(cx, cy + sizePx * 0.2f)
        lineTo(cx + sizePx * 0.7f, cy + sizePx * 0.6f)
        close()
    }

    if (!isAmbient) {
        // Glowing aura
        drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = 0.25f),
            radius = sizePx * 1.5f,
            center = Offset(cx, cy)
        )
        // Solid chevron
        drawPath(path = chevron, color = Color.White)
        drawPath(
            path = chevron,
            color = Color(0xFF00E5FF),
            style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round)
        )
    } else {
        // Ambient high-contrast outline
        drawPath(
            path = chevron,
            color = Color.White,
            style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round)
        )
    }
}
