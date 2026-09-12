package dk.jehaj.simpleroute.data.repository

import android.content.Context
import android.util.Log
import dk.jehaj.simpleroute.data.model.Route
import dk.jehaj.simpleroute.data.parser.GpxParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

class RouteRepository(private val context: Context) {

    private val parser = GpxParser()

    val routesDir: File
        get() = File(context.filesDir, "routes").apply {
            if (!exists()) {
                mkdirs()
            }
        }

    init {
        ensureInitialRoutes()
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

    suspend fun getRouteFiles(): List<File> = withContext(Dispatchers.IO) {
        val files = routesDir.listFiles { _, name ->
            name.endsWith(".gpx", ignoreCase = true)
        }?.toList() ?: emptyList()
        files.sortedBy { it.name }
    }

    suspend fun loadRoute(file: File): Route = withContext(Dispatchers.IO) {
        FileInputStream(file).use { input ->
            parser.parse(input, file.name)
        }
    }

    suspend fun saveRoute(fileName: String, inputStream: InputStream): File = withContext(Dispatchers.IO) {
        val sanitizedName = sanitizeFileName(fileName)
        val targetFile = File(routesDir, sanitizedName)
        FileOutputStream(targetFile).use { output ->
            inputStream.copyTo(output)
        }
        targetFile
    }

    suspend fun saveRoute(fileName: String, bytes: ByteArray): File = withContext(Dispatchers.IO) {
        val sanitizedName = sanitizeFileName(fileName)
        val targetFile = File(routesDir, sanitizedName)
        FileOutputStream(targetFile).use { output ->
            output.write(bytes)
        }
        targetFile
    }

    suspend fun deleteRoute(fileName: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(routesDir, fileName)
        if (file.exists()) file.delete() else false
    }

    private fun sanitizeFileName(name: String): String {
        var clean = name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
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
