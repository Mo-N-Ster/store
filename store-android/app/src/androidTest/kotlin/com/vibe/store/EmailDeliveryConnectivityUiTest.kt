package com.vibe.store

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/** Uses only the synthetic emulator and restores its original Wi-Fi/airplane settings in finally. */
class EmailDeliveryConnectivityUiTest {
    @Test fun constrainedDeliveryWaitsOfflineAndRunsAfterConnectivityReturns() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val originalAirplane = shell("cmd connectivity airplane-mode").trim().endsWith("enabled")
        val originalWifi = shell("cmd wifi status").contains("Wifi is enabled", ignoreCase = true)
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork(EMAIL_WORK).result.get(10, TimeUnit.SECONDS)
        try {
            shell("svc wifi disable")
            shell("cmd connectivity airplane-mode enable")
            waitFor(10_000) { !hasValidatedInternet(context) }

            EmailDeliveryScheduler.enqueue(context)
            waitFor(10_000) {
                work.getWorkInfosForUniqueWork(EMAIL_WORK).get(3, TimeUnit.SECONDS).any { it.state == WorkInfo.State.ENQUEUED }
            }
            Thread.sleep(1_500)
            assertTrue("network-constrained worker must remain queued while offline",
                work.getWorkInfosForUniqueWork(EMAIL_WORK).get(3, TimeUnit.SECONDS).none { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.SUCCEEDED })

            shell("cmd connectivity airplane-mode disable")
            if (originalWifi) shell("svc wifi enable")
            waitFor(20_000) { hasValidatedInternet(context) }
            waitFor(15_000) {
                work.getWorkInfosForUniqueWork(EMAIL_WORK).get(3, TimeUnit.SECONDS).any { it.state == WorkInfo.State.SUCCEEDED }
            }
        } finally {
            shell("cmd connectivity airplane-mode ${if (originalAirplane) "enable" else "disable"}")
            shell("svc wifi ${if (originalWifi) "enable" else "disable"}")
            work.cancelUniqueWork(EMAIL_WORK).result.get(10, TimeUnit.SECONDS)
        }
        assertEquals(originalAirplane, shell("cmd connectivity airplane-mode").trim().endsWith("enabled"))
        assertEquals(originalWifi, shell("cmd wifi status").contains("Wifi is enabled", ignoreCase = true))
    }

    private fun hasValidatedInternet(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun waitFor(timeout: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        assertTrue("condition not met in ${timeout}ms", condition())
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        val output = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            BufferedReader(InputStreamReader(input)).readText()
        }
        return output
    }
}
