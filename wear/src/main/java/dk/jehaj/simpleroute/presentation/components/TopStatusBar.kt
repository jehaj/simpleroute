package dk.jehaj.simpleroute.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import dk.jehaj.simpleroute.data.model.TurnCue
import java.util.Locale

@Composable
fun TopStatusBar(
    distanceToTurnMeters: Double,
    nextCue: TurnCue?,
    isAmbient: Boolean,
    modifier: Modifier = Modifier
) {
    val formattedDistance = formatDistance(distanceToTurnMeters)
    val turnCode = nextCue?.turn?.code?.ifEmpty { "—" } ?: "—"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 28.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Turn badge
            Box(
                modifier = Modifier
                    .background(
                        color = if (isAmbient) Color.DarkGray else Color(0xFF004D40),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = turnCode,
                    color = if (isAmbient) Color.White else Color(0xFF80CBC4),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Distance text
            Text(
                text = formattedDistance,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}

private fun formatDistance(distanceMeters: Double): String {
    return when {
        distanceMeters < 1000 -> "${distanceMeters.toInt()} m"
        else -> String.format(Locale.US, "%.1f km", distanceMeters / 1000.0)
    }
}
