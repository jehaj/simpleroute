package dk.jehaj.simpleroute.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dk.jehaj.simpleroute.R
import dk.jehaj.simpleroute.data.model.Route
import dk.jehaj.simpleroute.data.repository.RouteRepository
import dk.jehaj.simpleroute.haptics.HapticManager
import dk.jehaj.simpleroute.navigation.NavigationEngine
import dk.jehaj.simpleroute.navigation.NavigationState
import dk.jehaj.simpleroute.navigation.NavigationStateHolder
import dk.jehaj.simpleroute.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class NavigationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var hapticManager: HapticManager
    private lateinit var powerManager: PowerManager
    private var partialWakeLock: PowerManager.WakeLock? = null

    private val navigationEngine = NavigationEngine()
    private var activeRoute: Route? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            handleLocationUpdate(location)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        hapticManager = HapticManager(this)
        powerManager = getSystemService(PowerManager::class.java)

        // Acquire partial wake-lock for background tracking pipeline (capped to max 6 hours for safety)
        partialWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SimpleRoute:NavWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L) // 6-hour max timeout
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_NAVIGATION -> {
                val routeFileName = intent.getStringExtra(EXTRA_ROUTE_FILE)
                startForegroundWithNotification("Starting navigation...")
                loadRouteAndStart(routeFileName)
            }
            ACTION_STOP_NAVIGATION -> {
                stopNavigation()
            }
        }
        return START_STICKY
    }

    private fun loadRouteAndStart(fileName: String?) {
        serviceScope.launch {
            try {
                val repository = RouteRepository.getInstance(applicationContext)
                val file = if (fileName != null) {
                    File(repository.routesDir, fileName)
                } else {
                    repository.getRouteFiles().firstOrNull()
                }

                if (file != null && file.exists()) {
                    val route = repository.loadRoute(file)
                    activeRoute = route
                    navigationEngine.loadRoute(route)
                    startLocationTracking()
                    updateNotification("Navigating: ${route.name}")
                } else {
                    Log.e(TAG, "Route file not found: $fileName")
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed loading route in NavigationService", e)
                stopSelf()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        try {
            // High-accuracy updates at 1 Hz via FusedLocationProviderClient
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(1000L)
                .setMinUpdateDistanceMeters(0f)
                .build()

            locationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.i(TAG, "Started 1 Hz high-accuracy location updates")
        } catch (e: Exception) {
            Log.e(TAG, "Failed requesting location updates", e)
        }
    }

    private fun handleLocationUpdate(location: Location) {
        val (state, alertEvent) = navigationEngine.processLocation(
            lat = location.latitude,
            lon = location.longitude,
            speedMps = location.speed,
            bearingDeg = location.bearing,
            elevation = location.altitude
        )

        NavigationStateHolder.updateState(state)

        if (alertEvent != null) {
            Log.i(TAG, "Turn Alert triggered: ${alertEvent.cue.turn} in ${alertEvent.cue.distanceFromStartMeters}m")
            // 1. Haptic alert
            hapticManager.vibrateForTurn(alertEvent.cue.turn)

            // 2. Wake screen from ambient (Wake-on-Alert)
            wakeScreenOnAlert()

            // 3. Notify state holder
            NavigationStateHolder.emitAlertWake(alertEvent)
        }

        // Update notification
        val cueText = state.nextCue?.let { cue ->
            "In ${state.distanceToNextCueMeters.toInt()}m: ${cue.description.ifEmpty { cue.turn.name }}"
        } ?: "Remaining: ${(state.distanceRemainingMeters / 1000).format(1)} km"
        updateNotification(cueText)
    }

    @Suppress("DEPRECATION")
    private fun wakeScreenOnAlert() {
        try {
            val screenWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "SimpleRoute:ScreenAlertWakeLock"
            )
            screenWakeLock.acquire(4000L)
        } catch (e: Exception) {
            Log.e(TAG, "Error waking screen on alert", e)
        }
    }

    private fun stopNavigation() {
        try {
            locationClient.removeLocationUpdates(locationCallback)
            hapticManager.cancel()
            partialWakeLock?.let {
                if (it.isHeld) it.release()
            }
            NavigationStateHolder.updateState(NavigationState(isNavigating = false))
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping navigation", e)
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SimpleRoute Navigation",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active turn-by-turn navigation guidance"
            enableVibration(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, NavigationService::class.java).apply {
            action = ACTION_STOP_NAVIGATION
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(activeRoute?.name ?: "SimpleRoute")
            .setContentText(text)
            .setSmallIcon(R.drawable.splash_icon)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_stop,
                "Stop Ride",
                stopPendingIntent
            )
            .build()
    }

    private fun startForegroundWithNotification(initialText: String) {
        val notification = buildNotification(initialText)
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        locationClient.removeLocationUpdates(locationCallback)
        partialWakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "NavigationService"
        const val CHANNEL_ID = "navigation_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_NAVIGATION = "dk.jehaj.simpleroute.action.START_NAVIGATION"
        const val ACTION_STOP_NAVIGATION = "dk.jehaj.simpleroute.action.STOP_NAVIGATION"
        const val EXTRA_ROUTE_FILE = "extra_route_file"

        private fun Double.format(digits: Int) = "%.${digits}f".format(this)
    }
}
