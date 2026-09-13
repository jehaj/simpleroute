package dk.jehaj.simpleroute.datalayer

import android.util.Log
import android.widget.Toast
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dk.jehaj.simpleroute.data.repository.RouteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.InputStream
import java.net.URLDecoder

class WatchDataLayerListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: RouteRepository

    override fun onCreate() {
        super.onCreate()
        repository = RouteRepository.getInstance(applicationContext)
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        val path = channel.path
        Log.i(TAG, "Channel opened: $path")

        if (path.startsWith(CHANNEL_PATH_PREFIX)) {
            val fileNameRaw = path.removePrefix(CHANNEL_PATH_PREFIX).trimStart('/')
            val fileName = if (fileNameRaw.isNotEmpty()) {
                URLDecoder.decode(fileNameRaw, "UTF-8")
            } else {
                "shared_route.gpx"
            }

            serviceScope.launch {
                try {
                    val channelClient = Wearable.getChannelClient(this@WatchDataLayerListenerService)
                    val inputStream: InputStream = Tasks.await(channelClient.getInputStream(channel))

                    val bytes = inputStream.use { it.readBytes() }
                    if (bytes.isNotEmpty()) {
                        repository.saveRoute(fileName, bytes)
                        Log.i(TAG, "Saved GPX route: $fileName (${bytes.size} bytes)")

                        launch(Dispatchers.Main) {
                            Toast.makeText(
                                applicationContext,
                                "Route received: $fileName",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    Tasks.await(channelClient.close(channel))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed reading GPX from channel", e)
                }
            }
        } else {
            super.onChannelOpened(channel)
        }
    }

    companion object {
        private const val TAG = "WatchDataLayerService"
        const val CHANNEL_PATH_PREFIX = "/gpx-transfer"
    }
}
