package com.vibe.store

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.vibe.store.api.*
import com.vibe.store.presentation.ReportingScreen
import com.vibe.store.presentation.SecurityApp
import com.vibe.store.presentation.SpatialTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class ReportingUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun identity(id: Long = 1, rights: Set<String> = setOf("FINANCES:READ"), role: String = "manager") =
        PublicIdentity(id, "report-user-$id", "Test", "User", role, rights)

    private fun document(filter: ReportFilter, marker: String = "12.3456789"): DocumentModel {
        val currencyUnavailable = filter.tab == ReportTab.PURCHASES || filter.tab == ReportTab.FINANCES
        val columns: List<ReportColumn>
        val cells: List<ReportCell>
        when (filter.tab) {
            ReportTab.SUMMARY -> {
                columns = listOf(ReportColumn("metric", "Metric"), ReportColumn("value", "Value"))
                cells = listOf(ReportCell("estimatedGrossMargin"), ReportCell(marker, BigDecimal(marker)))
            }
            ReportTab.SALES -> {
                columns = listOf(ReportColumn("invoice", "Invoice"), ReportColumn("date", "Date"), ReportColumn("status", "Status"), ReportColumn("revenue", "Recognized revenue"))
                cells = listOf(ReportCell(marker), ReportCell(filter.fromInclusive.toString()), ReportCell("validated"), ReportCell(marker, BigDecimal(marker)))
            }
            ReportTab.PRODUCTS -> {
                columns = listOf(ReportColumn("kind", "Kind"), ReportColumn("name", "Product or category"), ReportColumn("quantity", "Quantity"), ReportColumn("revenue", "Validated revenue"))
                cells = listOf(ReportCell("product"), ReportCell("Historical item"), ReportCell("3"), ReportCell(marker, BigDecimal(marker)))
            }
            ReportTab.STOCK -> {
                columns = listOf(ReportColumn("kind", "Kind"), ReportColumn("name", "Product"), ReportColumn("quantity", "Quantity"), ReportColumn("period", "Recorded at"))
                cells = listOf(ReportCell("currentStock"), ReportCell("Widget"), ReportCell("0"), ReportCell(filter.fromInclusive.toString()))
            }
            ReportTab.PURCHASES -> {
                columns = listOf(ReportColumn("reference", "Purchase"), ReportColumn("date", "Created date"), ReportColumn("status", "Current status"), ReportColumn("total", "Total (unavailable)"), ReportColumn("supplier", "Supplier"))
                cells = listOf(ReportCell("P-1"), ReportCell(filter.fromInclusive.toString()), ReportCell("DRAFT"), ReportCell("Unavailable"), ReportCell("Supplier"))
            }
            ReportTab.TEAM -> {
                columns = listOf(ReportColumn("employee", "Employee"), ReportColumn("start", "Start"), ReportColumn("end", "End"), ReportColumn("status", "Status"), ReportColumn("hours", "Completed hours"))
                cells = listOf(ReportCell("Alex"), ReportCell("08:00"), ReportCell("16:00"), ReportCell("VALID"), ReportCell("8"))
            }
            ReportTab.FINANCES -> {
                columns = listOf(ReportColumn("metric", "Metric"), ReportColumn("value", "Value"), ReportColumn("currency", "Currency"))
                cells = listOf(ReportCell("validatedSalesRevenue"), ReportCell(marker, BigDecimal(marker)), ReportCell("EUR"))
            }
        }
        val series = when (filter.tab) {
            ReportTab.PURCHASES -> listOf(ReportSeries("purchaseCountTotal", listOf(BigDecimal("2"))))
            ReportTab.TEAM -> listOf(ReportSeries("sessions", listOf(BigDecimal("1"))))
            ReportTab.STOCK -> listOf(ReportSeries("movementEntries", listOf(BigDecimal("1"))))
            else -> listOf(ReportSeries("validatedRevenue", listOf(BigDecimal("0"), BigDecimal(marker)),
                listOf(filter.fromInclusive.toString(), filter.toExclusive.minusDays(1).toString())))
        }
        val rows = if (filter.tab == ReportTab.FINANCES) listOf(
            ReportRow(cells),
            ReportRow(listOf(ReportCell("validatedPurchaseTotal"), ReportCell("Unavailable"), ReportCell("XXX"))),
            ReportRow(listOf(ReportCell("salesMinusPurchasesNotProfit"), ReportCell("Not calculated (purchase currency unavailable)"), ReportCell("XXX"))),
        ) else listOf(ReportRow(cells))
        val currency = if (currencyUnavailable) "XXX" else "EUR"
        return DocumentModel("test-${filter.tab.name.lowercase()}", filter.tab, filter, currency,
            columns, rows, series,
            ReportMetricSnapshot(BigDecimal(marker), 1, BigDecimal(marker), 0, BigDecimal("0.005"),
                MarginDisclosure.ESTIMATED_WITH_PRICE_FALLBACK, "EUR"),
            if (currencyUnavailable) ReportCurrencyAvailability.UNAVAILABLE else ReportCurrencyAvailability.AVAILABLE)
    }

    private class Reports(private val respond: suspend (ReportFilter, Int) -> DocumentModel = { f, _ ->
        val unavailable = f.tab == ReportTab.PURCHASES || f.tab == ReportTab.FINANCES
        documentStatic(f, unavailable)
    }) : ReportingService {
        val filters = CopyOnWriteArrayList<ReportFilter>()
        private val count = AtomicInteger()
        override suspend fun snapshot(filter: ReportFilter): DocumentModel {
            filters += filter
            return respond(filter, count.incrementAndGet())
        }
        override suspend fun dashboard(filter: ReportFilter) = documentStatic(filter, false)
        override suspend fun dashboard(request: DashboardRequest) = documentStatic(
            ReportFilter(ReportTab.SUMMARY, request.asOfDate, request.asOfDate.plusDays(1), timeZoneId = request.timeZoneId), false)
    }

    private fun clickTab(tab: ReportTab) {
        compose.onNodeWithTag("reports-tabs").performScrollTo()
        compose.onNodeWithTag("report-tab-${tab.name}").performScrollTo().performClick()
    }

    private fun apply() { compose.onNodeWithTag("reports-apply").performScrollTo().performClick() }

    @Test fun allSevenTabsLoadOnlyTheirAppliedDocumentSnapshot() {
        val reports = Reports()
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            ReportingScreen(reports, identity(), french = false)
        } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-draft").fetchSemanticsNodes().isNotEmpty() }
        for (tab in ReportTab.entries) {
            if (tab != ReportTab.SUMMARY) clickTab(tab)
            apply()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-document-title").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(tab, reports.filters.last().tab)
            compose.onNodeWithTag("reports-document-title").assertTextContains(when (tab) {
                ReportTab.SUMMARY -> "Summary"; ReportTab.SALES -> "Sales"; ReportTab.PRODUCTS -> "Products"
                ReportTab.STOCK -> "Stock"; ReportTab.PURCHASES -> "Purchases"; ReportTab.TEAM -> "Team"; ReportTab.FINANCES -> "Finances"
            }, substring = true)
            if (tab == ReportTab.FINANCES) {
                compose.onNodeWithTag("reports-currency-unavailable").assertExists()
                compose.onNodeWithTag("reports-kpi-value-revenue").assertTextContains("EUR 12.3456789", substring = true)
            }
        }
        assertEquals(7, reports.filters.size)
    }

    @Test fun currencyLabelRequiresRenderedMonetaryContentForStockAndTeam() {
        for (tab in listOf(ReportTab.STOCK, ReportTab.TEAM)) {
            val reports = Reports { filter, _ ->
                val base = documentStatic(filter, false)
                val rows = when (tab) {
                    ReportTab.STOCK -> listOf(ReportRow(listOf(ReportCell("currentStock"), ReportCell("Widget"),
                        ReportCell("3"), ReportCell("2026-10-09"))))
                    ReportTab.TEAM -> listOf(ReportRow(listOf(ReportCell("Synthetic staff"), ReportCell("08:00"),
                        ReportCell("16:00"), ReportCell("VALID"), ReportCell("8"))))
                    else -> error("Only stock and team are tested here")
                }
                DocumentModel(base.reportId, base.tab, base.filter, "EUR", base.columns, rows, base.series,
                    ReportMetricSnapshot(BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO,
                        MarginDisclosure.ESTIMATED_SNAPSHOT_COST, "EUR"), ReportCurrencyAvailability.AVAILABLE)
            }
            compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
                ReportingScreen(reports, identity(), french = false)
            } } }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-draft").fetchSemanticsNodes().isNotEmpty() }
            clickTab(tab)
            apply()
            compose.waitUntil(10_000) { reports.filters.size == 1 && compose.onAllNodesWithTag("reports-document-title").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("reports-currency").assertDoesNotExist()
        }

        val monetary = Reports { filter, _ ->
            val base = documentStatic(filter, false)
            val row = ReportRow(listOf(ReportCell("movementCost"), ReportCell("1.25", BigDecimal("1.25"))))
            DocumentModel(base.reportId, base.tab, base.filter, "EUR", base.columns, listOf(row), base.series,
                ReportMetricSnapshot(BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO,
                    MarginDisclosure.ESTIMATED_SNAPSHOT_COST, null), ReportCurrencyAvailability.AVAILABLE)
        }
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            ReportingScreen(monetary, identity(), french = false)
        } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-draft").fetchSemanticsNodes().isNotEmpty() }
        clickTab(ReportTab.STOCK)
        apply()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-document-title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reports-currency").assertExists()
        compose.onNodeWithTag("reports-table-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("reports-cell-0-1").assertTextContains("1.25", substring = true)
    }

    @Test fun draftChangesStayUnappliedUntilApplyAndPresetCreatesInclusiveDateRange() {
        val reports = Reports()
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            ReportingScreen(reports, identity(), french = false)
        } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-draft").fetchSemanticsNodes().isNotEmpty() }
        clickTab(ReportTab.SALES)
        compose.onNodeWithTag("reports-preset-thirty").performScrollTo().performClick()
        compose.onNodeWithTag("reports-from").performScrollTo().assertExists()
        compose.onNodeWithTag("reports-product-id").performScrollTo().performTextInput("17")
        compose.onNodeWithTag("reports-category").performScrollTo().performTextInput("Snacks")
        assertTrue(reports.filters.isEmpty())
        compose.onNodeWithTag("reports-draft").assertExists()
        apply()
        compose.waitUntil(10_000) { reports.filters.size == 1 && compose.onAllNodesWithTag("reports-document-title").fetchSemanticsNodes().isNotEmpty() }
        val filter = reports.filters.single()
        assertEquals(ReportTab.SALES, filter.tab)
        assertEquals(30L, java.time.temporal.ChronoUnit.DAYS.between(filter.fromInclusive, filter.toExclusive))
        compose.onNodeWithTag("reports-applied-filters").assertExists()
        compose.onNodeWithTag("reports-applied-filters").assertTextContains("Product 17", substring = true)
        compose.onNodeWithTag("reports-applied-filters").assertTextContains("Historical category Snacks", substring = true)
    }

    @Test fun invalidDatesDisableApplyAndUnsupportedFilterControlsAreNotSilentlyShown() {
        val reports = Reports()
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            ReportingScreen(reports, identity(), french = true)
        } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-draft").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reports-from").performScrollTo().performTextClearance()
        compose.onNodeWithTag("reports-from").performTextInput("invalid")
        compose.onNodeWithTag("reports-filter-error").assertExists()
        compose.onNodeWithTag("reports-apply").assertIsNotEnabled()
        compose.onNodeWithTag("reports-reset").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            reports.filters.size == 1 && compose.onAllNodesWithTag("reports-document-title").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(ReportTab.SUMMARY, reports.filters.last().tab)
        clickTab(ReportTab.PURCHASES)
        compose.onNodeWithTag("reports-grain-WEEK").assertDoesNotExist()
        compose.onNodeWithTag("reports-employee-id").assertDoesNotExist()
        compose.onNodeWithText("Ce rapport utilise un regroupement quotidien.").assertExists()
        compose.onNodeWithTag("reports-filter-error").assertDoesNotExist()
        assertEquals(1, reports.filters.size)
    }

    @Test fun financesDenialAndIndividualPurchasePresenceDenialsRenderNoRows() {
        val deniedByFinance = Reports()
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            ReportingScreen(deniedByFinance, identity(rights = emptySet()), french = false)
        } } }
        compose.onNodeWithTag("reports-denied").assertExists()
        compose.onNodeWithTag("reports-apply").assertDoesNotExist()
        assertTrue(deniedByFinance.filters.isEmpty())

        for (tab in listOf(ReportTab.PURCHASES, ReportTab.TEAM)) {
            val reports = Reports { _, _ -> throw SecurityFailure(SecurityError.FORBIDDEN) }
            compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
                ReportingScreen(reports, identity(), french = false)
            } } }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-draft").fetchSemanticsNodes().isNotEmpty() }
            clickTab(tab)
            apply()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-denied").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("reports-row-0").assertDoesNotExist()
            assertEquals(tab, reports.filters.single().tab)
        }
    }

    @Test fun changedAppliedFilterDiscardsLateResponseAndHidesPreviousSnapshot() {
        val gate = CompletableDeferred<Unit>()
        val reports = Reports { filter, count ->
            if (count == 1) {
                withContext(NonCancellable) { gate.await() }
                documentStatic(filter, false, "1.11")
            } else documentStatic(filter, false, "2.22")
        }
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            ReportingScreen(reports, identity(), french = false)
        } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-draft").fetchSemanticsNodes().isNotEmpty() }
        apply()
        compose.waitUntil(10_000) { reports.filters.size == 1 }
        compose.onNodeWithTag("reports-from").performScrollTo().performTextClearance()
        compose.onNodeWithTag("reports-from").performTextInput("2026-01-02")
        apply()
        compose.waitUntil(10_000) { reports.filters.size == 2 && compose.onAllNodesWithTag("reports-table-toggle").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reports-table-toggle").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-cell-0-1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reports-cell-0-1").assertTextContains("2.22")
        gate.complete(Unit)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-cell-0-1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reports-cell-0-1").assertTextContains("2.22")
    }

    @Test fun accountSwitchDisposesOldReportAndRejectsItsLateResponse() {
        val gate = CompletableDeferred<Unit>()
        val reports = Reports { filter, count ->
            if (count == 1) {
                withContext(NonCancellable) { gate.await() }
                documentStatic(filter, false, "1.11")
            } else documentStatic(filter, false, "2.22")
        }
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            var account by remember { mutableStateOf(1L) }
            Column {
                TextButton(onClick = { account = 2L }) { Text("Switch account") }
                key(account) { ReportingScreen(reports, identity(account), french = false) }
            }
        } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-draft").fetchSemanticsNodes().isNotEmpty() }
        apply()
        compose.waitUntil(10_000) { reports.filters.size == 1 }
        compose.onNodeWithText("Switch account").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-draft").fetchSemanticsNodes().isNotEmpty() }
        apply()
        compose.waitUntil(10_000) { reports.filters.size == 2 && compose.onAllNodesWithTag("reports-table-toggle").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reports-table-toggle").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-cell-0-1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reports-cell-0-1").assertTextContains("2.22")
        gate.complete(Unit)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-cell-0-1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reports-cell-0-1").assertTextContains("2.22")
    }

    @Test fun emptyErrorRetryExactMoneyMarginUnavailableCurrencyAndChartEquivalent() {
        val empty = Reports { f, _ ->
            val base = documentStatic(f, f.tab == ReportTab.PURCHASES)
            DocumentModel(base.reportId, base.tab, base.filter, base.currency, base.columns, emptyList(), emptyList(), base.metrics,
                base.currencyAvailability)
        }
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(true) {
            ReportingScreen(empty, identity(), french = false)
        } } }
        apply()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-empty").fetchSemanticsNodes().isNotEmpty() }

        val retry = Reports { f, count -> if (count == 1) error("offline") else documentStatic(f, false, "12.3456789") }
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            ReportingScreen(retry, identity(), french = true)
        } } }
        apply()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reports-retry").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-document-title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reports-table-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("reports-cell-0-1").assertTextContains("12,3456789", substring = true)
        compose.onNodeWithText("Chiffre d’affaires validé", substring = true).assertExists()
        compose.onNodeWithText("Transactions validées", substring = true).assertExists()
        compose.onNodeWithText("Panier moyen", substring = true).assertExists()
        compose.onNodeWithTag("reports-margin-disclosure").assertExists()
        compose.onNodeWithTag("reports-chart-validatedRevenue").assertContentDescriptionContains("EUR 12,3456789", substring = true)
        val unavailable = Reports()
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(true) {
            ReportingScreen(unavailable, identity(), french = true)
        } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-draft").fetchSemanticsNodes().isNotEmpty() }
        clickTab(ReportTab.PURCHASES); apply()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-currency-unavailable").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reports-table-toggle").performScrollTo().performClick()
        compose.onNodeWithText("Indisponible").assertExists()
        compose.onNodeWithTag("reports-kpi-revenue").assertDoesNotExist()
        compose.onNodeWithText("EUR 0", substring = true).assertDoesNotExist()
        compose.onAllNodesWithText("0,00").assertCountEquals(0)
    }

    @Test fun resetClearsOldSnapshotBeforeLoadingCurrentTabDefaults() {
        val gate = CompletableDeferred<Unit>()
        val reports = Reports { filter, count ->
            if (count == 2) gate.await()
            documentStatic(filter, false, if (count == 1) "1.11" else "2.22")
        }
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            ReportingScreen(reports, identity(), french = false)
        } } }
        apply()
        compose.waitUntil(10_000) { reports.filters.size == 1 && compose.onAllNodesWithTag("reports-document-title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reports-reset").performScrollTo().performClick()
        compose.waitUntil(10_000) { reports.filters.size == 2 }
        compose.onNodeWithTag("reports-loading").assertExists()
        compose.onNodeWithTag("reports-document-title").assertDoesNotExist()
        assertEquals(ReportTab.SUMMARY, reports.filters.last().tab)
        assertEquals(LocalDate.now(ZoneId.systemDefault()).minusDays(6), reports.filters.last().fromInclusive)
        assertEquals(LocalDate.now(ZoneId.systemDefault()).plusDays(1), reports.filters.last().toExclusive)
        gate.complete(Unit)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-document-title").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun detailedTableHasAccessibleExpansionAndCollapseControl() {
        val reports = Reports()
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            ReportingScreen(reports, identity(), french = false)
        } } }
        apply()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-document-title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reports-cell-0-1").assertDoesNotExist()
        compose.onNodeWithTag("reports-table-toggle").assertContentDescriptionContains("Expand detailed table")
            .performScrollTo().performClick()
        compose.onNodeWithTag("reports-cell-0-1").assertExists()
        compose.onNodeWithTag("reports-table-toggle").assertContentDescriptionContains("Collapse detailed table").performClick()
        compose.onNodeWithTag("reports-cell-0-1").assertDoesNotExist()
    }

    @Test fun detailedTableAdaptsToWidthAndLongSnapshotsStayWithinBound() {
        for ((width, expectsHeader) in listOf(400 to false, 700 to true)) {
            val reports = Reports { filter, _ ->
                val base = documentStatic(filter, false)
                val rows = (0 until 200).map { index ->
                    ReportRow(listOf(ReportCell("row-$index"), ReportCell(index.toString())))
                }
                DocumentModel(base.reportId, base.tab, base.filter, base.currency, base.columns, rows,
                    base.series, base.metrics, base.currencyAvailability)
            }
            compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
                Box(Modifier.requiredWidth(width.dp)) { ReportingScreen(reports, identity(), french = false) }
            } } }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-draft").fetchSemanticsNodes().isNotEmpty() }
            apply()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-document-title").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("reports-table-toggle").performScrollTo().assertTextContains("200", substring = true)
            compose.onNodeWithTag("reports-table-toggle").performClick()
            if (expectsHeader) {
                compose.onNodeWithTag("reports-table-header").assertExists()
                compose.onAllNodesWithText("Metric").assertCountEquals(1)
                compose.onAllNodesWithText("Value").assertCountEquals(1)
            } else {
                compose.onNodeWithTag("reports-table-header").assertDoesNotExist()
                compose.onAllNodesWithText("Metric").assertCountEquals(200)
            }
            compose.onNodeWithTag("reports-row-0").assertExists()
            compose.onNodeWithTag("reports-row-199").assertExists()
        }
    }

    @Test fun stopHidesSnapshotAndResumeReloadsTheAppliedFilter() {
        lateinit var owner: TestLifecycleOwner
        val reports = Reports()
        compose.runOnUiThread {
            owner = TestLifecycleOwner()
            compose.activity.setContent { SpatialTheme(false) {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    ReportingScreen(reports, identity(), french = false)
                }
            } }
        }
        apply()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-document-title").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnUiThread { owner.moveTo(Lifecycle.State.CREATED) }
        compose.onNodeWithTag("reports-stopped").assertExists()
        compose.onNodeWithTag("reports-document-title").assertDoesNotExist()
        compose.runOnUiThread { owner.moveTo(Lifecycle.State.RESUMED) }
        compose.waitUntil(10_000) { reports.filters.size == 2 && compose.onAllNodesWithTag("reports-document-title").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun frenchEnglishAndAllWindowWidthsRemainReadable() {
        for ((width, language) in listOf(400 to true, 700 to false, 1000 to true)) {
            compose.runOnUiThread { compose.activity.setContent { SpatialTheme(dark = !language) {
                Box(Modifier.requiredWidth(width.dp)) {
                    ReportingScreen(Reports(), identity(), french = language)
                }
            } } }
            compose.onNodeWithText(if (language) "Rapports" else "Reports").assertExists()
            compose.onNodeWithTag("reports-apply").assertExists()
            compose.onNodeWithTag("reports-reset").assertExists()
        }
    }

    @Test fun reportKpiExplanationsAreLocalizedAndLinkToHelpWithoutChangingMetrics() {
        val reports = Reports()
        val helpTopic = java.util.concurrent.atomic.AtomicReference<String?>()
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(dark = false) {
            ReportingScreen(reports, identity(), french = true, onOpenHelp = { helpTopic.set(it) })
        } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-draft").fetchSemanticsNodes().isNotEmpty() }
        apply()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-kpi-revenue").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reports-help-revenue-action").performScrollTo().performClick()
        compose.onNodeWithTag("reports-help-revenue-details").assertExists()
        compose.onNodeWithTag("reports-help-revenue-explanation").assertTextContains("Somme des revenus reconnus", substring = true)
        compose.onNodeWithTag("reports-help-revenue-open-help").performScrollTo().performClick()
        assertEquals("reports", helpTopic.get())
        compose.onNodeWithTag("reports-kpi-value-revenue").assertExists()
    }

    @Test fun reportHelpControlWorksInCompactAndExpandedWidths() {
        for (width in listOf(400, 1000)) {
            val reports = Reports()
            compose.runOnUiThread { compose.activity.setContent { SpatialTheme(dark = width > 800) {
                Box(Modifier.requiredWidth(width.dp)) {
                    ReportingScreen(reports, identity(), french = width < 800)
                }
            } } }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-draft").fetchSemanticsNodes().isNotEmpty() }
            apply()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-kpi-revenue").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("reports-help-revenue-action").performScrollTo().performClick()
            compose.onNodeWithTag("reports-help-revenue-explanation").assertExists()
            compose.onNodeWithTag("reports-help-revenue-details").assertExists()
        }
    }

    @Test fun authorizedShellAddsReportsAndEnforcesEffectiveRightBeforeNavigation() {
        val user = identity()
        val identityService = identityService(user)
        val reports = Reports()
        compose.runOnUiThread { compose.activity.setContent {
            SecurityApp(identityService, reporting = reports, back = {})
        } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("authenticated-name").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav-reports").assertExists().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-screen").fetchSemanticsNodes().isNotEmpty() }
        apply()
        compose.waitUntil(10_000) { reports.filters.size == 1 }
        assertEquals(ReportTab.SUMMARY, reports.filters.single().tab)
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
        fun moveTo(state: Lifecycle.State) { registry.currentState = state }
    }

    private fun identityService(user: PublicIdentity) = object : IdentityService {
        override suspend fun needsOwner() = false
        override suspend fun bootstrap(input: OwnerRegistration) = error("unused")
        override suspend fun login(input: Credentials) = user
        override suspend fun current() = user
        override suspend fun switchUser(input: Credentials, confirmDiscard: Boolean) = user
        override suspend fun logout(confirmDiscard: Boolean) = Unit
        override suspend fun recoveryQuestion(identifier: String): RecoveryQuestion = error("unused")
        override suspend fun recover(input: RecoveryProof) = Unit
        override suspend fun definePassword(input: PasswordDefinition) = Unit
        override suspend fun permissions(accountId: Long): PermissionView = error("unused")
        override suspend fun restrict(input: DenialChange): PermissionView = error("unused")
        override suspend fun settings() = FoundationSettings(storeName = "Synthetic shop")
        override suspend fun configure(input: FoundationSettings) = Unit
        override suspend fun preferences() = DisplayPreferences("en", "light")
        override suspend fun preferences(input: DisplayPreferences) = Unit
    }
}

