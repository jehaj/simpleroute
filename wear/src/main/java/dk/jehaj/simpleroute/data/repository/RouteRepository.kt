package dk.jehaj.simpleroute.data.repository

import android.content.Context
import android.util.Log
import dk.jehaj.simpleroute.data.model.Route
import dk.jehaj.simpleroute.data.parser.GpxParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

class RouteRepository(private val context: Context) {

    private val parser = GpxParser()
    private val _routeFilesFlow = MutableStateFlow<List<File>>(emptyList())
    val routeFilesFlow: StateFlow<List<File>> = _routeFilesFlow.asStateFlow()

    val routesDir: File
        get() = File(context.filesDir, "routes").apply {
            if (!exists()) {
                mkdirs()
            }
        }

    init {
        ensureInitialRoutes()
        refreshRoutesSync()
    }

    private fun refreshRoutesSync() {
        val files = routesDir.listFiles { _, name ->
            name.endsWith(".gpx", ignoreCase = true)
        }?.toList() ?: emptyList()
        _routeFilesFlow.value = files.sortedBy { it.name }
    }

    suspend fun refreshRoutes(): List<File> = withContext(Dispatchers.IO) {
        val files = routesDir.listFiles { _, name ->
            name.endsWith(".gpx", ignoreCase = true)
        }?.toList() ?: emptyList()
        val sorted = files.sortedBy { it.name }
        _routeFilesFlow.value = sorted
        sorted
    }

    private fun ensureInitialRoutes() {
        try {
            val dir = routesDir
            val existing = dir.listFiles { _, name -> name.endsWith(".gpx", ignoreCase = true) }
            if (existing.isNullOrEmpty()) {
                // Copy bundled assets if present
                val assetRoutes = context.assets.list("routes") ?: emptyArray()
                for (assetName in assetRoutes) {
                    if (assetName.endsWith(".gpx", ignoreCase = true)) {
                        val targetFile = File(dir, assetName)
                        context.assets.open("routes/$assetName").use { input ->
                            FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring initial routes", e)
        }
    }

    suspend fun getRouteFiles(): List<File> = refreshRoutes()

    suspend fun loadRoute(file: File): Route = withContext(Dispatchers.IO) {
        FileInputStream(file).use { input ->
            parser.parse(input, file.name)
        }
    }

    suspend fun saveRoute(fileName: String, inputStream: InputStream): File = withContext(Dispatchers.IO) {
        val targetFile = getSafeRouteFile(fileName)
        FileOutputStream(targetFile).use { output ->
            inputStream.copyTo(output)
        }
        refreshRoutes()
        targetFile
    }

    suspend fun saveRoute(fileName: String, bytes: ByteArray): File = withContext(Dispatchers.IO) {
        val targetFile = getSafeRouteFile(fileName)
        FileOutputStream(targetFile).use { output ->
            output.write(bytes)
        }
        refreshRoutes()
        targetFile
    }

    suspend fun deleteRoute(fileName: String): Boolean = withContext(Dispatchers.IO) {
        val targetFile = getSafeRouteFile(fileName)
        val deleted = if (targetFile.exists()) targetFile.delete() else false
        if (deleted) refreshRoutes()
        deleted
    }

    fun getSafeRouteFile(fileName: String): File {
        val sanitizedName = sanitizeFileName(fileName)
        val file = File(routesDir, sanitizedName)
        // Ensure canonical path does not escape routesDir (prevent directory traversal)
        val baseCanonical = routesDir.canonicalPath
        val targetCanonical = file.canonicalPath
        if (!targetCanonical.startsWith(baseCanonical)) {
            throw SecurityException("Path traversal attempt detected: $fileName")
        }
        return file
    }

    private fun sanitizeFileName(name: String): String {
        // Strip any leading path delimiters
        var clean = name.substringAfterLast('/').substringAfterLast('\\')
        clean = clean.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        // Strip leading dots to prevent creating hidden files
        clean = clean.trimStart('.')
        if (clean.isEmpty()) {
            clean = "route"
        }
        if (!clean.endsWith(".gpx", ignoreCase = true)) {
            clean += ".gpx"
        }
        return clean
    }

    companion object {
        private const val TAG = "RouteRepository"

        @Volatile
        private var instance: RouteRepository? = null

        fun getInstance(context: Context): RouteRepository {
            return instance ?: synchronized(this) {
                instance ?: RouteRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
