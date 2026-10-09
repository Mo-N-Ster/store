package com.vibe.store.infrastructure.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vibe.store.api.*
import com.vibe.store.application.persistence.CommandCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Room reporting projections use only this UUID-scoped synthetic database. */
class ReportingNativeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun reportTabsUseExactValidatedTotalsCurrentStockAndCompletedHours() = runBlocking {
        val root = File(context.noBackupFilesDir, "i09-reporting/${UUID.randomUUID()}")
        val owner = RoomDatabaseOwner(context, root)
        try {
            CommandCoordinator(owner).execute {
                val dao = (this as RoomRepositories).dao
                dao.fixture()
                dao.setting(SettingEntity("currency", "EUR"))
                dao.invoice(InvoiceEntity("SYN-CANCELLED", 1, 1, STAMP, BigDecimal("10.005"), BigDecimal("9.005"), BigDecimal.ONE,
                    "cancelled", "SYN-CANCELLED-KEY", 1, "cancelled-command", currency = "XAF"))
                dao.invoiceLine(InvoiceLineEntity(2, "SYN-CANCELLED", 1, "Historical product", "Historical category", 1,
                    BigDecimal("10.005"), BigDecimal("10.005"), BigDecimal("8.005")))
                dao.purchase(PurchaseEntity(2, "SYN-VALIDATED-PUR", 1, "VALIDATED", BigDecimal("0.123456789"), 1, STAMP))
                dao.attendance(AttendanceEntity(2, 1, "2026-01-02T04:05:05.000Z", "MANUAL", "CORRECTED",
                    endTime = "2026-01-02T05:05:05.000Z", sessionRef = "SYN-COMPLETED"))
            }

            val interval = LocalDate.parse("2026-01-02")
            val sales = owner.read { reporting.snapshot(ReportFilter(ReportTab.SALES, interval, interval.plusDays(1))) }
            assertEquals(1L, sales.metrics.transactionCount)
            assertEquals(1L, sales.metrics.cancelledCount)
            assertEquals(0, BigDecimal("25.0").compareTo(sales.metrics.revenue))
            assertEquals("XAF", sales.currency)
            assertEquals(listOf("2026-01-02"), sales.series.single().labels)
            assertEquals(0, BigDecimal("25.0").compareTo(sales.series.single().points.single()))
            assertEquals("Recognized revenue", sales.columns.last().label)
            val cancelledRow = sales.rows.single { it.cells.first().text == "SYN-CANCELLED" }
            assertEquals("cancelled", cancelledRow.cells[2].text)
            assertEquals("Excluded", cancelledRow.cells[3].text)
            assertNull(cancelledRow.cells[3].monetaryValue)

            val historicalCategory = owner.read { reporting.snapshot(ReportFilter(ReportTab.SALES, interval, interval.plusDays(1), category = "Historical category")) }
            assertEquals(1L, historicalCategory.metrics.transactionCount)
            assertEquals(0, BigDecimal("25.0").compareTo(historicalCategory.metrics.revenue))

            val products = owner.read { reporting.snapshot(ReportFilter(ReportTab.PRODUCTS, interval, interval.plusDays(1))) }
            assertTrue(products.rows.any { it.cells[0].text == "CATEGORY" && it.cells[1].text == "Historical category" })
            assertEquals(BigDecimal("12.5"), products.series.single { it.key == "productPrice:1" }.points.single())

            val matchingProductCategory = owner.read { reporting.snapshot(ReportFilter(
                ReportTab.PRODUCTS, interval, interval.plusDays(1), productId = 1, category = "Historical category",
            )) }
            assertTrue(matchingProductCategory.rows.any { it.cells[0].text == "CATEGORY" && it.cells[1].text == "Historical category" })
            assertEquals(BigDecimal("12.5"), matchingProductCategory.series.single { it.key == "productPrice:1" }.points.single())
            val currentButNotHistoricalCategory = owner.read { reporting.snapshot(ReportFilter(
                ReportTab.PRODUCTS, interval, interval.plusDays(1), productId = 1, category = "Test",
            )) }
            assertTrue(currentButNotHistoricalCategory.rows.none { it.cells[0].text == "CATEGORY" })
            assertTrue(currentButNotHistoricalCategory.series.isEmpty())

            val cancelled = owner.read { reporting.snapshot(ReportFilter(ReportTab.SALES, interval, interval.plusDays(1), status = "cancelled")) }
            assertEquals(0L, cancelled.metrics.transactionCount)
            assertEquals(1L, cancelled.metrics.cancelledCount)
            assertEquals(1, cancelled.rows.size)

            val purchases = owner.read { reporting.snapshot(ReportFilter(ReportTab.PURCHASES, interval, interval.plusDays(1))) }
            assertTrue(purchases.rows.any { it.cells.first().text == "SYN-VALIDATED-PUR" })
            assertEquals(2L, purchases.series.single { it.key == "purchaseCountTotal" }.points.single().longValueExact())
            assertEquals(1L, purchases.series.single { it.key == "purchaseCountDraft" }.points.single().longValueExact())
            assertEquals(1L, purchases.series.single { it.key == "purchaseCountValidated" }.points.single().longValueExact())
            val validatedPurchase = purchases.rows.single { it.cells.first().text == "SYN-VALIDATED-PUR" }
            assertEquals(ReportCurrencyAvailability.UNAVAILABLE, purchases.currencyAvailability)
            assertEquals("XXX", purchases.currency)
            assertEquals("Total (unavailable)", purchases.columns[3].label)
            assertEquals("Unavailable", validatedPurchase.cells[3].text)
            assertNull(validatedPurchase.cells[3].monetaryValue)

            val finances = owner.read { reporting.snapshot(ReportFilter(ReportTab.FINANCES, interval, interval.plusDays(1))) }
            assertEquals(ReportCurrencyAvailability.UNAVAILABLE, finances.currencyAvailability)
            assertEquals("XXX", finances.currency)
            assertEquals("XAF", finances.metrics.currency)
            assertEquals("XAF", finances.rows.first().cells[2].text)
            assertEquals("Unavailable", finances.rows[1].cells[1].text)
            assertEquals("Unavailable", finances.rows[1].cells[2].text)
            assertEquals("Not calculated (purchase currency unavailable)", finances.rows[2].cells[1].text)
            assertNull(finances.rows[2].cells[1].monetaryValue)

            val stock = owner.read { reporting.snapshot(ReportFilter(ReportTab.STOCK, interval, interval.plusDays(1), rowLimit = 2)) }
            assertEquals(2, stock.rows.size)
            assertTrue(stock.rows.any { it.cells[0].text == "CURRENT" })
            assertTrue(stock.rows.any { it.cells[0].text == "sale" })

            val team = owner.read { reporting.snapshot(ReportFilter(ReportTab.TEAM, interval, interval.plusDays(1))) }
            val hours = team.series.single { it.key == "completedHours" }.points.single()
            assertEquals(0, BigDecimal.ONE.compareTo(hours))
            assertEquals(1L, team.series.single { it.key == "completed" }.points.single().longValueExact())
            assertTrue(owner.verifyIntegrity().let { it.quick && it.full && it.foreignKeys })
        } finally {
            owner.close()
        }
    }

    @Test fun dashboardIsOneLocalCalendarSnapshotWithMonthAndRollingTrend() = runBlocking {
        val owner = RoomDatabaseOwner(context, File(context.noBackupFilesDir, "i09-dashboard/${UUID.randomUUID()}"))
        try {
            CommandCoordinator(owner).execute {
                val dao = (this as RoomRepositories).dao
                dao.fixture()
                dao.setting(SettingEntity("currency", "EUR"))
                dao.invoice(InvoiceEntity("DEC-SALE", 1, null, "2025-12-31T23:30:00.000Z", BigDecimal("2.125"), BigDecimal("2.125"), BigDecimal.ZERO, "validated", currency = "XAF"))
                dao.invoiceLine(InvoiceLineEntity(2, "DEC-SALE", null, "Snapshot", "Legacy category", 1, BigDecimal("2.125"), BigDecimal("2.125")))
                dao.product(ProductEntity(2, "Zero", "T", BigDecimal.ONE, 0, 1, STAMP, STAMP))
                dao.product(ProductEntity(3, "Low", "T", BigDecimal.ONE, 2, 3, STAMP, STAMP))
            }
            val report = owner.read { reporting.dashboard(DashboardRequest(LocalDate.parse("2026-01-02"), "UTC", 1), true) }
            val values = report.rows.associate { it.cells[0].text to it.cells[1].text }
            assertEquals("25", values["todayValidatedRevenue"])
            assertEquals("1", values["todayValidatedTransactions"])
            assertEquals("25", values["todayAverageBasket"])
            assertEquals("25", values["currentMonthValidatedRevenue"])
            assertEquals("2", values["lowStockProducts"])
            assertEquals("1", values["outOfStockProducts"])
            val trend = report.series.single { it.key == "validatedRevenue" }
            assertEquals(30, trend.points.size)
            assertEquals("2025-12-04", trend.labels.first())
            assertEquals("2026-01-02", trend.labels.last())
            val yearBoundary = trend.labels.indexOf("2025-12-31")
            assertTrue(yearBoundary >= 0)
            assertEquals(0, BigDecimal("2.125").compareTo(trend.points[yearBoundary]))
            assertEquals(LocalDate.parse("2026-01-02"), report.dashboardContext?.asOfDate)
            assertEquals("UTC", report.dashboardContext?.timeZoneId)
            assertEquals("1", values["monthOpenAttendanceSessions"])
        } finally { owner.close() }
    }

    @Test fun purchaseStatusCountsCrossKeysetBatchesAndCalendarGrainsHaveZeroBuckets() = runBlocking {
        val owner = RoomDatabaseOwner(context, File(context.noBackupFilesDir, "i09-purchase-pages/${UUID.randomUUID()}"))
        try {
            CommandCoordinator(owner).execute {
                val dao = (this as RoomRepositories).dao
                dao.fixture()
                for (index in 2..206) {
                    val status = when (index % 3) { 0 -> "DRAFT"; 1 -> "VALIDATED"; else -> "CANCELLED" }
                    dao.purchase(PurchaseEntity(index.toLong(), "PAGE-PUR-$index", 1, status, BigDecimal("0.0000001"), 1, STAMP))
                }
            }
            val from = LocalDate.parse("2026-01-01")
            val to = LocalDate.parse("2026-01-04")
            val purchases = owner.read { reporting.snapshot(ReportFilter(ReportTab.PURCHASES, from, to, rowLimit = 1)) }
            assertEquals(206L, purchases.series.single { it.key == "purchaseCountTotal" }.points.single().longValueExact())
            val sum = listOf("Validated", "Draft", "Cancelled").sumOf { key ->
                purchases.series.single { it.key == "purchaseCount$key" }.points.single().longValueExact()
            }
            assertEquals(206L, sum)
            val daily = owner.read { reporting.snapshot(ReportFilter(ReportTab.SALES, from, to, ReportGrain.DAY)) }
            assertEquals(listOf("2026-01-01", "2026-01-02", "2026-01-03"), daily.series.single().labels)
            assertEquals(BigDecimal.ZERO, daily.series.single().points.first())
            val monthly = owner.read { reporting.snapshot(ReportFilter(ReportTab.SALES, from, LocalDate.parse("2026-02-01"), ReportGrain.MONTH)) }
            assertEquals(listOf("2026-01-01"), monthly.series.single().labels)
            val weekly = owner.read { reporting.snapshot(ReportFilter(ReportTab.SALES,
                LocalDate.parse("2025-12-29"), LocalDate.parse("2026-01-05"), ReportGrain.WEEK)) }
            assertEquals(listOf("2025-12-29"), weekly.series.single().labels)
        } finally { owner.close() }
    }

    @Test fun dashboardCalendarBoundsRespectDstAndLocalDate() = runBlocking {
        val owner = RoomDatabaseOwner(context, File(context.noBackupFilesDir, "i09-dst/${UUID.randomUUID()}"))
        try {
            CommandCoordinator(owner).execute {
                val dao = (this as RoomRepositories).dao
                dao.fixture()
                dao.setting(SettingEntity("currency", "EUR"))
                val rows = listOf(
                    Triple("DST-START", "2026-03-08T08:30:00.000Z", BigDecimal("0.1")),
                    Triple("DST-END", "2026-03-09T06:59:59.999Z", BigDecimal("0.2")),
                    Triple("DST-NEXT", "2026-03-09T07:00:00.000Z", BigDecimal("99.0")),
                )
                rows.forEachIndexed { index, (id, stamp, amount) ->
                    dao.invoice(InvoiceEntity(id, 1, null, stamp, amount, amount, BigDecimal.ZERO, "validated", currency = "EUR"))
                    dao.invoiceLine(InvoiceLineEntity((index + 2).toLong(), id, null, id, "DST", 1, amount, amount))
                }
            }
            val report = owner.read { reporting.dashboard(DashboardRequest(LocalDate.parse("2026-03-08"), "America/Los_Angeles"), false) }
            val values = report.rows.associate { it.cells[0].text to it.cells[1].text }
            assertEquals("0.3", values["todayValidatedRevenue"])
            assertEquals("2", values["todayValidatedTransactions"])
            assertEquals("America/Los_Angeles", report.dashboardContext?.timeZoneId)
        } finally { owner.close() }
    }

    @Test fun expiredLeaseIsCheckedBeforeProductRankingQuery() = runBlocking {
        val owner = RoomDatabaseOwner(context, File(context.noBackupFilesDir, "i09-lease/${UUID.randomUUID()}"))
        try {
            val date = LocalDate.parse("2026-01-02")
            owner.read {
                var checks = 0
                val repository = RoomReportingRepository((this as RoomRepositories).dao) { write ->
                    assertFalse(write)
                    checks += 1
                    if (checks == 4) throw IllegalStateException("injected expired report lease")
                }
                try {
                    repository.snapshot(ReportFilter(ReportTab.PRODUCTS, date, date.plusDays(1), rowLimit = 1))
                    fail("Expected lease check before product-ranking DAO query")
                } catch (failure: IllegalStateException) {
                    assertEquals("injected expired report lease", failure.message)
                }
                assertEquals(4, checks)
            }
        } finally {
            owner.close()
        }
    }

    @Test fun validatedSalesTotalsContinueAcrossDisplayPageBoundary() = runBlocking {
        val root = File(context.noBackupFilesDir, "i09-reporting-page/${UUID.randomUUID()}")
        val owner = RoomDatabaseOwner(context, root)
        try {
            CommandCoordinator(owner).execute {
                val dao = (this as RoomRepositories).dao
                dao.user(UserEntity(1, "synthetic", "NOT-A-REAL-HASH", "owner", "Test", "Only", "TO", STAMP))
                for (index in 1..201) {
                    val id = "page-sale-${index.toString().padStart(3, '0')}"
                    val amount = BigDecimal("0.001")
                    dao.invoice(InvoiceEntity(id, 1, null, STAMP, amount, amount, BigDecimal.ZERO,
                        "validated", currency = "EUR"))
                    dao.invoiceLine(InvoiceLineEntity(index.toLong(), id, null, "Synthetic", "Test", 1, amount, amount))
                }
            }
            val date = LocalDate.parse("2026-01-02")
            val report = owner.read { reporting.snapshot(ReportFilter(ReportTab.SALES, date, date.plusDays(1), rowLimit = 3)) }
            assertEquals(201L, report.metrics.transactionCount)
            assertEquals(0, BigDecimal("0.201").compareTo(report.metrics.revenue))
            assertEquals(3, report.rows.size)
        } finally {
            owner.close()
        }
    }

    @Test fun financeRejectsMissingOrMixedSalesCurrencyWithoutReturningPartialSnapshot() = runBlocking {
        suspend fun assertUnavailableAfterAddingInvoice(
            rootName: String,
            invoiceId: String,
            currency: String?,
        ) {
            val owner = RoomDatabaseOwner(context, File(context.noBackupFilesDir, "$rootName/${UUID.randomUUID()}"))
            try {
                CommandCoordinator(owner).execute {
                    val dao = (this as RoomRepositories).dao
                    dao.fixture()
                }
                // Existing fixture is a valid, single-currency XAF sale: it remains reportable.
                val valid = owner.read { reporting.snapshot(ReportFilter(
                    ReportTab.FINANCES, LocalDate.parse("2026-01-02"), LocalDate.parse("2026-01-03"),
                )) }
                assertEquals("XAF", valid.metrics.currency)
                assertEquals(0, BigDecimal("25.0").compareTo(valid.metrics.revenue))
                CommandCoordinator(owner).execute {
                    val dao = (this as RoomRepositories).dao
                    dao.invoice(InvoiceEntity(invoiceId, 1, null, STAMP, BigDecimal.ONE, BigDecimal.ONE,
                        BigDecimal.ZERO, "validated", currency = currency))
                    dao.invoiceLine(InvoiceLineEntity(2, invoiceId, null, "Synthetic", "Test", 1,
                        BigDecimal.ONE, BigDecimal.ONE))
                }

                var returnedSnapshot: DocumentModel? = null
                try {
                    returnedSnapshot = owner.read { reporting.snapshot(ReportFilter(
                        ReportTab.FINANCES, LocalDate.parse("2026-01-02"), LocalDate.parse("2026-01-03"),
                    )) }
                    fail("Expected FINANCES to fail closed for missing or mixed sales currency")
                } catch (failure: WorkflowFailure) {
                    assertEquals(WorkflowError.UNAVAILABLE, failure.code)
                }
                assertNull("A failed FINANCES projection must not expose a partial document", returnedSnapshot)
            } finally {
                owner.close()
            }
        }

        assertUnavailableAfterAddingInvoice("i09-currency-missing", "SYN-MISSING-CURRENCY", null)
        assertUnavailableAfterAddingInvoice("i09-currency-mixed", "SYN-MIXED-CURRENCY", "EUR")
    }
}
