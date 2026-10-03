package com.vibe.store.infrastructure.persistence

import com.vibe.store.api.AttendanceFilter
import com.vibe.store.api.EmployeeFilter
import com.vibe.store.application.persistence.AttendanceRepository
import com.vibe.store.application.persistence.EmployeeRepository
import com.vibe.store.application.team.EmployeeProfileRecord
import com.vibe.store.application.team.EmployeeRecord
import com.vibe.store.application.team.PresenceRecord
import com.vibe.store.application.team.PresenceQuery
import com.vibe.store.domain.AttendanceRecord
import com.vibe.store.domain.IdentityPolicy

internal class RoomEmployeeRepository(
    private val dao: StoreDao,
    private val check: suspend (Boolean) -> Unit,
) : EmployeeRepository {

    private fun record(user: UserEntity, employee: EmployeeEntity) = EmployeeRecord(
        accountId = user.id,
        employeeId = employee.id,
        username = user.username,
        firstName = user.firstName,
        lastName = user.lastName,
        initials = user.initials,
        email = user.email,
        phone = user.phone,
        hireDate = user.hireDate,
        role = user.role,
        active = user.active,
        photo = user.photo,
        employeeCode = employee.code,
        employment = employee.status,
        address = employee.address,
        createdAt = employee.createdAt,
        updatedAt = employee.updatedAt,
        endDate = employee.endDate,
    )

    override suspend fun find(accountId: Long): EmployeeRecord? {
        check(false)
        val user = dao.account(accountId) ?: return null
        val employee = dao.employeeForUser(accountId) ?: return null
        return record(user, employee)
    }

    override suspend fun page(filter: EmployeeFilter): List<EmployeeRecord> {
        check(false)

        val users = dao.accounts().associateBy { it.id }
        val search = IdentityPolicy.fold(filter.search)

        return dao.employees()
            .asSequence()
            .mapNotNull { employee ->
                val user = employee.userId?.let(users::get) ?: return@mapNotNull null
                record(user, employee)
            }
            .filter { value ->
                filter.role.isEmpty() || value.role == filter.role
            }
            .filter { value ->
                filter.employment.isEmpty() || value.employment == filter.employment
            }
            .filter { value ->
                filter.active == null || value.active == filter.active
            }
            .filter { value ->
                search.isEmpty() || listOfNotNull(
                    value.username,
                    value.firstName,
                    value.lastName,
                    value.employeeCode,
                    value.email,
                    "${value.firstName} ${value.lastName}",
                    "${value.lastName} ${value.firstName}",
                ).any { IdentityPolicy.fold(it).contains(search) }
            }
            .drop(filter.offset)
            .take(filter.limit)
            .toList()
    }

    override suspend fun nextEmployeeId(): Long {
        check(false)
        return dao.nextEmployeeId()
    }

    override suspend fun duplicateCode(code: String, exceptEmployeeId: Long?): Boolean {
        check(false)
        return dao.duplicateEmployeeCode(code, exceptEmployeeId)
    }

    override suspend fun insert(profile: EmployeeProfileRecord) {
        check(true)
        dao.employee(
            EmployeeEntity(
                id = profile.id,
                code = profile.code,
                userId = profile.accountId,
                firstName = profile.firstName,
                lastName = profile.lastName,
                status = profile.status,
                createdAt = profile.createdAt,
                updatedAt = profile.updatedAt,
                phone = profile.phone,
                email = profile.email,
                address = profile.address,
                hireDate = profile.hireDate,
                endDate = profile.endDate,
            ),
        )
    }

    override suspend fun update(profile: EmployeeProfileRecord) {
        check(true)
        dao.updateEmployee(
            EmployeeEntity(
                id = profile.id,
                code = profile.code,
                userId = profile.accountId,
                firstName = profile.firstName,
                lastName = profile.lastName,
                status = profile.status,
                createdAt = profile.createdAt,
                updatedAt = profile.updatedAt,
                phone = profile.phone,
                email = profile.email,
                address = profile.address,
                hireDate = profile.hireDate,
                endDate = profile.endDate,
            ),
        )
    }
}

internal class RoomAttendanceRepository(
    private val dao: StoreDao,
    private val check: suspend (Boolean) -> Unit,
) : AttendanceRepository {

    private fun AttendanceEntity.record() = PresenceRecord(
        id = id,
        signerId = userId,
        startTime = startTime,
        endTime = endTime,
        source = source,
        status = status,
        sessionRef = sessionRef,
        originalStartTime = originalStartTime,
        originalEndTime = originalEndTime,
        correctionReason = correctionReason,
        correctedBy = correctedBy,
        correctedAt = correctedAt,
    )

    override suspend fun attendance(id: Long): AttendanceRecord? {
        check(false)
        return dao.findAttendance(id)?.let {
            AttendanceRecord(it.id, it.userId, it.startTime, it.endTime, it.status)
        }
    }

    override suspend fun find(id: Long): PresenceRecord? {
        check(false)
        return dao.findAttendance(id)?.record()
    }

    override suspend fun page(query: PresenceQuery): List<PresenceRecord> {
        check(false)
        return dao.attendancePage(
            query.startInclusive,
            query.endExclusive,
            query.signerId,
            query.role,
            query.status,
            query.limit,
            query.offset,
        ).map { it.record() }
    }

    override suspend fun openFor(signerId: Long): PresenceRecord? {
        check(false)
        return dao.openAttendance(signerId)?.record()
    }

    override suspend fun nextAttendanceId(): Long {
        check(false)
        return dao.nextAttendanceId()
    }

    override suspend fun insert(record: PresenceRecord) {
        check(true)
        dao.attendance(record.entity())
    }

    override suspend fun update(record: PresenceRecord) {
        check(true)
        dao.updateAttendance(record.entity())
    }

    private fun PresenceRecord.entity() = AttendanceEntity(
        id = id,
        userId = signerId,
        startTime = startTime,
        source = source,
        status = status,
        endTime = endTime,
        sessionRef = sessionRef,
        originalStartTime = originalStartTime,
        originalEndTime = originalEndTime,
        correctionReason = correctionReason,
        correctedBy = correctedBy,
        correctedAt = correctedAt,
    )
}
