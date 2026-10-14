package dk.jehaj.simpleroute.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dk.jehaj.simpleroute.data.model.TrackPoint
import java.util.Locale
import kotlin.math.max

@Composable
fun FullElevationProfileView(
    trackPoints: List<TrackPoint>,
    totalDistanceMeters: Double,
    minElevation: Double,
    maxElevation: Double,
    modifier: Modifier = Modifier,
    isAmbient: Boolean = false,
    strokeColor: Color = Color(0xFF00E5FF),
    textColor: Color = Color(0xFF80DEEA)
) {
    if (trackPoints.size < 2 || totalDistanceMeters <= 0.0) {
        Box(modifier = modifier.background(Color.Black))
        return
    }

    // Downsample trackpoints for rendering performance if route has thousands of points
    val sampledPoints = remember(trackPoints) {
        if (trackPoints.size <= 250) trackPoints
        else {
            val step = max(1, trackPoints.size / 250)
            val list = mutableListOf<TrackPoint>()
            for (i in trackPoints.indices step step) {
                list.add(trackPoints[i])
            }
            if (list.last() !== trackPoints.last()) {
                list.add(trackPoints.last())
            }
            list
        }
    }

    val computedMinEle = remember(minElevation, sampledPoints) {
        if (minElevation != 0.0) minElevation else sampledPoints.minOf { it.ele }
    }
    val computedMaxEle = remember(maxElevation, sampledPoints) {
        if (maxElevation != 0.0) maxElevation else sampledPoints.maxOf { it.ele }
    }

    val eleRange = max(20.0, computedMaxEle - computedMinEle)

    Box(
        modifier = modifier
            .background(Color.Black)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val paddingTop = 12.dp.toPx()
            val paddingBottom = 18.dp.toPx()
            val availableHeight = max(1f, size.height - paddingTop - paddingBottom)

            fun distanceToX(dist: Double): Float {
                val fraction = (dist / totalDistanceMeters).toFloat()
                return (fraction * size.width).coerceIn(0f, size.width)
            }

            fun eleToY(ele: Double): Float {
                val normalized = ((ele - computedMinEle) / eleRange).toFloat()
                return (size.height - paddingBottom - (normalized * availableHeight)).coerceIn(
                    paddingTop,
                    size.height - paddingBottom
                )
            }

            val strokePath = Path()
            val fillPath = Path()

            val firstPt = sampledPoints.first()
            val firstX = distanceToX(firstPt.distanceMeters)
            val firstY = eleToY(firstPt.ele)

            strokePath.moveTo(firstX, firstY)
            fillPath.moveTo(firstX, size.height)
            fillPath.lineTo(firstX, firstY)

            for (i in 1 until sampledPoints.size) {
                val pt = sampledPoints[i]
                val x = distanceToX(pt.distanceMeters)
                val y = eleToY(pt.ele)
                strokePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            val lastPt = sampledPoints.last()
            val lastX = distanceToX(lastPt.distanceMeters)
            fillPath.lineTo(lastX, size.height)
            fillPath.close()

            // 1. Fill gradient
            if (!isAmbient) {
                val fillBrush = Brush.verticalGradient(
                    colors = listOf(
                        strokeColor.copy(alpha = 0.35f),
                        strokeColor.copy(alpha = 0.03f)
                    ),
                    startY = paddingTop,
                    endY = size.height - paddingBottom
                )
                drawPath(path = fillPath, brush = fillBrush)
            }

            // 2. Stroke
            drawPath(
                path = strokePath,
                color = if (isAmbient) Color.White else strokeColor,
                style = Stroke(
                    width = if (isAmbient) 1.5.dp.toPx() else 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // Bottom labels: Min / Max elevation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp)
        ) {
            androidx.compose.foundation.text.BasicText(
                text = "${computedMinEle.toInt()}m",
                style = androidx.compose.ui.text.TextStyle(
                    color = if (isAmbient) Color.White else textColor.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.weight(1f)
            )
            androidx.compose.foundation.text.BasicText(
                text = String.format(Locale.US, "%.1f km", totalDistanceMeters / 1000.0),
                style = androidx.compose.ui.text.TextStyle(
                    color = if (isAmbient) Color.White else Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
            androidx.compose.foundation.text.BasicText(
                text = "${computedMaxEle.toInt()}m",
                style = androidx.compose.ui.text.TextStyle(
                    color = if (isAmbient) Color.White else textColor.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
