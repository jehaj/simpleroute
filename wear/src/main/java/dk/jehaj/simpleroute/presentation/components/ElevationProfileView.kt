package dk.jehaj.simpleroute.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import dk.jehaj.simpleroute.data.model.TrackPoint
import kotlin.math.max
import kotlin.math.min

@Composable
fun ElevationProfileView(
    trackPoints: List<TrackPoint>,
    currentDistanceMeters: Double,
    currentElevation: Double,
    isAmbient: Boolean,
    modifier: Modifier = Modifier
) {
    val windowBehind = 500.0
    val windowAhead = 1500.0
    val totalWindow = windowBehind + windowAhead
    val startDistance = max(0.0, currentDistanceMeters - windowBehind)
    val endDistance = currentDistanceMeters + windowAhead

    // Filter relevant trackpoints in window
    val relevantPoints = remember(trackPoints, currentDistanceMeters) {
        if (trackPoints.isEmpty()) emptyList()
        else {
            trackPoints.filter {
                it.distanceMeters >= startDistance - 50 && it.distanceMeters <= endDistance + 50
            }
        }
    }

    Box(modifier = modifier) {
        // Top Vignette Gradient Overlay (Black fade tapering down to isolate from top views)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.90f),
                            Color.Black.copy(alpha = 0.50f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 45f
                    )
                )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (relevantPoints.size < 2) return@Canvas

            var minEle = relevantPoints.minOf { it.ele }
            var maxEle = relevantPoints.maxOf { it.ele }

            // Ensure a minimum elevation range of 20 meters so flat segments don't look like jagged peaks
            val eleRange = max(20.0, maxEle - minEle)
            val paddingY = 8.dp.toPx()
            val availableHeight = size.height - paddingY * 2

            fun distanceToX(dist: Double): Float {
                val fraction = ((dist - (currentDistanceMeters - windowBehind)) / totalWindow).toFloat()
                return (fraction * size.width).coerceIn(0f, size.width)
            }

            fun eleToY(ele: Double): Float {
                val normalized = ((ele - minEle) / eleRange).toFloat()
                return size.height - paddingY - (normalized * availableHeight)
            }

            val strokePath = Path()
            val fillPath = Path()

            val firstX = distanceToX(relevantPoints.first().distanceMeters)
            val firstY = eleToY(relevantPoints.first().ele)

            strokePath.moveTo(firstX, firstY)
            fillPath.moveTo(firstX, size.height)
            fillPath.lineTo(firstX, firstY)

            for (i in 1 until relevantPoints.size) {
                val pt = relevantPoints[i]
                val x = distanceToX(pt.distanceMeters)
                val y = eleToY(pt.ele)
                strokePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            val lastX = distanceToX(relevantPoints.last().distanceMeters)
            fillPath.lineTo(lastX, size.height)
            fillPath.close()

            // 1. Draw gradient area fill (disabled in Ambient Mode for OLED power saving)
            if (!isAmbient) {
                val fillBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.35f),
                        Color(0xFF00E5FF).copy(alpha = 0.03f)
                    ),
                    startY = paddingY,
                    endY = size.height
                )
                drawPath(path = fillPath, brush = fillBrush)
            }

            // 2. Draw profile stroke
            val strokeColor = if (isAmbient) Color.White else Color(0xFF00E5FF)
            drawPath(
                path = strokePath,
                color = strokeColor,
                style = Stroke(
                    width = if (isAmbient) 1.5.dp.toPx() else 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // 3. Rider position indicator: pinned at fixed 25% horizontal point
            val riderX = size.width * 0.25f
            val riderY = eleToY(currentElevation)

            if (!isAmbient) {
                // Outer glow
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = 0.35f),
                    radius = 8.dp.toPx(),
                    center = Offset(riderX, riderY)
                )
                // Inner solid pin dot
                drawCircle(
                    color = Color(0xFFFFFFFF),
                    radius = 4.dp.toPx(),
                    center = Offset(riderX, riderY)
                )
                drawCircle(
                    color = Color(0xFF00E5FF),
                    radius = 2.5.dp.toPx(),
                    center = Offset(riderX, riderY)
                )
            } else {
                // High-contrast outline dot in ambient mode
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = Offset(riderX, riderY)
                )
            }
        }

        // Elevation readout
        Text(
            text = "${currentElevation.toInt()} m",
            color = if (isAmbient) Color.White else Color(0xFFB0BEC5),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 4.dp)
        )
    }
}
