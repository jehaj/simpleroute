package dk.jehaj.simpleroute.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material3.AppScaffold
import dk.jehaj.simpleroute.data.repository.RouteRepository
import dk.jehaj.simpleroute.navigation.NavigationStateHolder
import dk.jehaj.simpleroute.presentation.theme.SimpleRouteTheme
import dk.jehaj.simpleroute.webserver.GpxWebServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class Screen {
    ROUTE_LIST,
    NAVIGATION
}

class MainActivity : ComponentActivity() {

    private val isAmbientState = MutableStateFlow(false)
    private val ambientUpdateTick = MutableStateFlow(0L)

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            super.onEnterAmbient(ambientDetails)
            isAmbientState.value = true
        }

        override fun onExitAmbient() {
            super.onExitAmbient()
            isAmbientState.value = false
        }

        override fun onUpdateAmbient() {
            super.onUpdateAmbient()
            // Ambient update tick at 0.1 Hz (every 10s or 60s)
            ambientUpdateTick.value = System.currentTimeMillis()
        }
    }

    private val ambientObserver = AmbientLifecycleObserver(this, ambientCallback)
    private lateinit var webServer: GpxWebServer
    private lateinit var repository: RouteRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        repository = RouteRepository.getInstance(applicationContext)
        webServer = GpxWebServer(applicationContext)

        lifecycle.addObserver(ambientObserver)

        // Listen for Wake-on-Alert events from NavigationEngine
        lifecycleScope.launch {
            NavigationStateHolder.alertWakeEvents.collect { alert ->
                Log.i(TAG, "Wake-on-Alert triggered for cue ${alert.cue.turn}")
                wakeScreenFromAmbient()
            }
        }

        setContent {
            SimpleRouteTheme {
                AppScaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    MainAppContent(
                        repository = repository,
                        webServer = webServer,
                        isAmbientFlow = isAmbientState,
                        onWakeScreen = { wakeScreenFromAmbient() }
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreenFromAmbient() {
        runOnUiThread {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    setShowWhenLocked(true)
                    setTurnScreenOn(true)
                } else {
                    window.addFlags(
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    )
                }

                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wakeLock = powerManager?.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "SimpleRoute:ScreenAlertWakeLock"
                )
                wakeLock?.acquire(4000L)
            } catch (e: Exception) {
                Log.e(TAG, "Error waking screen", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webServer.stop()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

@Composable
fun MainAppContent(
    repository: RouteRepository,
    webServer: GpxWebServer,
    isAmbientFlow: kotlinx.coroutines.flow.StateFlow<Boolean>,
    onWakeScreen: () -> Unit
) {
    val isAmbient by isAmbientFlow.collectAsState()
    val navState by NavigationStateHolder.navigationState.collectAsState()
    var currentScreen by remember { mutableStateOf(Screen.ROUTE_LIST) }
    val coroutineScope = rememberCoroutineScope()

    // Permissions check
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    // Auto-navigate to navigation screen if navigation is active
    LaunchedEffect(navState.isNavigating) {
        if (navState.isNavigating && currentScreen == Screen.ROUTE_LIST) {
            currentScreen = Screen.NAVIGATION
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    when (currentScreen) {
        Screen.ROUTE_LIST -> {
            RouteListScreen(
                repository = repository,
                webServer = webServer,
                isNavigating = navState.isNavigating,
                onResumeNavigation = {
                    currentScreen = Screen.NAVIGATION
                },
                onStopNavigation = {
                    NavigationStateHolder.stopNavigation(context)
                },
                onSelectRoute = { file ->
                    coroutineScope.launch {
                        val route = repository.loadRoute(file)
                        NavigationStateHolder.startNavigation(context, route)
                        currentScreen = Screen.NAVIGATION
                    }
                }
            )
        }
        Screen.NAVIGATION -> {
            NavigationScreen(
                state = navState,
                isAmbient = isAmbient,
                onStopNavigation = {
                    NavigationStateHolder.stopNavigation(context)
                    currentScreen = Screen.ROUTE_LIST
                }
            )
        }
    }
}