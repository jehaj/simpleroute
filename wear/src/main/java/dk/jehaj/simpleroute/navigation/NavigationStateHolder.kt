package dk.jehaj.simpleroute.navigation

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dk.jehaj.simpleroute.data.model.Route
import dk.jehaj.simpleroute.service.NavigationService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object NavigationStateHolder {

    private val _navigationState = MutableStateFlow(NavigationState())
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    private val _alertWakeEvents = MutableSharedFlow<AlertEvent>(extraBufferCapacity = 1)
    val alertWakeEvents: SharedFlow<AlertEvent> = _alertWakeEvents.asSharedFlow()

    fun updateState(newState: NavigationState) {
        _navigationState.value = newState
    }

    fun emitAlertWake(event: AlertEvent) {
        _alertWakeEvents.tryEmit(event)
    }

    fun startNavigation(context: Context, route: Route) {
        val intent = Intent(context, NavigationService::class.java).apply {
            action = NavigationService.ACTION_START_NAVIGATION
            putExtra(NavigationService.EXTRA_ROUTE_FILE, route.fileName)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopNavigation(context: Context) {
        val intent = Intent(context, NavigationService::class.java).apply {
            action = NavigationService.ACTION_STOP_NAVIGATION
        }
        context.startService(intent)
    }
}
