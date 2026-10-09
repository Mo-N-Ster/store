package com.vibe.store

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.key
import androidx.compose.material3.Surface
import androidx.core.view.WindowCompat
import com.vibe.store.api.*
import com.vibe.store.presentation.ReportingScreen
import com.vibe.store.presentation.SpatialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal

/** Screenshot-only test data: synthetic report values and a synthetic manager identity. */
class ReportingScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun captureSevenReportsAndRepresentativePresentationVariants() {
        val service = ScreenshotReports()
        val args = InstrumentationRegistry.getArguments()
        val selected = args.getString("captureTab")?.let { ReportTab.valueOf(it) }
        val french = args.getString("french") == "true"
        val dark = args.getString("dark") == "true"
        val tabs = selected?.let(::listOf) ?: ReportTab.entries
        val widthDp = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.screenWidthDp
        val widthClass = when { widthDp < 600 -> "compact"; widthDp < 840 -> "medium"; else -> "expanded" }
        val theme = if (dark) "dark" else "light"
        val language = if (french) "fr" else "en"
        for (tab in tabs) {
            compose.runOnUiThread { compose.activity.setContent {
                SpatialTheme(dark = dark) {
                    Surface(Modifier.fillMaxSize()) {
                        key(tab) { ReportingScreen(service, identity(), french = french) }
                    }
                }
            } }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-draft").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(!dark, WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
                .isAppearanceLightStatusBars)
            if (tab != ReportTab.SUMMARY) compose.onNodeWithTag("report-tab-${tab.name}").performScrollTo().performClick()
            compose.onNodeWithTag("reports-apply").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("reports-document-title").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("reports-document-title").performScrollTo()
            capture("${widthClass}_${language}_${theme}_${tab.name.lowercase()}")
            if (tab == ReportTab.FINANCES) {
                compose.onNodeWithTag("reports-margin-disclosure").performScrollTo()
                capture("${widthClass}_${language}_${theme}_finances_metrics")
            }
            if (tab == ReportTab.PURCHASES) {
                compose.onNodeWithTag("reports-table-toggle").performScrollTo().performClick()
                compose.onNodeWithTag("reports-row-0").performScrollTo()
                capture("${widthClass}_${language}_${theme}_purchases_table")
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "i09_$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun identity() = PublicIdentity(9901, "synthetic-report", "Synthetic", "Manager", "manager", setOf("FINANCES:READ"))

    private inner class ScreenshotReports : ReportingService {
        override suspend fun snapshot(filter: ReportFilter) = doc(filter)
        override suspend fun dashboard(filter: ReportFilter) = doc(filter)
        override suspend fun dashboard(request: DashboardRequest) = doc(ReportFilter(ReportTab.SUMMARY,
            request.asOfDate, request.asOfDate.plusDays(1), timeZoneId = request.timeZoneId))
    }

    private fun doc(filter: ReportFilter): DocumentModel {
        val money = listOf(BigDecimal("0"), BigDecimal("12.3456789"), BigDecimal("25.005"))
        val labels = listOf("2026-10-01", "2026-10-02", "2026-10-03")
        val rows: List<ReportRow>
        val columns: List<ReportColumn>
        val series: List<ReportSeries>
        when (filter.tab) {
            ReportTab.SUMMARY -> {
                columns = listOf(ReportColumn("metric", "Metric"), ReportColumn("value", "Value"))
                rows = listOf(ReportRow(listOf(ReportCell("validatedRevenue"), ReportCell("1234.56789", BigDecimal("1234.56789")))),
                    ReportRow(listOf(ReportCell("cancelledTransactions"), ReportCell("2"))))
                series = listOf(ReportSeries("validatedRevenue", money, labels))
            }
            ReportTab.SALES -> {
                columns = listOf(ReportColumn("invoice", "Invoice"), ReportColumn("date", "Date"), ReportColumn("status", "Status"), ReportColumn("revenue", "Recognized revenue"))
                rows = (1..8).map { i -> ReportRow(listOf(ReportCell("INV-$i"), ReportCell(labels[(i - 1) % 3]), ReportCell(if (i == 8) "cancelled" else "validated"), ReportCell(if (i == 8) "—" else "12.${i}45", if (i == 8) null else BigDecimal("12.${i}45")))) }
                series = listOf(ReportSeries("validatedRevenue", money, labels))
            }
            ReportTab.PRODUCTS -> {
                columns = listOf(ReportColumn("kind", "Kind"), ReportColumn("name", "Product or category"), ReportColumn("quantity", "Quantity"), ReportColumn("revenue", "Validated revenue"))
                rows = (1..8).map { i -> ReportRow(listOf(ReportCell("product"), ReportCell("Synthetic product $i"), ReportCell("$i"), ReportCell("12.${i}45", BigDecimal("12.${i}45")))) }
                series = listOf(ReportSeries("productPrice:42", money, labels))
            }
            ReportTab.STOCK -> {
                columns = listOf(ReportColumn("kind", "Kind"), ReportColumn("name", "Product"), ReportColumn("quantity", "Quantity"), ReportColumn("period", "Recorded at"))
                rows = (1..8).map { i -> ReportRow(listOf(ReportCell("currentStock"), ReportCell("Synthetic product $i"), ReportCell("${i - 1}"), ReportCell(labels[(i - 1) % 3]))) }
                series = listOf(ReportSeries("movementEntries", listOf(BigDecimal("3")), listOf("Period total")), ReportSeries("movementExits", listOf(BigDecimal("2")), listOf("Period total")))
            }
            ReportTab.PURCHASES -> {
                columns = listOf(ReportColumn("reference", "Purchase"), ReportColumn("date", "Created date"), ReportColumn("status", "Current status"), ReportColumn("total", "Total (unavailable)"), ReportColumn("supplier", "Supplier"))
                rows = (1..8).map { i -> ReportRow(listOf(ReportCell("PO-$i"), ReportCell(labels[(i - 1) % 3]), ReportCell(listOf("VALIDATED", "DRAFT", "CANCELLED")[i % 3]), ReportCell("Unavailable"), ReportCell("Synthetic supplier"))) }
                series = listOf(ReportSeries("purchaseCountTotal", listOf(BigDecimal("8")), listOf("Selected period")), ReportSeries("purchaseCountValidated", listOf(BigDecimal("3")), listOf("Selected period")), ReportSeries("purchaseCountDraft", listOf(BigDecimal("3")), listOf("Selected period")), ReportSeries("purchaseCountCancelled", listOf(BigDecimal("2")), listOf("Selected period")))
            }
            ReportTab.TEAM -> {
                columns = listOf(ReportColumn("employee", "Employee"), ReportColumn("start", "Start"), ReportColumn("end", "End"), ReportColumn("status", "Status"), ReportColumn("hours", "Completed hours"))
                rows = (1..8).map { i -> ReportRow(listOf(ReportCell("Synthetic staff $i"), ReportCell("08:00"), ReportCell("16:00"), ReportCell("VALID"), ReportCell("8"))) }
                series = listOf(ReportSeries("sessions", listOf(BigDecimal("8")), listOf("Selected period")), ReportSeries("completedHours", listOf(BigDecimal("64")), listOf("Selected period")))
            }
            ReportTab.FINANCES -> {
                columns = listOf(ReportColumn("metric", "Metric"), ReportColumn("value", "Value"), ReportColumn("currency", "Currency"))
                rows = listOf(ReportRow(listOf(ReportCell("validatedSalesRevenue"), ReportCell("1234.56789", BigDecimal("1234.56789")), ReportCell("EUR"))),
                    ReportRow(listOf(ReportCell("validatedPurchaseTotal"), ReportCell("Unavailable"), ReportCell("XXX"))),
                    ReportRow(listOf(ReportCell("salesMinusPurchasesNotProfit"), ReportCell("Not calculated (purchase currency unavailable)"), ReportCell("XXX"))))
                series = listOf(ReportSeries("validatedRevenue", money, labels))
            }
        }
        val salesMetricTab = filter.tab in setOf(ReportTab.SUMMARY, ReportTab.SALES, ReportTab.PRODUCTS, ReportTab.FINANCES)
        val metrics = if (salesMetricTab) ReportMetricSnapshot(BigDecimal("1234.56789"), 18,
            BigDecimal("68.587105"), 2, BigDecimal("45.005"), MarginDisclosure.ESTIMATED_SNAPSHOT_COST, "EUR")
        else ReportMetricSnapshot(BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO,
            MarginDisclosure.ESTIMATED_SNAPSHOT_COST, null)
        val unavailable = filter.tab == ReportTab.PURCHASES || filter.tab == ReportTab.FINANCES
        return DocumentModel("synthetic-${filter.tab.name}", filter.tab, filter, if (unavailable) "XXX" else "EUR",
            columns, rows, series, metrics,
            if (unavailable) ReportCurrencyAvailability.UNAVAILABLE else ReportCurrencyAvailability.AVAILABLE)
    }
}
