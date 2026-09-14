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

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                0.0f to Color.Transparent,
                0.25f to Color.Black.copy(alpha = 0.85f),
                0.40f to Color.Black,
                1.0f to Color.Black
            )
        )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (relevantPoints.size < 2) return@Canvas

            val minEle = relevantPoints.minOf { it.ele }
            val maxEle = relevantPoints.maxOf { it.ele }

            // Ensure a minimum elevation range of 20 meters so flat segments don't look like jagged peaks
            val eleRange = max(20.0, maxEle - minEle)
            val paddingTop = 16.dp.toPx()
            val paddingBottom = 30.dp.toPx()
            val availableHeight = max(1f, size.height - paddingTop - paddingBottom)

            fun distanceToX(dist: Double): Float {
                val fraction = ((dist - (currentDistanceMeters - windowBehind)) / totalWindow).toFloat()
                return (fraction * size.width).coerceIn(0f, size.width)
            }

            fun eleToY(ele: Double): Float {
                val normalized = ((ele - minEle) / eleRange).toFloat()
                return (size.height - paddingBottom - (normalized * availableHeight)).coerceIn(
                    paddingTop,
                    size.height - paddingBottom
                )
            }

            val strokePath = Path()
            val fillPath = Path()

            val firstPt = relevantPoints.first()
            val firstX = distanceToX(firstPt.distanceMeters)
            val firstY = eleToY(firstPt.ele)

            // Ground horizontally to left edge when starting route (firstX > 0)
            if (firstX > 0f) {
                strokePath.moveTo(0f, firstY)
                strokePath.lineTo(firstX, firstY)
                fillPath.moveTo(0f, size.height)
                fillPath.lineTo(0f, firstY)
                fillPath.lineTo(firstX, firstY)
            } else {
                strokePath.moveTo(firstX, firstY)
                fillPath.moveTo(firstX, size.height)
                fillPath.lineTo(firstX, firstY)
            }

            for (i in 1 until relevantPoints.size) {
                val pt = relevantPoints[i]
                val x = distanceToX(pt.distanceMeters)
                val y = eleToY(pt.ele)
                strokePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            val lastPt = relevantPoints.last()
            val lastX = distanceToX(lastPt.distanceMeters)
            val lastY = eleToY(lastPt.ele)

            // Ground horizontally to right edge when near end of route (lastX < size.width)
            if (lastX < size.width) {
                strokePath.lineTo(size.width, lastY)
                fillPath.lineTo(size.width, lastY)
                fillPath.lineTo(size.width, size.height)
            } else {
                fillPath.lineTo(lastX, size.height)
            }
            fillPath.close()

            // 1. Draw gradient area fill (disabled in Ambient Mode for OLED power saving)
            if (!isAmbient) {
                val fillBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.35f),
                        Color(0xFF00E5FF).copy(alpha = 0.03f)
                    ),
                    startY = paddingTop,
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
            val riderEle = if (currentElevation != 0.0) currentElevation else firstPt.ele
            val riderY = eleToY(riderEle)

            if (!isAmbient) {
                // Outer glow
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = 0.35f),
                    radius = 7.dp.toPx(),
                    center = Offset(riderX, riderY)
                )
                // Inner solid pin dot
                drawCircle(
                    color = Color.White,
                    radius = 3.5.dp.toPx(),
                    center = Offset(riderX, riderY)
                )
                drawCircle(
                    color = Color(0xFF00E5FF),
                    radius = 2.dp.toPx(),
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

        // Elevation readout: centered at bottom where round display has maximum vertical depth
        val displayEle = if (currentElevation != 0.0) currentElevation.toInt() else relevantPoints.firstOrNull()?.ele?.toInt() ?: 0
        Text(
            text = "$displayEle m",
            color = if (isAmbient) Color.White else Color(0xFF80DEEA),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
        )
    }
}
