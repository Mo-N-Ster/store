package com.vibe.store.testing

import com.vibe.store.api.*
import com.vibe.store.application.persistence.*
import com.vibe.store.application.security.*
import com.vibe.store.application.team.*
import com.vibe.store.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** I05 use-case tests: no Android, filesystem, database or external network. */
class TeamAuthorityTest {
    private data class Person(
        val id: Long, val username: String, val role: String,
        val first: String, val last: String, val active: Boolean = true,
        val verifier: String = "TEST:Password-123", val photo: String? = null,
        val failures: Int = 0, val lockedUntil: Long? = null,
    )

    private class Memory : DatabaseOwner, UnitOfWork, TransactionRepositories,
        SecurityRepository, SettingsWriter {
        override var generation = GenerationId("i05")
        val people = mutableMapOf<Long, Person>()
        val profiles = mutableMapOf<Long, EmployeeProfileRecord>()
        val records = mutableMapOf<Long, PresenceRecord>()
        val events = mutableListOf<SecurityAudit>()
        val cashOpen = mutableSetOf<Long>()
        val deniedRights = mutableMapOf<Long, Set<String>>()
        val configuration = mutableMapOf<String, String>()
        var lastLoginAt: String? = null

        override val security: SecurityRepository get() = this
        override val employees: EmployeeRepository get() = employeePort
        override val attendance: AttendanceRepository get() = attendancePort
        override val settings: SettingsWriter get() = this
        override val identity: IdentityRepository get() = error("unused")
        override val cash: CashRepository get() = error("unused")
        override val catalog: CatalogRepository get() = error("unused")
        override val sales: SalesRepository get() = error("unused")
        override val purchases: PurchaseRepository get() = error("unused")
        override val inventories: InventoryRepository get() = error("unused")
        override val messaging: MessagingRepository get() = error("unused")
        override val audit: AuditRepository get() = error("unused")

        override suspend fun <T> read(block: suspend ReadRepositories.() -> T): T = block(this)
        override suspend fun <T> transaction(block: suspend TransactionRepositories.() -> T): T {
            val beforePeople = people.toMap()
            val beforeProfiles = profiles.toMap()
            val beforeRecords = records.toMap()
            val beforeEvents = events.toList()
            val beforeDenials = deniedRights.toMap()
            val beforeConfig = configuration.toMap()
            val beforeLogin = lastLoginAt
            return try { block(this) } catch (failure: Throwable) {
                people.clear(); people.putAll(beforePeople)
                profiles.clear(); profiles.putAll(beforeProfiles)
                records.clear(); records.putAll(beforeRecords)
                events.clear(); events.addAll(beforeEvents)
                deniedRights.clear(); deniedRights.putAll(beforeDenials)
                configuration.clear(); configuration.putAll(beforeConfig)
                lastLoginAt = beforeLogin
                throw failure
            }
        }

        private fun Person.auth() = AuthAccount(
            id, username, "$username@example.invalid", first, last, role, active,
            profiles[id]?.status, verifier, null, null, failures, lockedUntil, 0, null,
        )
        override suspend fun accounts() = people.values.map { it.auth() }
        override suspend fun account(id: Long) = people[id]?.auth()
        override suspend fun createOwner(input: NewOwner): Long {
            val id = (people.keys.maxOrNull() ?: 0L) + 1
            people[id] = Person(id, input.username, "owner", input.firstName, input.lastName,
                verifier = input.verifier)
            return id
        }
        override suspend fun createManaged(input: NewManagedAccount): Long {
            val id = (people.keys.maxOrNull() ?: 0L) + 1
            people[id] = Person(id, input.username, input.role, input.firstName, input.lastName,
                input.active, input.verifier, input.photo)
            return id
        }
        override suspend fun updateManaged(input: ManagedAccountChange) {
            val current = people.getValue(input.accountId)
            people[input.accountId] = current.copy(
                username = input.username, first = input.firstName, last = input.lastName,
                role = input.role, active = input.active, photo = input.photo,
            )
        }
        override suspend fun inherited(id: Long) = RolePolicy.inherited(people.getValue(id).role)
        override suspend fun denied(id: Long) = deniedRights[id] ?: emptySet()
        override suspend fun replaceDenials(id: Long, codes: Set<String>) { deniedRights[id] = codes }
        override suspend fun loginResult(id: Long, failures: Int, until: Long?, successAt: String?) {
            people[id] = people.getValue(id).copy(failures = failures, lockedUntil = until)
            if (successAt != null) lastLoginAt = successAt
        }
        override suspend fun recoveryResult(id: Long, failures: Int, until: Long?, verifier: String?) {
            if (verifier != null) people[id] = people.getValue(id).copy(
                verifier = verifier, failures = 0, lockedUntil = null,
            )
        }
        override suspend fun hasOpenCash(id: Long) = id in cashOpen
        override suspend fun appendAudit(event: SecurityAudit) { events += event }

        private val employeePort = object : EmployeeRepository {
        private fun project(profile: EmployeeProfileRecord): EmployeeRecord {
            val user = people.getValue(profile.accountId)
            return EmployeeRecord(
                accountId = user.id, employeeId = profile.id,
                username = user.username, firstName = user.first, lastName = user.last,
                initials = "${user.first.first()}${user.last.first()}",
                email = "$user.username@example.invalid", phone = profile.phone,
                hireDate = profile.hireDate, role = user.role, active = user.active,
                photo = user.photo, employeeCode = profile.code,
                employment = profile.status, address = profile.address,
                createdAt = profile.createdAt, updatedAt = profile.updatedAt,
                endDate = profile.endDate,
            )
        }
        override suspend fun find(accountId: Long) = profiles[accountId]?.let(::project)
        override suspend fun page(filter: EmployeeFilter): List<EmployeeRecord> {
            val search = IdentityPolicy.fold(filter.search)
            return profiles.values.asSequence().map(::project)
                .filter { filter.role.isEmpty() || it.role == filter.role }
                .filter { filter.employment.isEmpty() || it.employment == filter.employment }
                .filter { filter.active == null || it.active == filter.active }
                .filter {
                    search.isEmpty() || listOf(it.username, it.firstName, it.lastName,
                        it.employeeCode, "${it.firstName} ${it.lastName}")
                        .any { term -> IdentityPolicy.fold(term).contains(search) }
                }
                .sortedBy { it.employeeId }
                .drop(filter.offset).take(filter.limit).toList()
        }
        override suspend fun nextEmployeeId() = (profiles.values.maxOfOrNull { it.id } ?: 0L) + 1
        override suspend fun duplicateCode(code: String, exceptEmployeeId: Long?) =
            profiles.values.any { it.id != exceptEmployeeId && it.code.equals(code, true) }
        override suspend fun insert(profile: EmployeeProfileRecord) { profiles[profile.accountId] = profile }
        override suspend fun update(profile: EmployeeProfileRecord) { profiles[profile.accountId] = profile }
        }

        private val attendancePort = object : AttendanceRepository {
        override suspend fun attendance(id: Long) = records[id]?.let {
            AttendanceRecord(it.id, it.signerId, it.startTime, it.endTime, it.status)
        }
        override suspend fun find(id: Long) = records[id]
        override suspend fun page(query: PresenceQuery): List<PresenceRecord> = records.values.asSequence()
            .filter { query.startInclusive.isEmpty() || it.startTime >= query.startInclusive }
            .filter { query.endExclusive.isEmpty() || it.startTime < query.endExclusive }
            .filter { query.signerId == null || it.signerId == query.signerId }
            .filter { query.role.isEmpty() || people.getValue(it.signerId).role == query.role }
            .filter { query.status.isEmpty() || it.status == query.status }
            .sortedWith(compareByDescending<PresenceRecord> { it.startTime }.thenByDescending { it.id })
            .drop(query.offset).take(query.limit).toList()
        override suspend fun openFor(signerId: Long) = records.values
            .filter { it.signerId == signerId && it.endTime == null }
            .maxWithOrNull(compareBy<PresenceRecord> { it.startTime }.thenBy { it.id })
        override suspend fun nextAttendanceId() = (records.keys.maxOrNull() ?: 0L) + 1
        override suspend fun insert(record: PresenceRecord) { records[record.id] = record }
        override suspend fun update(record: PresenceRecord) { records[record.id] = record }
        }

        override suspend fun value(key: String) = configuration[key]
        override suspend fun put(key: String, value: String) { configuration[key] = value }
        override suspend fun verifyIntegrity() = IntegrityResult(true, true, true)
        override suspend fun checkpointCloseReopen() = Unit
        override suspend fun close() = Unit
    }