private fun documentStatic(filter: ReportFilter, unavailable: Boolean, marker: String = "12.3456789"): DocumentModel {
    val currency = if (unavailable) "XXX" else "EUR"
    val columns = listOf(ReportColumn("metric", "Metric"), ReportColumn("value", "Value"))
    val row = ReportRow(listOf(ReportCell("estimatedGrossMargin"), ReportCell(marker, BigDecimal(marker))))
    val series = when (filter.tab) {
        ReportTab.PURCHASES -> listOf(ReportSeries("purchaseCountTotal", listOf(BigDecimal("2")), listOf("Selected period")))
        ReportTab.STOCK -> listOf(ReportSeries("movementEntries", listOf(BigDecimal("1")), listOf("Selected period")))
        ReportTab.TEAM -> listOf(ReportSeries("sessions", listOf(BigDecimal("1")), listOf("Selected period")))
        else -> listOf(ReportSeries("validatedRevenue", listOf(BigDecimal("0"), BigDecimal(marker)),
            listOf(filter.fromInclusive.toString(), filter.toExclusive.minusDays(1).toString())))
    }
    val metrics = if (filter.tab in setOf(ReportTab.SUMMARY, ReportTab.SALES, ReportTab.PRODUCTS, ReportTab.FINANCES))
        ReportMetricSnapshot(BigDecimal(marker), 1, BigDecimal(marker), 0, BigDecimal("0.005"),
            MarginDisclosure.ESTIMATED_WITH_PRICE_FALLBACK, "EUR")
    else ReportMetricSnapshot(BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO,
        MarginDisclosure.ESTIMATED_WITH_PRICE_FALLBACK, null)
    return DocumentModel("static-${filter.tab}", filter.tab, filter, currency, columns, listOf(row), series,
        metrics,
        if (unavailable) ReportCurrencyAvailability.UNAVAILABLE else ReportCurrencyAvailability.AVAILABLE)
}
