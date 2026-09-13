package dk.jehaj.simpleroute.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RouteRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var routesDir: File
    private lateinit var repository: RouteRepository

    @Before
    fun setUp() {
        routesDir = tempFolder.newFolder("routes")
        repository = RouteRepository(baseDir = routesDir)
    }

    @Test
    fun testDeleteRouteByFile() = runBlocking {
        val testFile = File(routesDir, "morning_ride.gpx").apply {
            writeText("<gpx></gpx>")
        }
        repository.refreshRoutes()
        assertEquals(1, repository.getRouteFiles().size)
        assertEquals(1, repository.routeFilesFlow.value.size)

        val deleted = repository.deleteRoute(testFile)
        assertTrue("Route file should be deleted", deleted)
        assertFalse("File should no longer exist on disk", testFile.exists())
        assertEquals(0, repository.getRouteFiles().size)
        assertEquals(0, repository.routeFilesFlow.value.size)
    }

    @Test
    fun testDeleteRouteWithSpacesInName() = runBlocking {
        val testFile = File(routesDir, "Brendstrup - Aarhus.gpx").apply {
            writeText("<gpx></gpx>")
        }
        repository.refreshRoutes()
        assertEquals(1, repository.getRouteFiles().size)

        val deleted = repository.deleteRoute("Brendstrup - Aarhus.gpx")
        assertTrue("Route file with spaces should be deleted successfully", deleted)
        assertFalse("File should no longer exist on disk", testFile.exists())
        assertEquals(0, repository.getRouteFiles().size)
    }

    @Test
    fun testDeleteNonExistentRoute() = runBlocking {
        val deleted = repository.deleteRoute("does_not_exist.gpx")
        assertFalse("Deleting non-existent route should return false", deleted)
    }

    @Test
    fun testPreventPathTraversalOnDeleteFile() = runBlocking {
        val sensitiveFile = tempFolder.newFile("sensitive.txt")
        try {
            repository.deleteRoute(sensitiveFile)
            fail("Should throw SecurityException on path traversal")
        } catch (e: SecurityException) {
            assertTrue("File outside routesDir must remain untouched", sensitiveFile.exists())
        }
    }

    @Test
    fun testPreventPathTraversalOnDeleteFileName() = runBlocking {
        val outsideFile = tempFolder.newFile("outside.gpx")
        try {
            repository.deleteRoute("../outside.gpx")
            // Either throws SecurityException or fails safe without deleting outside file
            assertTrue("Outside file must not be deleted", outsideFile.exists())
        } catch (e: SecurityException) {
            assertTrue("Outside file must not be deleted", outsideFile.exists())
        }
    }
}
