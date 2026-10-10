package com.vibe.store

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Synthetic scheduler test: delayed work cannot contact SMTP or mutate the application database. */
class EmailDeliverySchedulerUiTest {
    @Test fun repeatedSchedulingUsesOneUniqueNonParallelChain() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(EMAIL_WORK).result.get(10, TimeUnit.SECONDS)
        try {
            EmailDeliveryScheduler.enqueue(context, TimeUnit.HOURS.toMillis(2))
            EmailDeliveryScheduler.enqueue(context, TimeUnit.HOURS.toMillis(2))
            val infos = manager.getWorkInfosForUniqueWork(EMAIL_WORK).get(10, TimeUnit.SECONDS)

            assertEquals("both requests must be retained in one unique chain", 2, infos.size)
            assertEquals(1, infos.count { it.state == WorkInfo.State.ENQUEUED })
            assertEquals(1, infos.count { it.state == WorkInfo.State.BLOCKED })
            assertEquals("the chain cannot have concurrent workers", 0, infos.count { it.state == WorkInfo.State.RUNNING })
        } finally {
            manager.cancelUniqueWork(EMAIL_WORK).result.get(10, TimeUnit.SECONDS)
        }
    }
}
