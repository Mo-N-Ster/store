package com.vibe.store.application.team

import com.vibe.store.api.*
import com.vibe.store.application.security.IdentityAuthority

/**
 * One public TeamService; separate account and attendance responsibilities.
 * Both authorities reuse the same I03 identity/session/transaction boundary.
 */
class TeamAuthority(
    identity: IdentityAuthority,
    media: ProfilePhotoMedia,
    clock: () -> Long = System::currentTimeMillis,
) : TeamService {

    private val accounts = TeamAccountAuthority(
        identity = identity,
        media = media,
        clock = clock,
    )

    private val presence = PresenceAuthority(
        identity = identity,
        clock = clock,
    )

    override suspend fun employees(
        filter: EmployeeFilter,
    ): List<EmployeeSummary> =
        accounts.employees(filter)

    override suspend fun employee(
        accountId: Long,
    ): EmployeeDetail =
        accounts.employee(accountId)

    override suspend fun create(
        input: EmployeeCreation,
    ): EmployeeWrite =
        accounts.create(input)

    override suspend fun update(
        input: EmployeeUpdate,
    ): EmployeeDetail =
        accounts.update(input)

    override suspend fun password(
        input: PasswordChange,
    ): TemporaryPassword? =
        accounts.password(input)

    override suspend fun photo(
        accountId: Long,
    ): ProductImage? =
        accounts.photo(accountId)

    override suspend fun photo(
        accountId: Long,
        change: ProfilePhotoChange,
    ): EmployeeDetail =
        accounts.photo(accountId, change)

    override suspend fun today(
        filter: DailyAttendanceFilter,
    ): DailyAttendancePage =
        presence.today(filter)

    override suspend fun attendance(
        filter: AttendanceFilter,
    ): AttendancePage =
        presence.attendance(filter)

    override suspend fun sign(
        input: PresenceProof,
    ): AttendanceView =
        presence.sign(input)

    override suspend fun correct(
        input: AttendanceCorrection,
    ): AttendanceView =
        presence.correct(input)
}