    private class Fixture {
        val db = Memory()
        var now = 1_760_000_000_000L
        private val hasher = object : PasswordHasher {
            override fun hash(value: String) = "TEST:$value"
            override fun verifies(value: String, verifier: String) = hash(value) == verifier
        }
        val identity = IdentityAuthority(db, CommandCoordinator(db), hasher, clock = { now })
        private val media = object : ProfilePhotoMedia {
            override suspend fun encode(selection: SelectedImage) = "data:image/png;base64,AA=="
            override suspend fun decode(dataUrl: String) = ProductImage("image/png", byteArrayOf(0))
        }
        val team = TeamAuthority(identity, media, clock = { now })
        fun seed() {
            db.people[1] = Person(1, "owner", "owner", "Olivia", "Owner")
            db.people[2] = Person(2, "employee", "employee", "Emma", "Employee")
            db.people[3] = Person(3, "manager", "manager", "Mario", "Manager")
            listOf(2L, 3L).forEach { id ->
                val person = db.people.getValue(id)
                db.profiles[id] = EmployeeProfileRecord(
                    id = id, accountId = id, code = "EMP-$id",
                    firstName = person.first, lastName = person.last,
                    status = "ACTIVE", createdAt = stamp(now), updatedAt = stamp(now),
                    phone = null, email = null, address = null, hireDate = null, endDate = null,
                )
            }
        }
        suspend fun login(who: String = "owner") =
            identity.login(Credentials(who, "Password-123"))
        fun update(id: Long = 2L, role: String = "employee", active: Boolean = true,
            employment: String = "ACTIVE", endDate: String? = null) = EmployeeUpdate(
            accountId = id, username = if (id == 2L) "employee" else "manager",
            firstName = if (id == 2L) "Emma" else "Mario",
            lastName = if (id == 2L) "Employee" else "Manager",
            email = null, phone = null, hireDate = null, active = active,
            role = role, employeeCode = "EMP-$id", employment = employment,
            address = null, endDate = endDate,
        )
    }

