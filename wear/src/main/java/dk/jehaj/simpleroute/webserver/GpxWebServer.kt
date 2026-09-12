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
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Locale

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

    private val _currentPin = MutableStateFlow<String?>(null)
    val currentPin: StateFlow<String?> = _currentPin.asStateFlow()

    private var failedAttempts = 0
    private var lockoutUntil = 0L

    fun getLocalIpAddress(): String? {
        try {
            // First attempt: check WifiManager ipAddress
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                val ipString = String.format(
                    Locale.US,
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

            // Generate cryptographically secure 4-digit PIN
            val pin = String.format(Locale.US, "%04d", SecureRandom().nextInt(10000))
            _currentPin.value = pin
            failedAttempts = 0
            lockoutUntil = 0L

            engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                routing {
                    get("/") {
                        val routes = repository.getRouteFiles()
                        val html = buildIndexHtml(routes.map { it.name }, url)
                        call.respondText(html, ContentType.Text.Html)
                    }

                    post("/upload") {
                        if (System.currentTimeMillis() < lockoutUntil) {
                            call.respondText(
                                buildErrorHtml("Too many invalid attempts. Temporary lockout in effect. Try again later."),
                                ContentType.Text.Html,
                                status = HttpStatusCode.TooManyRequests
                            )
                            return@post
                        }

                        try {
                            val multipart = call.receiveMultipart()
                            var submittedPin = ""
                            var uploadedFileName = ""
                            var uploadedBytes: ByteArray? = null
                            var payloadTooLarge = false

                            multipart.forEachPart { part ->
                                when (part) {
                                    is PartData.FormItem -> {
                                        if (part.name == "pin") {
                                            submittedPin = part.value.trim()
                                        }
                                    }
                                    is PartData.FileItem -> {
                                        uploadedFileName = part.originalFileName ?: "route.gpx"
                                        val output = ByteArrayOutputStream()
                                        val buffer = ByteArray(8192)
                                        var totalBytes = 0L
                                        part.provider().toInputStream().use { stream ->
                                            var bytesRead: Int
                                            while (stream.read(buffer).also { bytesRead = it } != -1) {
                                                totalBytes += bytesRead
                                                if (totalBytes > MAX_UPLOAD_SIZE_BYTES) {
                                                    payloadTooLarge = true
                                                    break
                                                }
                                                output.write(buffer, 0, bytesRead)
                                            }
                                        }
                                        if (!payloadTooLarge) {
                                            uploadedBytes = output.toByteArray()
                                        }
                                    }
                                    else -> {}
                                }
                                part.dispose()
                            }

                            if (payloadTooLarge) {
                                call.respondText(
                                    buildErrorHtml("File exceeds maximum allowed size of 5 MB."),
                                    ContentType.Text.Html,
                                    status = HttpStatusCode.PayloadTooLarge
                                )
                                return@post
                            }

                            val expectedPin = _currentPin.value
                            if (expectedPin == null || submittedPin != expectedPin) {
                                failedAttempts++
                                if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                                    lockoutUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS
                                }
                                call.respondText(
                                    buildErrorHtml("Invalid 4-digit PIN. Check the PIN currently displayed on your watch screen."),
                                    ContentType.Text.Html,
                                    status = HttpStatusCode.Unauthorized
                                )
                                return@post
                            }

                            // Successful PIN validation: reset failed attempts
                            failedAttempts = 0

                            if (uploadedBytes != null && uploadedFileName.isNotEmpty()) {
                                repository.saveRoute(uploadedFileName, uploadedBytes!!)
                                val successHtml = buildSuccessHtml(uploadedFileName)
                                call.respondText(successHtml, ContentType.Text.Html)
                            } else {
                                call.respondText(
                                    buildErrorHtml("No valid GPX file was provided in the upload request."),
                                    ContentType.Text.Html,
                                    status = HttpStatusCode.BadRequest
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling upload", e)
                            call.respondText(
                                buildErrorHtml("Upload failed due to an unexpected server error: ${e.message}"),
                                ContentType.Text.Html,
                                status = HttpStatusCode.InternalServerError
                            )
                        }
                    }
                }
            }.start(wait = false)

            _isRunning.value = true
            Log.i(TAG, "GPX Web Server started on $url with PIN $pin")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start GPX Web Server", e)
            _isRunning.value = false
            _serverUrl.value = null
            _currentPin.value = null
        }
    }

    fun stop() {
        try {
            engine?.stop(1000, 2000)
            engine = null
            _isRunning.value = false
            _serverUrl.value = null
            _currentPin.value = null
            Log.i(TAG, "GPX Web Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping GPX Web Server", e)
        }
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
    }

    private fun buildIndexHtml(currentRoutes: List<String>, serverUrl: String): String {
        val routeListItems = if (currentRoutes.isEmpty()) {
            "<li><em>No routes uploaded yet</em></li>"
        } else {
            currentRoutes.joinToString("\n") { "<li>${escapeHtml(it)}</li>" }
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
                    .pin-box {
                        background: #282828;
                        border: 1px solid #444;
                        border-radius: 8px;
                        padding: 16px;
                        margin-bottom: 16px;
                    }
                    .pin-box label {
                        display: block;
                        font-size: 14px;
                        margin-bottom: 8px;
                        color: #80CBC4;
                    }
                    .pin-box input {
                        background: #121212;
                        border: 1px solid #555;
                        color: #FFF;
                        font-size: 20px;
                        font-weight: bold;
                        letter-spacing: 4px;
                        text-align: center;
                        padding: 8px;
                        width: 140px;
                        border-radius: 6px;
                    }
                    .upload-box {
                        border: 2px dashed #444;
                        border-radius: 12px;
                        padding: 24px;
                        text-align: center;
                        margin: 16px 0;
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
                        <div class="pin-box">
                            <label for="pin">Watch Security PIN:</label>
                            <input type="text" id="pin" name="pin" maxlength="4" pattern="[0-9]{4}" placeholder="0000" required autocomplete="off" />
                        </div>
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
        val safeName = escapeHtml(fileName)
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
                    <p><strong>$safeName</strong> has been saved to your watch.</p>
                    <p>You can now open it from the SimpleRoute watch app.</p>
                    <a href="/">← Upload another file</a>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildErrorHtml(errorMessage: String): String {
        val safeMessage = escapeHtml(errorMessage)
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Upload Error</title>
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
                    h2 { color: #EF5350; }
                    a { color: #80CBC4; text-decoration: none; font-weight: bold; display: inline-block; margin-top: 16px; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>✕ Upload Failed</h2>
                    <p>$safeMessage</p>
                    <a href="/">← Try again</a>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    companion object {
        private const val TAG = "GpxWebServer"
        private const val MAX_UPLOAD_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 60_000L // 1 minute
    }
}
