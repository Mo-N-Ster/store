package com.vibe.store.testing

import com.vibe.store.api.*
import com.vibe.store.application.persistence.*
import com.vibe.store.application.reporting.*
import com.vibe.store.application.security.*
import com.vibe.store.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ReportingAuthorityTest {
    private class Memory : DatabaseOwner, UnitOfWork, TransactionRepositories, SecurityRepository {
        override var generation = GenerationId("main")
        val users = mutableMapOf<Long, AuthAccount>()
        val denials = mutableMapOf<Long, Set<String>>()
        override val security get() = this
        override val settings: SettingsWriter get() = object : SettingsWriter { override suspend fun value(key: String) = null; override suspend fun put(key: String, value: String) = Unit }
        override val identity: IdentityRepository get() = error("unused")
        override val cash: CashRepository get() = error("unused")
        override val catalog: CatalogRepository get() = error("unused")
        override val sales: SalesRepository get() = error("unused")
        override val purchases: PurchaseRepository get() = error("unused")
        override val inventories: InventoryRepository get() = error("unused")
        override val employees: EmployeeRepository get() = error("unused")
        override val attendance: AttendanceRepository get() = error("unused")
        override val messaging: MessagingRepository get() = error("unused")
        override val audit: AuditRepository get() = error("unused")
        override suspend fun <T> read(block: suspend ReadRepositories.() -> T): T = block(this)
        override suspend fun <T> transaction(block: suspend TransactionRepositories.() -> T): T = block(this)
        override suspend fun verifyIntegrity() = IntegrityResult(true, true, true)
        override suspend fun checkpointCloseReopen() = Unit
        override suspend fun close() = Unit
        override suspend fun accounts() = users.values.toList()
        override suspend fun account(id: Long) = users[id]
        override suspend fun inherited(id: Long) = RolePolicy.inherited(users.getValue(id).role)
        override suspend fun denied(id: Long) = denials[id] ?: emptySet()
        override suspend fun createOwner(input: NewOwner): Long = error("unused")
        override suspend fun createManaged(input: NewManagedAccount): Long = error("unused")
        override suspend fun updateManaged(input: ManagedAccountChange) = error("unused")
        override suspend fun replaceDenials(id: Long, codes: Set<String>) { denials[id] = codes }
        override suspend fun loginResult(id: Long, failures: Int, until: Long?, successAt: String?) = Unit
        override suspend fun recoveryResult(id: Long, failures: Int, until: Long?, verifier: String?) = Unit
        override suspend fun hasOpenCash(id: Long) = false
        override suspend fun appendAudit(event: SecurityAudit) = Unit
    }

    private class FakeReporting : ReportingRepository {
        var reportCalls = 0
        var dashboardCalls = 0
        var includePresence: Boolean? = null
        var dashboardRequest: DashboardRequest? = null
        override suspend fun snapshot(filter: ReportFilter): DocumentModel { reportCalls++; return document(filter) }
        override suspend fun dashboard(filter: ReportFilter, includePresence: Boolean): DocumentModel {
            dashboardCalls++; this.includePresence = includePresence; return document(filter)
        }
        override suspend fun dashboard(request: DashboardRequest, includePresence: Boolean): DocumentModel {
            dashboardCalls++; this.includePresence = includePresence; dashboardRequest = request
            return document(ReportFilter(ReportTab.SUMMARY, request.asOfDate, request.asOfDate.plusDays(1),
                timeZoneId = request.timeZoneId, rowLimit = request.rowLimit))
        }
        private fun document(filter: ReportFilter) = DocumentModel(
            "test", filter.tab, filter, "EUR", listOf(ReportColumn("value", "Value")), emptyList(), emptyList(),
            ReportMetricSnapshot(BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, MarginDisclosure.ESTIMATED_SNAPSHOT_COST),
        )
    }

    private val from = LocalDate.parse("2026-09-01")
    private val to = LocalDate.parse("2026-10-01")
    private fun filter(tab: ReportTab, status: String? = null) = ReportFilter(tab, from, to, status = status)
    private fun dashboardFilter(from: LocalDate = this.from, grain: ReportGrain = ReportGrain.DAY) =
        ReportFilter(ReportTab.SUMMARY, from, from.plusDays(1), grain = grain, timeZoneId = "Europe/Rome", rowLimit = 7)

    private fun fixture(): Triple<Memory, FakeReporting, IdentityAuthority> {
        val db = Memory()
        fun account(id: Long, user: String, role: String) = AuthAccount(id, user, "$user@example.invalid", "Test", "Only", role, true, null,
            "TEST:Password-123", null, null, 0, null, 0, null)
        db.users[1] = account(1, "owner", "owner")
        db.users[2] = account(2, "manager", "manager")
        db.users[3] = account(3, "employee", "employee")
        val hasher = object : PasswordHasher {
            override fun hash(value: String) = "TEST:$value"
            override fun verifies(value: String, verifier: String) = hash(value) == verifier
        }
        val identity = IdentityAuthority(db, CommandCoordinator(db), hasher, clock = { 1_000_000L })
        val reporting = FakeReporting()
        return Triple(db, reporting, identity)
    }

    private suspend fun forbidden(block: suspend () -> Unit) {
        try { block(); fail("Expected forbidden") }
        catch (e: SecurityFailure) { assertEquals(SecurityError.FORBIDDEN, e.code) }
    }

    @Test fun dashboardUsesCurrentIdentityAndDoesNotRequireFinancePermission() = runBlocking {
        val (db, repo, identity) = fixture()
        identity.login(Credentials("employee", "Password-123"))
        val result = ReportingAuthority(identity, repo).dashboard(dashboardFilter())
        assertEquals(ReportTab.SUMMARY, result.tab)
        assertEquals(1, repo.dashboardCalls)
        assertEquals(from, repo.dashboardRequest?.asOfDate)
        assertEquals("Europe/Rome", repo.dashboardRequest?.timeZoneId)
        assertEquals(7, repo.dashboardRequest?.rowLimit)
        assertEquals(true, repo.includePresence)
        assertEquals(0, repo.reportCalls)
        assertTrue(db.denials.isEmpty())
    }

    @Test fun calendarDashboardUsesExplicitZoneAndEffectivePresenceRight() = runBlocking {
        val (db, repo, identity) = fixture()
        identity.login(Credentials("manager", "Password-123"))
        db.denials[2] = setOf("PRESENCE:READ")
        val request = DashboardRequest(LocalDate.parse("2026-03-08"), "America/Los_Angeles")
        val result = ReportingAuthority(identity, repo).dashboard(request)
        assertEquals(ReportTab.SUMMARY, result.tab)
        assertEquals(request, repo.dashboardRequest)
        assertEquals(false, repo.includePresence)
        assertEquals(1, repo.dashboardCalls)
        assertEquals(0, repo.reportCalls)
    }

    @Test fun reportDenialHappensBeforeRepositoryAndDoesNotDiscloseRows() = runBlocking {
        val (_, repo, identity) = fixture()
        identity.login(Credentials("employee", "Password-123"))
        forbidden { ReportingAuthority(identity, repo).snapshot(filter(ReportTab.SALES)) }
        assertEquals(0, repo.reportCalls)
        assertEquals(0, repo.dashboardCalls)
    }

    @Test fun individuallyDeniedFinanceReadIsRejectedBeforeRepository() = runBlocking {
        val (db, repo, identity) = fixture()
        identity.login(Credentials("manager", "Password-123"))
        db.denials[2] = setOf("FINANCES:READ")
        forbidden { ReportingAuthority(identity, repo).snapshot(filter(ReportTab.FINANCES)) }
        assertEquals(0, repo.reportCalls)
    }

    @Test fun purchaseAndTeamSectionsRequireCurrentEffectiveAdditionalRights() = runBlocking {
        val (db, repo, identity) = fixture()
        identity.login(Credentials("manager", "Password-123"))
        db.denials[2] = setOf("PURCHASES:READ", "PRESENCE:READ")
        val service = ReportingAuthority(identity, repo)
        forbidden { service.snapshot(filter(ReportTab.PURCHASES)) }
        forbidden { service.snapshot(filter(ReportTab.TEAM)) }
        assertEquals(0, repo.reportCalls)
        val dashboard = service.dashboard(dashboardFilter())
        assertEquals(1, repo.dashboardCalls)
        assertEquals(false, repo.includePresence)
        assertEquals(ReportTab.SUMMARY, dashboard.tab)
    }

    @Test fun legacyDashboardRejectsMultiDayAndNonDayGrainBeforeProjection() = runBlocking {
        val (_, repo, identity) = fixture()
        identity.login(Credentials("owner", "Password-123"))
        val service = ReportingAuthority(identity, repo)
        val incompatible = listOf(
            ReportFilter(ReportTab.SUMMARY, from, to, grain = ReportGrain.DAY),
            dashboardFilter(grain = ReportGrain.WEEK),
        )
        for (filter in incompatible) {
            try {
                service.dashboard(filter)
                fail("Expected incompatible legacy dashboard filter rejection")
            } catch (e: WorkflowFailure) { assertEquals(WorkflowError.INVALID_INPUT, e.code) }
        }
        assertEquals(0, repo.dashboardCalls)
        assertEquals(0, repo.reportCalls)
    }

    @Test fun deniedDashboardDoesNotReachAnyProjection() = runBlocking {
        val (db, repo, identity) = fixture()
        identity.login(Credentials("employee", "Password-123"))
        db.denials[3] = setOf("DASHBOARD:READ")
        forbidden { ReportingAuthority(identity, repo).dashboard(DashboardRequest(from, "UTC")) }
        assertEquals(0, repo.dashboardCalls)
        assertEquals(0, repo.reportCalls)
    }

    @Test fun invalidTabStatusIsRejectedWithoutProjectionRead() = runBlocking {
        val (_, repo, identity) = fixture()
        identity.login(Credentials("owner", "Password-123"))
        try {
            ReportingAuthority(identity, repo).snapshot(filter(ReportTab.SALES, "DRAFT"))
            fail("Expected invalid filter")
        } catch (e: WorkflowFailure) { assertEquals(WorkflowError.INVALID_INPUT, e.code) }
        assertEquals(0, repo.reportCalls)
    }

    @Test fun incompatibleDimensionsAndUnsupportedGrainsAreRejected() = runBlocking {
        val (_, repo, identity) = fixture()
        identity.login(Credentials("owner", "Password-123"))
        val service = ReportingAuthority(identity, repo)
        val invalid = listOf(
            ReportFilter(ReportTab.SUMMARY, from, to, productId = 1),
            ReportFilter(ReportTab.SALES, from, to, employeeId = 1),
            ReportFilter(ReportTab.STOCK, from, to, supplierId = 1),
            ReportFilter(ReportTab.TEAM, from, to, grain = ReportGrain.WEEK),
            ReportFilter(ReportTab.PURCHASES, from, to, grain = ReportGrain.MONTH),
        )
        for (filter in invalid) {
            try {
                service.snapshot(filter)
                fail("Expected incompatible filter rejection: ${filter.tab}/${filter.grain}")
            } catch (e: WorkflowFailure) {
                assertEquals(WorkflowError.INVALID_INPUT, e.code)
            }
        }
        assertEquals(0, repo.reportCalls)
    }
}
