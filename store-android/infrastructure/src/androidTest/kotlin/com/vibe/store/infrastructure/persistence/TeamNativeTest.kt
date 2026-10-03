package com.vibe.store.infrastructure.persistence

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.vibe.store.api.*
import com.vibe.store.application.persistence.CommandCoordinator
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.application.team.PresenceRecord
import com.vibe.store.application.team.TeamAuthority
import com.vibe.store.infrastructure.media.AndroidProfilePhotoMedia
import com.vibe.store.infrastructure.security.BcryptPasswords
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** Native I05 tests: real Room/SQLite, Android profile media, and isolated synthetic databases. */
class TeamNativeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 1, 2, 3)
    private val photo get() = AndroidProfilePhotoMedia.selection(Uri.parse("content://synthetic/i05-profile"))

    private inner class Fixture {
        val root = File(context.noBackupFilesDir, "i05-native/${UUID.randomUUID()}")
        val owner = RoomDatabaseOwner(context, File(root, "database"))
        val commands = CommandCoordinator(owner)
        val identity = IdentityAuthority(owner, commands, BcryptPasswords())
        val media = AndroidProfilePhotoMedia.fixture { png.inputStream() }
        var now = 1_760_000_000_000L
        val team = TeamAuthority(identity, media, clock = { now })

        suspend fun initialize() {
            identity.bootstrap(OwnerRegistration(
                "owner", "owner@example.invalid", "Synthetic", "Owner",
                "Password-123", "Synthetic question", "Synthetic answer",
            ))
            identity.login(Credentials("owner", "Password-123"))
        }

        suspend fun staff(
            username: String = "employee",
            code: String = "EMP-002",
            role: String = "employee",
            manual: String? = "Staff-Password-123",
            temporary: Boolean = false,
            withPhoto: Boolean = false,
        ) = team.create(EmployeeCreation(
            username = username,
            firstName = if (role == "manager") "Synthetic Manager" else "Synthetic Employee",
            lastName = if (role == "manager") "Manager" else "Employee",
            email = "$username@example.invalid",
            phone = null,
            hireDate = null,
            role = role,
            employeeCode = code,
            employment = "ACTIVE",
            address = null,
            manualPassword = manual,
            generateTemporaryPassword = temporary,
            photo = if (withPhoto) photo else null,
        ))
    }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try {
            f.initialize()
            block(f)
            assertTrue(f.owner.verifyIntegrity().let { it.quick && it.full && it.foreignKeys })
        } finally {
            f.owner.close()
        }
    }

    private suspend fun rejected(code: TeamError, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected TeamFailure($code)")
        } catch (failure: TeamFailure) {
            assertEquals(code, failure.code)
        }
    }

    private fun stamp(epoch: Long): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT,
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(epoch))

    @Test fun accountProfilePasswordAndPhotoSurviveRealRoomReopen() = runBlocking { fixture { f ->
        val created = f.staff(withPhoto = true).employee
        assertTrue(created.hasPhoto)
        assertEquals("employee", created.role)
        assertEquals("EMP-002", created.employeeCode)
        assertArrayEquals(png, f.team.photo(created.accountId)!!.bytes)
        assertNull(f.team.employee(created.accountId).endDate)

        val temporary = f.staff("manager", "MGR-003", "manager", manual = null, temporary = true)
        val secret = requireNotNull(temporary.temporaryPassword)
        assertFalse(secret.toString().contains(secret.value))
        assertTrue(secret.value.length >= 12)
        rejected(TeamError.CONFLICT) { f.staff("duplicate", "EMP-002") }

        f.owner.checkpointCloseReopen()
        assertEquals(setOf(created.accountId, temporary.employee.accountId),
            f.team.employees().map { it.accountId }.toSet())
        assertEquals("ACTIVE", f.team.employee(created.accountId).employment)
        assertArrayEquals(png, f.team.photo(created.accountId)!!.bytes)
        f.owner.read {
            val dao = (this as RoomRepositories).dao
            assertEquals(3, dao.accounts().size)
            assertEquals(2, dao.employees().size)
            assertTrue(dao.account(created.accountId)!!.passwordHash.startsWith("\$2"))
            assertNotNull(dao.employeeForUser(created.accountId))
        }
    } }

    @Test fun personalProofPersistsWithoutSwitchingFacilitatorOrImplicitPunch() = runBlocking { fixture { f ->
        val staff = f.staff().employee
        assertEquals(1L, f.identity.current()!!.id)
        assertTrue(f.team.attendance().items.isEmpty())
        rejected(TeamError.INVALID_CREDENTIALS) {
            f.team.sign(PresenceProof(staff.accountId, "wrong-secret", PresenceAction.ENTER))
        }
        f.owner.read {
            val account = (this as RoomRepositories).dao.account(staff.accountId)!!
            assertEquals(1, account.failedLoginAttempts)
            assertNull(account.lastLoginAt)
            assertNull(attendance.attendance(1))
        }
        val started = f.team.sign(PresenceProof(
            staff.accountId, "Staff-Password-123", PresenceAction.ENTER,
        ))
        assertEquals(staff.accountId, started.signerId)
        assertEquals("EXPLICIT", started.source)
        assertNull(started.endTime)
        assertEquals(1L, f.identity.current()!!.id)
        rejected(TeamError.INVALID_TRANSITION) {
            f.team.sign(PresenceProof(staff.accountId, "Staff-Password-123", PresenceAction.ENTER))
        }
        f.owner.read {
            val account = (this as RoomRepositories).dao.account(staff.accountId)!!
            assertEquals(0, account.failedLoginAttempts)
            assertNull(account.lastLoginAt) // Attendance proof is never login.
        }
        f.now += 3_600_000L
        val ended = f.team.sign(PresenceProof(staff.accountId, "Staff-Password-123", PresenceAction.EXIT))
        assertEquals(started.id, ended.id)
        assertEquals(3_600_000L, f.team.attendance(AttendanceFilter(signerId = staff.accountId)).completedMillis)
        f.identity.logout()
        f.identity.login(Credentials("owner", "Password-123"))
        f.owner.checkpointCloseReopen()
        assertEquals(1, f.team.attendance().items.size)
        assertEquals(ended.endTime, f.owner.read { attendance.attendance(started.id)!!.endTime })
    } }

    @Test fun correctionPreservesOriginalsAndRefusedIntervalsDoNotMutateRoom() = runBlocking { fixture { f ->
        val staff = f.staff().employee
        val entered = f.team.sign(PresenceProof(staff.accountId, "Staff-Password-123", PresenceAction.ENTER))
        f.now += 3_600_000L
        val exited = f.team.sign(PresenceProof(staff.accountId, "Staff-Password-123", PresenceAction.EXIT))
        rejected(TeamError.INVALID_INTERVAL) {
            f.team.correct(AttendanceCorrection(exited.id, stamp(f.now), stamp(f.now - 1_000L), "Invalid interval"))
        }
        assertEquals("VALID", f.owner.read { attendance.attendance(exited.id)!!.status })
        val corrected = f.team.correct(AttendanceCorrection(
            exited.id, stamp(f.now - 1_800_000L), stamp(f.now), "Synthetic missed entry",
        ))
        assertEquals("CORRECTED", corrected.status)
        assertEquals(entered.startTime, corrected.originalStartTime)
        assertEquals(exited.endTime, corrected.originalEndTime)
        assertEquals(1L, corrected.correctedBy)
        f.owner.checkpointCloseReopen()
        val persisted = f.team.attendance(AttendanceFilter(signerId = staff.accountId)).items.single()
        assertEquals(entered.startTime, persisted.originalStartTime)
        assertEquals(exited.endTime, persisted.originalEndTime)
        f.identity.switchUser(Credentials("employee", "Staff-Password-123"))
        assertEquals(listOf(staff.accountId), f.team.attendance().items.map { it.signerId })
        rejected(TeamError.FORBIDDEN) { f.team.attendance(AttendanceFilter(signerId = 1L)) }
        try {
            f.team.correct(AttendanceCorrection(exited.id, stamp(f.now - 2_000_000L), stamp(f.now), "Forbidden edit"))
            fail("Employee must not correct")
        } catch (failure: SecurityFailure) {
            assertEquals(SecurityError.FORBIDDEN, failure.code)
        }
        assertEquals("CORRECTED", f.owner.read { attendance.attendance(exited.id)!!.status })
    } }

    @Test fun managerRestrictionsAndOpenCashBlockSensitiveChanges() = runBlocking { fixture { f ->
        val employee = f.staff().employee
        f.staff("manager", "MGR-003", "manager", manual = "Manager-Password-123")
        f.commands.execute {
            (this as RoomRepositories).dao.cash(CashEntity(
                1, "I05-SYN-CASH", employee.accountId, "OPEN", 20.0, stamp(f.now),
            ))
        }
        val original = f.team.employee(employee.accountId)
        val promotion = EmployeeUpdate(
            accountId = original.accountId, username = original.username,
            firstName = original.firstName, lastName = original.lastName,
            email = original.email, phone = original.phone, hireDate = original.hireDate,
            active = original.active, role = "manager", employeeCode = original.employeeCode,
            employment = original.employment, address = original.address, endDate = original.endDate,
        )
        rejected(TeamError.CASH_OPEN) { f.team.update(promotion) }
        assertEquals("employee", f.team.employee(employee.accountId).role)
        f.identity.switchUser(Credentials("manager", "Manager-Password-123"))
        rejected(TeamError.FORBIDDEN) {
            f.staff("second-manager", "MGR-004", "manager")
        }
        rejected(TeamError.FORBIDDEN) { f.team.update(promotion) }
        assertEquals("employee", f.team.employee(employee.accountId).role)
        assertTrue(f.owner.read { security.hasOpenCash(employee.accountId) })
    } }

    @Test fun injectedWriterFailureRollsBackAttendanceAndAuditAtomically() = runBlocking { fixture { f ->
        val staff = f.staff().employee
        val before = f.team.attendance().items.size
        val auditCount = f.owner.read { (this as RoomRepositories).dao.nextAuditId() }
        try {
            f.commands.execute {
                attendance.insert(PresenceRecord(
                    id = 1, signerId = staff.accountId, startTime = stamp(f.now),
                    endTime = null, source = "EXPLICIT", status = "VALID", sessionRef = null,
                    originalStartTime = null, originalEndTime = null, correctionReason = null,
                    correctedBy = null, correctedAt = null,
                ))
                error("synthetic I05 transaction interruption")
            }
            fail("Interrupted transaction must throw")
        } catch (failure: IllegalStateException) {
            assertEquals("synthetic I05 transaction interruption", failure.message)
        }
        f.owner.checkpointCloseReopen()
        assertEquals(before, f.team.attendance().items.size)
        assertNull(f.owner.read { attendance.attendance(1) })
        assertEquals(auditCount, f.owner.read { (this as RoomRepositories).dao.nextAuditId() })
    } }

    @Test fun automaticReferenceIsUniqueAndDurableInRealRoom() = runBlocking { fixture { f ->
        val created = f.staff("generated", code = " ").employee
        assertEquals("EMP-0001", created.employeeCode)
        assertFalse(created.hasPhoto) // photo stays optional.
        f.owner.checkpointCloseReopen()
        assertEquals("EMP-0001", f.team.employee(created.accountId).employeeCode)
        f.owner.read {
            val row = (this as RoomRepositories).dao.employeeForUser(created.accountId)
            assertEquals("EMP-0001", row!!.code)
        }
    } }
}
