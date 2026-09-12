package dk.jehaj.simpleroute.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
    val route = state.route

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 20) {
                        displayMode = DisplayMode.MAP
                    } else if (dragAmount < -20) {
                        displayMode = DisplayMode.ARROW
                    }
                }
            }
            .clickable {
                // Toggle mode on tap
                displayMode = if (displayMode == DisplayMode.ARROW) DisplayMode.MAP else DisplayMode.ARROW
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
    }
}
