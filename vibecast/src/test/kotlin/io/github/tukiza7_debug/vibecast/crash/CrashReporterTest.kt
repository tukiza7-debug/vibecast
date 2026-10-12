package io.github.tukiza7_debug.vibecast.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReporterTest {
    private val stack =
        "java.lang.IllegalStateException: boom\n" +
            "\tat com.example.Foo.bar(Foo.kt:1)\n" +
            "\tat com.example.Baz.qux(Baz.kt:2)\n"

    @Test
    fun `buildReport contains version, device, breadcrumb and stack`() {
        val report =
            CrashReporter.buildReport(
                versionName = "1.4.1",
                versionCode = "1004001",
                androidRelease = "14",
                sdkInt = 34,
                manufacturer = "Samsung",
                model = "SM-S911B",
                lastRoute = "detail/org.archive.publicdomain/https%3A%2F%2Fx",
                threadName = "main",
                stackTrace = stack,
            )

        assertTrue(report.startsWith("VibeCast 1.4.1 (1004001)\n"))
        assertTrue(report.contains("Android 14 (SDK 34), Samsung SM-S911B"))
        assertTrue(report.contains("Last route: detail/org.archive.publicdomain/https%3A%2F%2Fx"))
        assertTrue(report.contains("Thread: main"))
        assertTrue(report.contains("IllegalStateException: boom"))
        assertTrue(report.contains("\tat com.example.Foo.bar(Foo.kt:1)"))
    }

    @Test
    fun `buildReport falls back to unknown route`() {
        val report =
            CrashReporter.buildReport(
                versionName = "1.0.0",
                versionCode = "1",
                androidRelease = "12",
                sdkInt = 31,
                manufacturer = "Google",
                model = "Pixel 6",
                lastRoute = null,
                threadName = "DefaultDispatcher",
                stackTrace = stack,
            )

        assertTrue(report.contains("Last route: unknown"))
        assertTrue(report.contains("Thread: DefaultDispatcher"))
    }

    @Test
    fun `stackTraceOf renders the exception and causes`() {
        val trace = CrashReporter.stackTraceOf(IllegalStateException("outer", RuntimeException("inner")))
        assertTrue(trace.contains("java.lang.IllegalStateException: outer"))
        assertTrue(trace.contains("Caused by: java.lang.RuntimeException: inner"))
    }

    @Test
    fun `preview keeps only the first lines`() {
        val report = (1..30).joinToString("\n") { "line $it" }
        val preview = CrashReporter.preview(report)
        assertEquals(12, preview.lines().size)
        assertEquals("line 1", preview.lines().first())
        assertFalse(preview.contains("line 30"))
        assertTrue(CrashReporter.preview(report, lines = 3).lines().size == 3)
    }
}
