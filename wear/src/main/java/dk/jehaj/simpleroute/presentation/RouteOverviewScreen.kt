package dk.jehaj.simpleroute.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import dk.jehaj.simpleroute.data.model.Route
import dk.jehaj.simpleroute.presentation.components.FullElevationProfileView
import dk.jehaj.simpleroute.presentation.components.RouteOverviewMap
import java.util.Locale

@Composable
fun RouteOverviewScreen(
    route: Route,
    isAmbient: Boolean,
    onStartNavigation: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBack()
    }

    val listState = rememberScalingLazyListState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            // Header: Route title
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = route.name,
                        color = Color(0xFF80CBC4),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            // Key Route Stats (Distance, Ascent, Descent)
            item {
                Card(
                    onClick = { },
                    enabled = false,
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f km", route.totalDistanceMeters / 1000.0),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "↗ +${route.totalAscentMeters.toInt()}m",
                                color = Color(0xFF81C784),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "↘ -${route.totalDescentMeters.toInt()}m",
                                color = Color(0xFFE57373),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (route.waypoints.isNotEmpty()) {
                                Text(
                                    text = "★ ${route.waypoints.size}",
                                    color = Color(0xFFFFD54F),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Route Map Preview Canvas (Fitted to circular viewport)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0A0A0A))
                            .border(1.dp, Color(0xFF263238), CircleShape)
                    ) {
                        RouteOverviewMap(
                            trackPoints = route.trackPoints,
                            waypoints = route.waypoints,
                            isAmbient = isAmbient,
                            padding = 18.dp
                        )
                    }
                }
            }

            // Elevation Horizon
            if (route.trackPoints.size >= 2) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp)
                    ) {
                        Text(
                            text = "ELEVATION PROFILE",
                            color = Color(0xFF78909C),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF141414))
                                .border(1.dp, Color(0xFF263238), RoundedCornerShape(8.dp))
                        ) {
                            FullElevationProfileView(
                                trackPoints = route.trackPoints,
                                totalDistanceMeters = route.totalDistanceMeters,
                                minElevation = route.minElevation,
                                maxElevation = route.maxElevation,
                                isAmbient = isAmbient
                            )
                        }
                    }
                }
            }

            // Start Navigation Primary Action Button
            item {
                Button(
                    onClick = onStartNavigation,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "▶ Start Route",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Back / Cancel Button
            item {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263238)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "Back to Routes",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
