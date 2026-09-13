package dk.jehaj.simpleroute.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import dk.jehaj.simpleroute.data.model.TurnCue
import dk.jehaj.simpleroute.data.model.TurnType

@Composable
fun TurnArrowView(
    cue: TurnCue?,
    isAmbient: Boolean,
    modifier: Modifier = Modifier
) {
    val angle = cue?.turnAngle ?: 0f
    val turnType = cue?.turn ?: TurnType.C
    val desc = cue?.description.orEmpty()

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Arrow vector canvas
        Canvas(
            modifier = Modifier
                .size(100.dp)
                .padding(8.dp)
        ) {
            val strokeColor = if (isAmbient) Color.White else Color(0xFF00E5FF)
            val strokeWidth = if (isAmbient) 3.5.dp.toPx() else 5.dp.toPx()

            if (turnType == TurnType.RNDB) {
                drawRoundabout(strokeColor, strokeWidth, angle, isAmbient)
            } else {
                drawTurnArrow(strokeColor, strokeWidth, angle, isAmbient)
            }
        }

        // Turn instruction text
        if (desc.isNotEmpty()) {
            Text(
                text = desc,
                color = if (isAmbient) Color.White else Color(0xFFE0E0E0),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
    }
}

private fun DrawScope.drawTurnArrow(
    color: Color,
    strokeWidth: Float,
    angle: Float,
    isAmbient: Boolean
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val arrowLength = size.height * 0.38f

    rotate(degrees = angle, pivot = Offset(cx, cy)) {
        val arrowPath = Path().apply {
            // Shaft from bottom towards center
            moveTo(cx, cy + arrowLength)
            lineTo(cx, cy - arrowLength)

            // Arrow head
            val headSize = arrowLength * 0.5f
            moveTo(cx - headSize, cy - arrowLength + headSize)
            lineTo(cx, cy - arrowLength)
            lineTo(cx + headSize, cy - arrowLength + headSize)
        }

        drawPath(
            path = arrowPath,
            color = color,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

private fun DrawScope.drawRoundabout(
    color: Color,
    strokeWidth: Float,
    angle: Float,
    isAmbient: Boolean
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = size.width * 0.28f

    // Circle
    drawCircle(
        color = color,
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = strokeWidth)
    )

    // Entry arrow from bottom
    val entryPath = Path().apply {
        moveTo(cx, cy + radius * 1.6f)
        lineTo(cx, cy + radius)
    }
    drawPath(
        path = entryPath,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    // Exit chevron rotated by deflection angle
    rotate(degrees = angle, pivot = Offset(cx, cy)) {
        val exitHead = Path().apply {
            val tipY = cy - radius * 1.5f
            moveTo(cx, cy - radius)
            lineTo(cx, tipY)
            moveTo(cx - 10f, tipY + 10f)
            lineTo(cx, tipY)
            lineTo(cx + 10f, tipY + 10f)
        }
        drawPath(
            path = exitHead,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
