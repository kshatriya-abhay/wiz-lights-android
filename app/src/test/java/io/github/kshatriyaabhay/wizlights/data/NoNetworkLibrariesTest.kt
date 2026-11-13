package io.github.kshatriyaabhay.wizlights.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The app must never talk to the internet: fail if a networking or telemetry library is on the classpath. */
class NoNetworkLibrariesTest {
    private val forbidden = listOf("okhttp", "ktor", "retrofit", "firebase", "play-services", "volley", "cronet", "sentry", "crashlytics")

    @Test
    fun noForbiddenLibraries() {
        val entries = System.getProperty("java.class.path").orEmpty().split(File.pathSeparator).map { File(it).path.lowercase() }
        val hits = entries.filter { e -> forbidden.any { it in e } }
        assertTrue("Forbidden libraries on the classpath:\n" + hits.joinToString("\n"), hits.isEmpty())
    }
}
