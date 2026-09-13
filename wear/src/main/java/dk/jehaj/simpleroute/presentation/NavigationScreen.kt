package dk.jehaj.simpleroute.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.Text
import dk.jehaj.simpleroute.R
import dk.jehaj.simpleroute.navigation.NavigationState
import dk.jehaj.simpleroute.presentation.components.BreadcrumbMapView
import dk.jehaj.simpleroute.presentation.components.ElevationProfileView
import dk.jehaj.simpleroute.presentation.components.TopStatusBar
import dk.jehaj.simpleroute.presentation.components.TurnArrowView

enum class DisplayMode {
    ARROW,
    MAP
}

@Composable
fun NavigationScreen(
    state: NavigationState,
    isAmbient: Boolean,
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    var displayMode by remember { mutableStateOf(DisplayMode.ARROW) }
    var showStopDialog by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val route = state.route

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(showStopDialog) {
                if (!showStopDialog) {
                    detectTapGestures(
                        onTap = {
                            displayMode = if (displayMode == DisplayMode.ARROW) DisplayMode.MAP else DisplayMode.ARROW
                        },
                        onLongPress = {
                            if (!isAmbient) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                showStopDialog = true
                            }
                        }
                    )
                }
            }
            .pointerInput(showStopDialog) {
                if (!showStopDialog) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        if (dragAmount > 20) {
                            displayMode = DisplayMode.MAP
                        } else if (dragAmount < -20) {
                            displayMode = DisplayMode.ARROW
                        }
                    }
                }
            }
    ) {
        // Main Display Area: Mode A (Arrow) or Mode B (Map)
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            when (displayMode) {
                DisplayMode.ARROW -> {
                    TurnArrowView(
                        cue = state.nextCue,
                        isAmbient = isAmbient,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                DisplayMode.MAP -> {
                    if (route != null) {
                        BreadcrumbMapView(
                            trackPoints = route.trackPoints,
                            currentTrackIndex = state.currentTrackIndex,
                            currentLat = state.currentLatitude,
                            currentLon = state.currentLongitude,
                            currentBearingDeg = state.currentBearingDeg,
                            turnCues = route.turnCues,
                            isAmbient = isAmbient,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Top Status Bar
        TopStatusBar(
            distanceToTurnMeters = state.distanceToNextCueMeters,
            nextCue = state.nextCue,
            isAmbient = isAmbient,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Bottom Elevation Profile (occupies lower 28% to 32% of display)
        if (route != null && route.trackPoints.isNotEmpty()) {
            ElevationProfileView(
                trackPoints = route.trackPoints,
                currentDistanceMeters = state.distanceTraveledMeters,
                currentElevation = state.currentElevation,
                isAmbient = isAmbient,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.30f)
                    .align(Alignment.BottomCenter)
            )
        }

        // Stop Ride Confirmation Overlay
        if (showStopDialog && !isAmbient) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .clickable { showStopDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Stop Navigation?",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cancel / Go back ('X')
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FilledIconButton(
                                onClick = { showStopDialog = false },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color(0xFF37474F),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = "Cancel",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Cancel",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }

                        // Stop ride ('■')
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FilledIconButton(
                                onClick = {
                                    showStopDialog = false
                                    onStopNavigation()
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color(0xFFB71C1C),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_stop),
                                    contentDescription = "Stop",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Stop",
                                color = Color(0xFFFF8A80),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