    companion object {
        private fun stamp(epoch: Long): String = SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT,
        ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(epoch))
    }
    private suspend fun rejected(code: TeamError, action: suspend () -> Unit) {
        try {
            action()
            fail("Expected TeamFailure($code)")
        } catch (failure: TeamFailure) {
            assertEquals(code, failure.code)
        }
    }

    @Test fun ownerWithoutHrProfileIsOnDailySheetAndCanSign() = runBlocking {
        val f = Fixture(); f.seed(); f.login()
        val today = f.team.today()
        val owner = today.rows.single { it.accountId == 1L }
        assertNull(owner.employeeId)
        assertNull(owner.employment)
        assertEquals(PresenceAction.ENTER, owner.nextAction)
        assertEquals(listOf(1L), f.team.today(DailyAttendanceFilter(role = "owner")).rows.map { it.accountId })
        val entry = f.team.sign(PresenceProof(1, "Password-123", PresenceAction.ENTER))
        assertEquals("EXPLICIT", entry.source)
        assertNull(entry.endTime)
        assertEquals(PresenceAction.EXIT, f.team.today(DailyAttendanceFilter(role = "owner")).rows.single().nextAction)
        f.now += 3_600_000L
        val exit = f.team.sign(PresenceProof(1, "Password-123", PresenceAction.EXIT))
        assertEquals(entry.id, exit.id)
        assertNotNull(exit.endTime)
        assertEquals(3_600_000L, f.team.today(DailyAttendanceFilter(role = "owner")).rows.single().completedMillis)
        assertEquals(1, f.db.records.size)
    }

    @Test fun signRequiresPersonalPasswordAndPersistsLockoutWithoutSwitchingSession() = runBlocking {
        val f = Fixture(); f.seed(); f.login()
        val loginBefore = f.db.lastLoginAt
        repeat(5) {
            rejected(TeamError.INVALID_CREDENTIALS) {
                f.team.sign(PresenceProof(2, "wrong", PresenceAction.ENTER))
            }
        }
        assertEquals(5, f.db.people.getValue(2).failures)
        assertEquals(f.now + 900_000L, f.db.people.getValue(2).lockedUntil)
        assertEquals(5, f.db.events.count { it.action == "attendance_proof_failed" })
        rejected(TeamError.INVALID_CREDENTIALS) {
            f.team.sign(PresenceProof(2, "Password-123", PresenceAction.ENTER))
        }
        assertTrue(f.db.records.isEmpty())
        assertEquals(1L, f.identity.current()?.id)
        assertEquals(loginBefore, f.db.lastLoginAt)
        f.now += 900_001L
        val entered = f.team.sign(PresenceProof(2, "Password-123", PresenceAction.ENTER))
        assertEquals(2L, entered.signerId)
        assertEquals(0, f.db.people.getValue(2).failures)
        assertEquals(loginBefore, f.db.lastLoginAt)
    }

    @Test fun rejectsDoubleEntryExitWithoutEntryAndUnprofiledNonOwner() = runBlocking {
        val f = Fixture(); f.seed(); f.login()
        rejected(TeamError.INVALID_TRANSITION) {
            f.team.sign(PresenceProof(2, "Password-123", PresenceAction.EXIT))
        }
        f.team.sign(PresenceProof(2, "Password-123", PresenceAction.ENTER))
        rejected(TeamError.INVALID_TRANSITION) {
            f.team.sign(PresenceProof(2, "Password-123", PresenceAction.ENTER))
        }
        f.db.profiles.remove(3L)
        rejected(TeamError.FORBIDDEN) {
            f.team.sign(PresenceProof(3, "Password-123", PresenceAction.ENTER))
        }
        assertEquals(1, f.db.records.size)
        assertEquals(1L, f.identity.current()?.id)
    }

    @Test fun openCashBlocksSensitiveChangesButNotOrdinaryProfileOrPhoto() = runBlocking {
        val f = Fixture(); f.seed(); f.login()
        f.db.cashOpen += 2L
        rejected(TeamError.CASH_OPEN) { f.team.update(f.update(role = "manager")) }
        rejected(TeamError.CASH_OPEN) { f.team.update(f.update(active = false)) }
        rejected(TeamError.CASH_OPEN) { f.team.update(f.update(employment = "SUSPENDED")) }
        rejected(TeamError.CASH_OPEN) { f.team.update(f.update(endDate = "2026-10-01")) }
        assertEquals("employee", f.db.people.getValue(2).role)
        assertEquals("ACTIVE", f.db.profiles.getValue(2).status)
        assertNull(f.db.profiles.getValue(2).endDate)
        val ordinary = f.team.update(f.update())
        assertEquals("employee", ordinary.role)
        val photo = f.team.photo(2, ProfilePhotoChange(replacement = object : SelectedImage {}))
        assertTrue(photo.hasPhoto)
        assertEquals("image/png", f.team.photo(2)?.mimeType)
    }

    @Test fun correctionsPreserveOriginalsAndEmployeeHistoryIsServerScoped() = runBlocking {
        val f = Fixture(); f.seed(); f.login()
        val first = f.team.sign(PresenceProof(2, "Password-123", PresenceAction.ENTER))
        f.now += 3_600_000L
        val exit = f.team.sign(PresenceProof(2, "Password-123", PresenceAction.EXIT))
        val corrected = f.team.correct(AttendanceCorrection(
            first.id, stamp(f.now - 1_800_000L), stamp(f.now), "Forgot entry",
        ))
        assertEquals("CORRECTED", corrected.status)
        assertEquals(exit.startTime, corrected.originalStartTime)
        assertEquals(exit.endTime, corrected.originalEndTime)
        f.now += 3_600_000L
        val twice = f.team.correct(AttendanceCorrection(
            first.id, stamp(f.now - 1_800_000L), stamp(f.now), "Second correction",
        ))
        assertEquals(exit.startTime, twice.originalStartTime)
        assertEquals(exit.endTime, twice.originalEndTime)
        assertEquals(2, f.db.events.count { it.action == "attendance_corrected" })
        f.identity.switchUser(Credentials("employee", "Password-123"))
        assertEquals(listOf(2L), f.team.attendance().items.map { it.signerId })
        rejected(TeamError.FORBIDDEN) { f.team.attendance(AttendanceFilter(signerId = 3L)) }
        try {
            f.team.correct(AttendanceCorrection(first.id, stamp(f.now - 1_000L), stamp(f.now), "Forbidden"))
            fail("Employee must not correct attendance")
        } catch (_: SecurityFailure) {
            // PRESENCE:UPDATE is absent from the employee's effective permissions.
        }
    }

    @Test fun loginLogoutDoNotCreateAttendanceAndDatesAreCanonical() = runBlocking {
        val f = Fixture(); f.seed(); f.login(); f.identity.logout()
        assertTrue(f.db.records.isEmpty())
        f.login()
        assertTrue(f.team.attendance().items.isEmpty())
        assertEquals(1, f.team.today(DailyAttendanceFilter(limit = 1)).rows.size)
        assertTrue(f.team.today(DailyAttendanceFilter(limit = 1)).hasMore)
        rejected(TeamError.INVALID_INPUT) {
            f.team.today(DailyAttendanceFilter(role = "invalid"))
        }
        assertEquals(f.now, TeamPolicy.timestamp(stamp(f.now)))
        assertTrue(runCatching { TeamPolicy.timestamp("2026-1-01T00:00:00.000Z") }.isFailure)
        assertTrue(runCatching { TeamPolicy.timestamp("2026-02-30T00:00:00.000Z") }.isFailure)
    }

    private fun creation(user: String, code: String) = EmployeeCreation(
        username = user, firstName = "First$user", lastName = "Last$user",
        email = null, phone = null, hireDate = null, role = "employee",
        employeeCode = code, employment = "ACTIVE", address = null,
        manualPassword = "Password-123", generateTemporaryPassword = false,
    )

    @Test fun blankEmployeeCodeIsAllocatedAndCustomCodeStillWorks() = runBlocking {
        val f = Fixture(); f.seed(); f.login()
        val first = f.team.create(creation("generated1", " ")).employee
        assertEquals("EMP-0004", first.employeeCode)
        val second = f.team.create(creation("generated2", "")).employee
        assertEquals("EMP-0005", second.employeeCode)
        val manual = f.team.create(creation("custom3", " STAFF-9 ")).employee
        assertEquals("STAFF-9", manual.employeeCode)
        assertEquals("EMP-0004", f.team.employee(first.accountId).employeeCode)
        assertEquals(5, f.db.profiles.size)
        val beforeAccounts = f.db.people.size
        val beforeProfiles = f.db.profiles.size
        val beforeAudit = f.db.events.size
        rejected(TeamError.CONFLICT) {
            f.team.create(creation("duplicate4", "staff-9"))
        }
        assertEquals(beforeAccounts, f.db.people.size)
        assertEquals(beforeProfiles, f.db.profiles.size)
        assertEquals(beforeAudit, f.db.events.size)
    }

    @Test fun generatedCodeSkipsHistoricalManualCollisionAndRejectedCreateReservesNothing() = runBlocking {
        val f = Fixture(); f.seed(); f.login()
        // The next numeric profile id is 4; the code is already occupied by
        // a historical manual reference, despite that profile having id 2.
        f.db.profiles[2L] = f.db.profiles.getValue(2L).copy(code = "EMP-0004")
        rejected(TeamError.CONFLICT) {
            f.team.create(creation("duplicate1", "EMP-0004"))
        }
        assertEquals(3, f.db.people.size)
        assertEquals(2, f.db.profiles.size)
        val generated = f.team.create(creation("fresh", "")).employee
        assertEquals("EMP-0005", generated.employeeCode)
        assertEquals("EMP-0004", f.db.profiles.getValue(2L).code)
    }
}
