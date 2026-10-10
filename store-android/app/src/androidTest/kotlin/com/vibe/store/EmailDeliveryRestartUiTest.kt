package com.vibe.store

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Run prepareForProcessRestart, force-stop only com.vibe.store.debug, then run verifyAfterProcessRestart. */
class EmailDeliveryRestartUiTest {
    @Test fun prepareForProcessRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork(EMAIL_WORK).result.get(10, TimeUnit.SECONDS)
        EmailDeliveryScheduler.enqueue(context, TimeUnit.HOURS.toMillis(2))
        val entries = work.getWorkInfosForUniqueWork(EMAIL_WORK).get(10, TimeUnit.SECONDS)
        assertEquals(1, entries.size)
        assertEquals(WorkInfo.State.ENQUEUED, entries.single().state)
    }

    @Test fun verifyAfterProcessRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val work = WorkManager.getInstance(context)
        val entries = work.getWorkInfosForUniqueWork(EMAIL_WORK).get(10, TimeUnit.SECONDS)
        try {
            assertEquals("WorkManager must restore the unique delayed request after app process restart", 1, entries.size)
            assertEquals(WorkInfo.State.ENQUEUED, entries.single().state)
            assertTrue(entries.single().tags.any { it.contains("EmailDeliveryWorker") })
        } finally {
            work.cancelUniqueWork(EMAIL_WORK).result.get(10, TimeUnit.SECONDS)
        }
    }
}
