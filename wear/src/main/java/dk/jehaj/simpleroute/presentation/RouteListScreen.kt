package dk.jehaj.simpleroute.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import dk.jehaj.simpleroute.data.model.Route
import dk.jehaj.simpleroute.data.repository.RouteRepository
import dk.jehaj.simpleroute.webserver.GpxWebServer
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun RouteListScreen(
    repository: RouteRepository,
    webServer: GpxWebServer,
    isNavigating: Boolean,
    onResumeNavigation: () -> Unit,
    onStopNavigation: () -> Unit,
    onSelectRoute: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val routeFiles by repository.routeFilesFlow.collectAsState()
    var showWifiDialog by remember { mutableStateOf(false) }
    var routeToDelete by remember { mutableStateOf<File?>(null) }

    val isServerRunning by webServer.isRunning.collectAsState()
    val serverUrl by webServer.serverUrl.collectAsState()
    val currentPin by webServer.currentPin.collectAsState()

    fun refreshRoutes() {
        coroutineScope.launch {
            repository.refreshRoutes()
        }
    }

    LaunchedEffect(Unit) {
        refreshRoutes()
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
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "SimpleRoute",
                        color = Color(0xFF80CBC4),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // If already navigating, show Resume / Stop option
            if (isNavigating) {
                item {
                    Button(
                        onClick = onResumeNavigation,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Text("▶ Resume Navigation", fontWeight = FontWeight.Bold)
                    }
                }
                item {
                    Button(
                        onClick = onStopNavigation,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Text("■ Stop Navigation")
                    }
                }
            }

            // Wi-Fi Import Action
            item {
                Card(
                    onClick = {
                        if (!isServerRunning) {
                            webServer.start()
                        }
                        showWifiDialog = true
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isServerRunning) Color(0xFF004D40) else Color(0xFF263238)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = if (isServerRunning) "Wi-Fi Server Running" else "Import via Wi-Fi",
                            color = Color(0xFF80CBC4),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isServerRunning) (serverUrl ?: "Starting...") else "Upload .gpx from web browser",
                            color = Color(0xFFB0BEC5),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Route list section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Routes (${routeFiles.size})",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "↻ Refresh",
                        color = Color(0xFF80CBC4),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { refreshRoutes() }
                            .padding(4.dp)
                    )
                }
            }

            if (routeFiles.isNotEmpty()) {
                item {
                    Text(
                        text = "Hold route to delete",
                        color = Color(0xFF78909C),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    )
                }
            }

            if (routeFiles.isEmpty()) {
                item {
                    Text(
                        text = "No routes found.\nUse Wi-Fi or Companion App to transfer a GPX file.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            } else {
                items(routeFiles) { file ->
                    Card(
                        onClick = { onSelectRoute(file) },
                        onLongClick = { routeToDelete = file },
                        onLongClickLabel = "Delete route ${file.nameWithoutExtension}",
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = file.nameWithoutExtension,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${file.length() / 1024} KB",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        // Delete Route Confirmation Dialog Overlay
        if (routeToDelete != null) {
            val file = routeToDelete!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Delete Route?",
                        color = Color(0xFFEF5350),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = file.nameWithoutExtension,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { routeToDelete = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                val target = file
                                routeToDelete = null
                                coroutineScope.launch {
                                    if (isNavigating) {
                                        onStopNavigation()
                                    }
                                    repository.deleteRoute(target)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Wi-Fi Server Details Overlay
        if (showWifiDialog && isServerRunning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Import via Wi-Fi",
                        color = Color(0xFF80CBC4),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Open browser to:",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF004D40), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = serverUrl ?: "Connecting...",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (serverUrl != null && serverUrl!!.contains("127.0.0.1")) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Connect watch to Wi-Fi in Settings",
                            color = Color(0xFFFFB74D),
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (currentPin != null) {
                        Text(
                            text = "PIN: ${currentPin!!}",
                            color = Color(0xFFFFD54F),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row {
                        Button(
                            onClick = {
                                webServer.stop()
                                refreshRoutes()
                                showWifiDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Text("Done", fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                webServer.stop()
                                refreshRoutes()
                                showWifiDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Text("Stop", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
