package dk.jehaj.simpleroute.webserver

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import dk.jehaj.simpleroute.data.repository.RouteRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

class GpxWebServer(
    private val context: Context,
    private val port: Int = 8080
) {
    private val repository = RouteRepository.getInstance(context)
    private var engine: ApplicationEngine? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    fun getLocalIpAddress(): String? {
        try {
            // First attempt: check WifiManager ipAddress
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                val ipString = String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    (ipInt shr 8) and 0xff,
                    (ipInt shr 16) and 0xff,
                    (ipInt shr 24) and 0xff
                )
                if (ipString != "0.0.0.0") return ipString
            }

            // Fallback: enumerate network interfaces
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining local IP", e)
        }
        return null
    }

    fun start() {
        if (_isRunning.value) return

        try {
            val ip = getLocalIpAddress() ?: "127.0.0.1"
            val url = "http://$ip:$port"
            _serverUrl.value = url

            engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                routing {
                    get("/") {
                        val routes = repository.getRouteFiles()
                        val html = buildIndexHtml(routes.map { it.name }, url)
                        call.respondText(html, ContentType.Text.Html)
                    }

                    post("/upload") {
                        try {
                            val multipart = call.receiveMultipart()
                            var uploadedFileName = ""
                            var uploadedBytes: ByteArray? = null

                            multipart.forEachPart { part ->
                                if (part is PartData.FileItem) {
                                    val originalName = part.originalFileName ?: "route.gpx"
                                    uploadedFileName = originalName
                                    uploadedBytes = part.provider().readBytes()
                                }
                                part.dispose()
                            }

                            if (uploadedBytes != null && uploadedFileName.isNotEmpty()) {
                                repository.saveRoute(uploadedFileName, uploadedBytes!!)
                                val successHtml = buildSuccessHtml(uploadedFileName)
                                call.respondText(successHtml, ContentType.Text.Html)
                            } else {
                                call.respondText("No GPX file found in request", status = HttpStatusCode.BadRequest)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling upload", e)
                            call.respondText("Upload failed: ${e.message}", status = HttpStatusCode.InternalServerError)
                        }
                    }
                }
            }.start(wait = false)

            _isRunning.value = true
            Log.i(TAG, "GPX Web Server started on $url")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start GPX Web Server", e)
            _isRunning.value = false
            _serverUrl.value = null
        }
    }

    fun stop() {
        try {
            engine?.stop(1000, 2000)
            engine = null
            _isRunning.value = false
            _serverUrl.value = null
            Log.i(TAG, "GPX Web Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping GPX Web Server", e)
        }
    }

    private fun buildIndexHtml(currentRoutes: List<String>, serverUrl: String): String {
        val routeListItems = if (currentRoutes.isEmpty()) {
            "<li><em>No routes uploaded yet</em></li>"
        } else {
            currentRoutes.joinToString("\n") { "<li>$it</li>" }
        }

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>SimpleRoute Watch GPX Upload</title>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        background: #121212;
                        color: #E0E0E0;
                        margin: 0;
                        padding: 24px;
                        display: flex;
                        justify-content: center;
                    }
                    .container {
                        max-width: 480px;
                        width: 100%;
                        background: #1E1E1E;
                        border-radius: 16px;
                        padding: 24px;
                        box-shadow: 0 4px 20px rgba(0,0,0,0.5);
                    }
                    h1 { color: #80CBC4; font-size: 22px; margin-top: 0; }
                    p { font-size: 14px; color: #AAA; line-height: 1.5; }
                    .upload-box {
                        border: 2px dashed #444;
                        border-radius: 12px;
                        padding: 24px;
                        text-align: center;
                        margin: 20px 0;
                        background: #282828;
                    }
                    input[type=file] {
                        margin: 12px 0;
                        color: #E0E0E0;
                    }
                    button {
                        background: #00897B;
                        color: white;
                        border: none;
                        padding: 12px 24px;
                        font-size: 16px;
                        font-weight: bold;
                        border-radius: 8px;
                        cursor: pointer;
                        width: 100%;
                    }
                    button:hover { background: #00796B; }
                    ul { list-style: square; padding-left: 20px; font-size: 14px; }
                    li { margin-bottom: 6px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>SimpleRoute GPX Upload</h1>
                    <p>Upload a BRouter-generated <code>.gpx</code> file directly to your watch over Wi-Fi.</p>
                    <form action="/upload" method="post" enctype="multipart/form-data">
                        <div class="upload-box">
                            <input type="file" name="file" accept=".gpx" required />
                        </div>
                        <button type="submit">Send to Watch</button>
                    </form>
                    <hr style="border: 0; border-top: 1px solid #333; margin: 24px 0;" />
                    <h3>Routes currently on Watch:</h3>
                    <ul>
                        $routeListItems
                    </ul>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildSuccessHtml(fileName: String): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Upload Successful</title>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        background: #121212;
                        color: #E0E0E0;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        height: 100vh;
                        margin: 0;
                    }
                    .card {
                        background: #1E1E1E;
                        border-radius: 16px;
                        padding: 32px;
                        text-align: center;
                        max-width: 400px;
                    }
                    h2 { color: #81C784; }
                    a { color: #80CBC4; text-decoration: none; font-weight: bold; display: inline-block; margin-top: 16px; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>✓ Upload Successful!</h2>
                    <p><strong>$fileName</strong> has been saved to your watch.</p>
                    <p>You can now open it from the SimpleRoute watch app.</p>
                    <a href="/">← Upload another file</a>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    companion object {
        private const val TAG = "GpxWebServer"
    }
}
