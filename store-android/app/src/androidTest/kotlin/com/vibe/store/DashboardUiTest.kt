package com.vibe.store

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.vibe.store.api.*
import com.vibe.store.presentation.DashboardScreen
import com.vibe.store.presentation.DashboardTimeContext
import com.vibe.store.presentation.DashboardTimeSource
import com.vibe.store.presentation.SpatialTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

class DashboardUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun identity(id: Long, role: String = "employee", rights: Set<String> = setOf("DASHBOARD:READ")) =
        PublicIdentity(id, "user$id", "Test", role.replaceFirstChar { it.uppercase() }, role, rights)

    private fun document(amount: String = "12.3456789", includePresence: Boolean = false, empty: Boolean = false): DocumentModel {
        val rows = if (empty) emptyList() else buildList {
            add(ReportRow(listOf(ReportCell("todayValidatedRevenue"), ReportCell(amount, BigDecimal(amount)))))
            add(ReportRow(listOf(ReportCell("todayValidatedTransactions"), ReportCell("2"))))
            add(ReportRow(listOf(ReportCell("todayAverageBasket"), ReportCell("6.17283945", BigDecimal("6.17283945")))))
            add(ReportRow(listOf(ReportCell("currentMonthValidatedRevenue"), ReportCell(amount, BigDecimal(amount)))))
            add(ReportRow(listOf(ReportCell("outOfStockProducts"), ReportCell("1"))))
            add(ReportRow(listOf(ReportCell("lowStockProducts"), ReportCell("3"))))
            if (includePresence) add(ReportRow(listOf(ReportCell("monthCompletedAttendanceSessions"), ReportCell("4"))))
        }
        val series = if (empty) emptyList() else listOf(ReportSeries("validatedRevenue",
            listOf(BigDecimal("0"), BigDecimal(amount)), listOf("2026-10-08", "2026-10-09")))
        return DocumentModel("dashboard-test", ReportTab.SUMMARY,
            ReportFilter(ReportTab.SUMMARY, LocalDate.parse("2026-10-09"), LocalDate.parse("2026-10-10"), timeZoneId = "UTC"),
            "EUR", listOf(ReportColumn("metric", "Metric"), ReportColumn("value", "Value")), rows, series,
            ReportMetricSnapshot(BigDecimal(amount), 2, BigDecimal("6.17283945"), 0, BigDecimal.ZERO,
                MarginDisclosure.ESTIMATED_SNAPSHOT_COST, "EUR"))
    }

    private inner class Reports(private val respond: suspend (DashboardRequest, Int) -> DocumentModel = { _, _ -> document() }) : ReportingService {
        val calls = AtomicInteger()
        val requests = mutableListOf<DashboardRequest>()
        override suspend fun snapshot(filter: ReportFilter): DocumentModel = error("unexpected report snapshot")
        override suspend fun dashboard(filter: ReportFilter): DocumentModel = error("unexpected legacy dashboard")
        override suspend fun dashboard(request: DashboardRequest): DocumentModel {
            synchronized(requests) { requests += request }
            return respond(request, calls.incrementAndGet())
        }
    }

    private class FakeTimeSource(initial: DashboardTimeContext) : DashboardTimeSource {
        @Volatile var context = initial
        @Volatile var deadline: Instant? = null
        @Volatile var waits = 0
        @Volatile var cancelledWaits = 0
        private val pending = AtomicReference<CompletableDeferred<Unit>?>(null)
        override fun current() = context
        override suspend fun awaitChange(context: DashboardTimeContext) {
            this.deadline = context.nextLocalMidnight()
            waits++
            val signal = CompletableDeferred<Unit>()
            pending.set(signal)
            try { signal.await() } finally {
                if (!currentCoroutineContext().isActive) cancelledWaits++
                pending.compareAndSet(signal, null)
            }
        }
        fun change(next: DashboardTimeContext) {
            context = next
            pending.get()?.complete(Unit)
        }
    }

    private fun show(service: Reports, time: FakeTimeSource) {
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            DashboardScreen(service, identity(901), french = false, timeSource = time)
        } } }
    }

    @Test fun sameLocalDaySchedulesMidnightWithoutRefreshingRepeatedly() {
        val source = FakeTimeSource(DashboardTimeContext(LocalDate.parse("2026-05-10"), "Europe/Rome"))
        val service = Reports()
        show(service, source)
        compose.waitUntil(10_000) { service.calls.get() == 1 && source.waits > 0 }
        assertEquals(1, service.calls.get())
        assertEquals(Instant.parse("2026-05-10T22:00:00Z"), source.deadline)
    }

    @Test fun localMidnightWhileForegroundedRefreshesAndHidesOldSnapshot() {
        val source = FakeTimeSource(DashboardTimeContext(LocalDate.parse("2026-05-10"), "Europe/Rome"))
        val secondResponse = CompletableDeferred<Unit>()
        val service = Reports { _, count ->
            if (count == 1) document("10.11") else { secondResponse.await(); document("11.11") }
        }
        show(service, source)
        compose.waitUntil(10_000) { service.calls.get() == 1 && source.waits > 0 }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("EUR 10.11").fetchSemanticsNodes().size == 2 }
        compose.onAllNodesWithText("EUR 10.11").assertCountEquals(2)
        source.change(DashboardTimeContext(LocalDate.parse("2026-05-11"), "Europe/Rome"))
        compose.waitUntil(10_000) { service.calls.get() == 2 }
        compose.onNodeWithTag("dashboard-loading").assertExists()
        compose.onAllNodesWithText("EUR 10.11").assertCountEquals(0)
        secondResponse.complete(Unit)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("EUR 11.11").fetchSemanticsNodes().size == 2 }
        assertEquals(LocalDate.parse("2026-05-11"), service.requests.last().asOfDate)
    }

    @Test fun timezoneChangeRefreshesWhenLocalDateChanges() {
        val source = FakeTimeSource(DashboardTimeContext(LocalDate.parse("2026-06-01"), "Pacific/Kiritimati"))
        val service = Reports()
        show(service, source)
        compose.waitUntil(10_000) { service.calls.get() == 1 && source.waits > 0 }
        source.change(DashboardTimeContext(LocalDate.parse("2026-05-31"), "Pacific/Honolulu"))
        compose.waitUntil(10_000) { service.calls.get() == 2 }
        assertEquals("Pacific/Honolulu", service.requests.last().timeZoneId)
        assertEquals(LocalDate.parse("2026-05-31"), service.requests.last().asOfDate)
    }

    @Test fun timezoneChangeRefreshesEvenWhenLocalDateIsUnchanged() {
        val source = FakeTimeSource(DashboardTimeContext(LocalDate.parse("2026-06-01"), "Europe/Rome"))
        val service = Reports()
        show(service, source)
        compose.waitUntil(10_000) { service.calls.get() == 1 && source.waits > 0 }
        source.change(DashboardTimeContext(LocalDate.parse("2026-06-01"), "Africa/Accra"))
        compose.waitUntil(10_000) { service.calls.get() == 2 }
        assertEquals("Africa/Accra", service.requests.last().timeZoneId)
    }

    @Test fun localMidnightDeadlineUsesTwentyThreeAndTwentyFiveHourDstDays() {
        val zone = ZoneId.of("Europe/Rome")
        val spring = DashboardTimeContext(LocalDate.parse("2026-03-29"), zone.id)
        val springStart = spring.date.atStartOfDay(zone).toInstant()
        assertEquals(Duration.ofHours(23), Duration.between(springStart, spring.nextLocalMidnight()))
        val autumn = DashboardTimeContext(LocalDate.parse("2026-10-25"), zone.id)
        val autumnStart = autumn.date.atStartOfDay(zone).toInstant()
        assertEquals(Duration.ofHours(25), Duration.between(autumnStart, autumn.nextLocalMidnight()))
    }

    @Test fun disposalCancelsScheduledWaitAndLateOldContextResponseIsDiscarded() {
        val source = FakeTimeSource(DashboardTimeContext(LocalDate.parse("2026-07-01"), "UTC"))
        val oldResponse = CompletableDeferred<Unit>()
        val newResponse = CompletableDeferred<Unit>()
        val service = Reports { _, count ->
            if (count == 1) { oldResponse.await(); document("1.11") }
            else { newResponse.await(); document("2.22") }
        }
        show(service, source)
        compose.waitUntil(10_000) { service.calls.get() == 1 && source.waits > 0 }
        source.change(DashboardTimeContext(LocalDate.parse("2026-07-02"), "UTC"))
        compose.waitUntil(10_000) { service.calls.get() == 2 }
        compose.onNodeWithTag("dashboard-loading").assertExists()
        newResponse.complete(Unit)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("EUR 2.22").fetchSemanticsNodes().size == 2 }
        oldResponse.complete(Unit)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("EUR 1.11").fetchSemanticsNodes().isEmpty() }
        compose.runOnUiThread { compose.activity.setContent { Text("Disposed") } }
        compose.waitUntil(10_000) { source.cancelledWaits > 0 }
    }

    @Test fun authorizedRolesRenderCanonicalSnapshotAndRespectPresenceOmission() {
        for (role in listOf("employee", "manager", "owner")) {
            val service = Reports { _, _ -> document(includePresence = true) }
            compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
                DashboardScreen(service, identity(role.hashCode().toLong(), role), french = false)
            } } }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("dashboard-currency").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("dashboard-currency").assertTextEquals("Currency: EUR")
            compose.onAllNodesWithText("EUR 12.3456789").assertCountEquals(2)
            compose.onNodeWithTag("dashboard-trend-chart").assertExists()
            compose.onNodeWithTag("dashboard-trend-numeric").assertTextContains("2026-10-08", substring = true)
            compose.onNodeWithTag("dashboard-disclosure").assertTextContains("validated sales only", substring = true)
            compose.onNodeWithText("Completed attendance this month").assertDoesNotExist()
            assertEquals(1, service.calls.get())
            assertTrue(service.requests.single().timeZoneId.isNotBlank())
        }
    }

    @Test fun presenceContextRequiresItsOwnEffectivePermission() {
        val service = Reports { _, _ -> document(includePresence = true) }
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            DashboardScreen(service, identity(1, "manager", setOf("DASHBOARD:READ", "PRESENCE:READ")), french = false)
        } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Completed attendance this month").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Completed attendance this month").assertExists()
    }

    @Test fun effectiveDashboardDenialAndBackendDenialNeverExposeSnapshot() {
        val notPermitted = Reports()
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            DashboardScreen(notPermitted, identity(1, rights = emptySet()), french = true)
        } } }
        compose.onNodeWithTag("dashboard-denied").assertExists()
        compose.onNodeWithText("EUR 12.3456789").assertDoesNotExist()
        assertEquals(0, notPermitted.calls.get())

        val backendDenied = Reports { _, _ -> throw SecurityFailure(SecurityError.FORBIDDEN) }
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(true) {
            DashboardScreen(backendDenied, identity(2), french = true)
        } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("dashboard-denied").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("EUR 12.3456789").assertDoesNotExist()
        compose.onNodeWithText("Accès au tableau de bord non autorisé.").assertExists()
    }

    @Test fun emptyAndErrorRetryStatesAreDistinct() {
        val empty = Reports { _, _ -> document(empty = true) }
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            DashboardScreen(empty, identity(1), french = false)
        } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("dashboard-empty").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("EUR 12.3456789").assertDoesNotExist()

        val retry = Reports { _, count -> if (count == 1) error("synthetic failure") else document("3.25") }
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            DashboardScreen(retry, identity(2), french = true)
        } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("dashboard-error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("dashboard-retry").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("EUR 3,25").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, retry.calls.get())
    }

    @Test fun changingIdentityCancelsLateResponseAndLogoutDisposesPrivateSnapshot() {
        val service = Reports { _, count ->
            if (count == 1) {
                withContext(NonCancellable) { delay(600) }
                document("11.11")
            } else document("22.22")
        }
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            var actor by remember { mutableStateOf(1L) }
            var signedIn by remember { mutableStateOf(true) }
            androidx.compose.foundation.layout.Column {
                TextButton(onClick = { actor = 2 }) { Text("Switch synthetic account") }
                TextButton(onClick = { signedIn = false }) { Text("Sign out synthetic account") }
                if (signedIn) key(actor) { DashboardScreen(service, identity(actor), french = false) }
            }
        } } }
        compose.waitUntil(10_000) { service.calls.get() >= 1 }
        compose.onNodeWithText("Switch synthetic account").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("EUR 22.22").fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(750)
        compose.onAllNodesWithText("EUR 22.22").assertCountEquals(2)
        compose.onAllNodesWithText("EUR 11.11").assertCountEquals(0)
        compose.onNodeWithText("Sign out synthetic account").performClick()
        compose.onNodeWithTag("dashboard-screen").assertDoesNotExist()
        compose.onAllNodesWithText("EUR 22.22").assertCountEquals(0)
    }

    @Test fun dashboardAdaptsAcrossWidthsAndFrenchEnglishLabels() {
        for ((width, tag, language) in listOf(Triple(400, "COMPACT", "fr"), Triple(700, "MEDIUM", "en"), Triple(1000, "EXPANDED", "fr"))) {
            val dp = width
            val expected = tag
            val lang = language
            compose.runOnUiThread { compose.activity.setContent { SpatialTheme(dark = lang == "en") {
                Box(Modifier.requiredWidth(dp.dp)) { DashboardScreen(Reports(), identity(dp.toLong()), french = lang == "fr") }
            } } }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("dashboard-layout-$expected").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(if (lang == "fr") "Tableau de bord" else "Dashboard").assertExists()
            if (lang == "fr") compose.onAllNodesWithText("EUR 12,3456789").assertCountEquals(2)
        }
    }
}
