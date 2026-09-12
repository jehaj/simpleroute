package dk.jehaj.simpleroute

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import dk.jehaj.simpleroute.ui.theme.SimpleRouteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder

data class SelectedGpx(
    val uri: Uri,
    val fileName: String,
    val sizeBytes: Long
)

class MainActivity : ComponentActivity() {

    private var initialGpx by mutableStateOf<SelectedGpx?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            SimpleRouteTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) { innerPadding ->
                    PhoneAppScreen(
                        initialGpx = initialGpx,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }

        if (uri != null) {
            val (name, size) = queryFileInfo(this, uri)
            initialGpx = SelectedGpx(uri, name, size)
        }
    }

    companion object {
        fun queryFileInfo(context: Context, uri: Uri): Pair<String, Long> {
            var name = "route.gpx"
            var size = 0L
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) name = cursor.getString(nameIdx) ?: "route.gpx"
                        if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                    }
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Error querying file info", e)
            }
            return name to size
        }
    }
}

@Composable
fun PhoneAppScreen(
    initialGpx: SelectedGpx?,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedFile by remember { mutableStateOf<SelectedGpx?>(initialGpx) }
    var connectedNodes by remember { mutableStateOf<List<Node>>(emptyList()) }
    var isLoadingNodes by remember { mutableStateOf(false) }
    var isTransferring by remember { mutableStateOf(false) }
    var transferStatus by remember { mutableStateOf<String?>(null) }

    // Synchronize initialGpx
    LaunchedEffect(initialGpx) {
        if (initialGpx != null) {
            selectedFile = initialGpx
        }
    }

    fun refreshConnectedWatches() {
        coroutineScope.launch {
            isLoadingNodes = true
            try {
                val nodes = withContext(Dispatchers.IO) {
                    Tasks.await(Wearable.getNodeClient(context).connectedNodes)
                }
                connectedNodes = nodes
            } catch (e: Exception) {
                Log.e("PhoneAppScreen", "Failed listing wearable nodes", e)
            } finally {
                isLoadingNodes = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshConnectedWatches()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val (name, size) = MainActivity.queryFileInfo(context, uri)
            selectedFile = SelectedGpx(uri, name, size)
            transferStatus = null
        }
    }

    fun sendGpxToWatch(file: SelectedGpx) {
        val targetNode = connectedNodes.firstOrNull()
        if (targetNode == null) {
            transferStatus = "Error: No connected watch detected."
            return
        }

        coroutineScope.launch {
            isTransferring = true
            transferStatus = "Connecting to ${targetNode.displayName}..."
            try {
                withContext(Dispatchers.IO) {
                    val maxFileBytes = 10 * 1024 * 1024L // 10 MB limit
                    val bytes = context.contentResolver.openInputStream(file.uri)?.use { stream ->
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0L
                        var read: Int
                        while (stream.read(buffer).also { read = it } != -1) {
                            total += read
                            if (total > maxFileBytes) {
                                throw IllegalStateException("File exceeds maximum allowed size (10 MB)")
                            }
                            out.write(buffer, 0, read)
                        }
                        out.toByteArray()
                    } ?: throw Exception("Cannot read file")

                    val encodedName = URLEncoder.encode(file.fileName, "UTF-8")
                    val channelPath = "/gpx-transfer/$encodedName"

                    val channelClient = Wearable.getChannelClient(context)
                    val channel = Tasks.await(channelClient.openChannel(targetNode.id, channelPath))
                    val outputStream: OutputStream = Tasks.await(channelClient.getOutputStream(channel))

                    outputStream.use { out ->
                        out.write(bytes)
                        out.flush()
                    }

                    // Allow watch time to read bytes before sender closes
                    kotlinx.coroutines.delay(1000L)
                    try {
                        Tasks.await(channelClient.close(channel))
                    } catch (e: Exception) {
                        // Channel may already be closed by watch upon receiving EOF
                    }
                }
                transferStatus = "✓ Success! Sent ${file.fileName} to ${targetNode.displayName}."
            } catch (e: Exception) {
                Log.e("PhoneAppScreen", "Error transferring GPX", e)
                transferStatus = "Transfer failed: ${e.message}"
            } finally {
                isTransferring = false
            }
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SimpleRoute Companion",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Send BRouter GPX routes directly to your watch",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Connected Watch Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connected Watch",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (connectedNodes.isNotEmpty()) {
                            Text(
                                text = connectedNodes.joinToString { it.displayName },
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Text(
                                text = if (isLoadingNodes) "Searching..." else "No watch connected",
                                color = if (isLoadingNodes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { refreshConnectedWatches() },
                        enabled = !isLoadingNodes
                    ) {
                        Text("Refresh")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // File selection card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (selectedFile != null) {
                        Text(
                            text = "Selected Route",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = selectedFile!!.fileName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                        if (selectedFile!!.sizeBytes > 0) {
                            Text(
                                text = "${selectedFile!!.sizeBytes / 1024} KB",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "No GPX file selected",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            filePickerLauncher.launch(arrayOf("*/*", "application/gpx+xml"))
                        }
                    ) {
                        Text(if (selectedFile == null) "Select GPX File" else "Change GPX File")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action button
            if (selectedFile != null) {
                Button(
                    onClick = { sendGpxToWatch(selectedFile!!) },
                    enabled = connectedNodes.isNotEmpty() && !isTransferring,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isTransferring) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Sending to Watch...")
                    } else {
                        Text(
                            text = "Send Route to Watch",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Transfer Status
            if (transferStatus != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = transferStatus!!,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = if (transferStatus!!.startsWith("✓")) {
                        Color(0xFF2E7D32)
                    } else if (transferStatus!!.startsWith("Error") || transferStatus!!.startsWith("Transfer failed")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}