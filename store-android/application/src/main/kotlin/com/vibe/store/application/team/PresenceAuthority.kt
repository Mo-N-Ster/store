package com.vibe.store.application.team

import com.vibe.store.api.*
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.application.security.SecurityAudit
import com.vibe.store.domain.TeamPolicy
import com.vibe.store.domain.IdentityPolicy
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal class PresenceAuthority(
    private val identity: IdentityAuthority,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeZone: () -> TimeZone = { TimeZone.getDefault() },
) {
    private fun fail(code: TeamError): Nothing = throw TeamFailure(code)

    private fun utcFormat() =
        SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.ROOT,
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }

    private fun stamp(epoch: Long = clock()): String =
        utcFormat().format(Date(epoch))

    private data class Bounds(
        val startInclusive: String,
        val endExclusive: String,
    )

    private fun localDay(epoch: Long): Calendar =
        Calendar.getInstance(timeZone()).apply {
            timeInMillis = epoch
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun parsedDay(value: String): Calendar {
        if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value)) {
            fail(TeamError.INVALID_INPUT)
        }

        val parsed = try {
            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.ROOT,
            ).apply {
                timeZone = this@PresenceAuthority.timeZone()
                isLenient = false
            }.parse(value)
        } catch (_: java.text.ParseException) {
            null
        } ?: fail(TeamError.INVALID_INPUT)

        return Calendar.getInstance(timeZone()).apply {
            time = parsed
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun dayBounds(day: Calendar): Bounds {
        val start = day.clone() as Calendar
        val end = day.clone() as Calendar
        end.add(Calendar.DAY_OF_MONTH, 1)

        return Bounds(
            startInclusive = stamp(start.timeInMillis),
            endExclusive = stamp(end.timeInMillis),
        )
    }

    private fun historyBounds(
        from: String,
        to: String,
    ): Bounds {
        val first =
            if (from.isEmpty()) null else parsedDay(from)

        val last =
            if (to.isEmpty()) null else parsedDay(to)

        if (
            first != null &&
            last != null &&
            first.timeInMillis > last.timeInMillis
        ) {
            fail(TeamError.INVALID_INPUT)
        }

        val endExclusive = last?.let {
            (it.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        return Bounds(
            startInclusive =
                first?.let { stamp(it.timeInMillis) } ?: "",
            endExclusive =
                endExclusive?.let {
                    stamp(it.timeInMillis)
                } ?: "",
        )
    }

    private data class DailySubject(
        val accountId: Long,
        val employeeId: Long?,
        val username: String,
        val firstName: String,
        val lastName: String,
        val role: String,
        val active: Boolean,
        val employment: String?,
    )
    private fun PresenceRecord.view() = AttendanceView(
        id = id,
        signerId = signerId,
        startTime = startTime,
        endTime = endTime,
        status = status,
        source = source,
        originalStartTime = originalStartTime,
        originalEndTime = originalEndTime,
        correctionReason = correctionReason,
        correctedBy = correctedBy,
        correctedAt = correctedAt,
    )

    private fun completedMillis(
        records: List<PresenceRecord>,
    ): Long =
        try {
            records.sumOf {
                TeamPolicy.completedDuration(
                    it.startTime,
                    it.endTime,
                )
            }
        } catch (_: IllegalArgumentException) {
            fail(TeamError.INVALID_INTERVAL)
        }

    private fun validatePaging(
        limit: Int,
        offset: Int,
    ) {
        if (limit !in 1..200 || offset < 0) {
            fail(TeamError.INVALID_INPUT)
        }
    }

    suspend fun today(
        filter: DailyAttendanceFilter = DailyAttendanceFilter(),
    ): DailyAttendancePage {
        validatePaging(filter.limit, filter.offset)

        if (filter.role.isNotEmpty()) {
            try {
                TeamPolicy.role(filter.role)
            } catch (_: IllegalArgumentException) {
                fail(TeamError.INVALID_INPUT)
            }
        }

        if (filter.employment.isNotEmpty()) {
            try {
                TeamPolicy.employment(filter.employment)
            } catch (_: IllegalArgumentException) {
                fail(TeamError.INVALID_INPUT)
            }
        }

        val bounds = dayBounds(localDay(clock()))
        val search = IdentityPolicy.fold(filter.search)

        return identity.authorizedRead("PRESENCE:READ") {
            val owners = mutableListOf<DailySubject>()

            // An Owner without an HR profile is a real account,
            // not a synthetic EmployeeEntity.
            if (
                filter.employment.isEmpty() &&
                (filter.role.isEmpty() || filter.role == "owner")
            ) {
                for (account in security.accounts()) {
                    if (
                        account.role != "owner" ||
                        employees.find(account.id) != null
                    ) {
                        continue
                    }

                    val names = listOfNotNull(
                        account.username,
                        account.email,
                        account.firstName,
                        account.lastName,
                        "${account.firstName} ${account.lastName}",
                        "${account.lastName} ${account.firstName}",
                    )

                    if (
                        search.isNotEmpty() &&
                        names.none {
                            IdentityPolicy.fold(it).contains(search)
                        }
                    ) {
                        continue
                    }

                    owners += DailySubject(
                        accountId = account.id,
                        employeeId = null,
                        username = account.username,
                        firstName = account.firstName,
                        lastName = account.lastName,
                        role = account.role,
                        active = account.active,
                        employment = null,
                    )
                }
            }

            owners.sortBy { it.accountId }

            val ownerPage = owners
                .drop(filter.offset)
                .take(filter.limit)

            val employeeSlots = filter.limit - ownerPage.size

            // Account for Owner rows before paginating HR profiles.
            val employeeOffset =
                (filter.offset - owners.size).coerceAtLeast(0)

            val employeePage = employees.page(
                EmployeeFilter(
                    search = filter.search,
                    role = filter.role,
                    employment = filter.employment,
                    active = null,
                    limit = employeeSlots + 1,
                    offset = employeeOffset,
                ),
            )

            val subjects = ownerPage +
                employeePage.take(employeeSlots).map { employee ->
                    DailySubject(
                        accountId = employee.accountId,
                        employeeId = employee.employeeId,
                        username = employee.username,
                        firstName = employee.firstName,
                        lastName = employee.lastName,
                        role = employee.role,
                        active = employee.active,
                        employment = employee.employment,
                    )
                }

            val rows = subjects.map { subject ->
                val records = attendance.page(
                    PresenceQuery(
                        startInclusive = bounds.startInclusive,
                        endExclusive = bounds.endExclusive,
                        signerId = subject.accountId,
                        limit = 200,
                    ),
                )

                val open = attendance.openFor(
                    subject.accountId,
                )

                val latest = open ?: records.firstOrNull()

                val eligible = subject.active && (
                    (
                        subject.role == "owner" &&
                        subject.employeeId == null
                    ) ||
                        subject.employment in setOf(
                            "ACTIVE",
                            "ABSENT",
                        )
                    )

                val next = when {
                    !eligible -> null
                    open == null -> PresenceAction.ENTER
                    open.status == "VALID" -> PresenceAction.EXIT
                    else -> null
                }

                DailyAttendanceRow(
                    accountId = subject.accountId,
                    employeeId = subject.employeeId,
                    username = subject.username,
                    firstName = subject.firstName,
                    lastName = subject.lastName,
                    role = subject.role,
                    active = subject.active,
                    employment = subject.employment,
                    attendance = latest?.view(),
                    completedMillis = completedMillis(
                        records.filter { it.endTime != null },
                    ),
                    nextAction = next,
                )
            }

            DailyAttendancePage(
                rows = rows,
                hasMore =
                    owners.size >
                        filter.offset + ownerPage.size ||
                        employeePage.size > employeeSlots,
            )
        }
    }

    suspend fun attendance(
        filter: AttendanceFilter = AttendanceFilter(),
    ): AttendancePage {
        validatePaging(filter.limit, filter.offset)

        if (filter.signerId?.let { it <= 0 } == true) {
            fail(TeamError.INVALID_INPUT)
        }

        if (filter.role.isNotEmpty()) {
            try {
                TeamPolicy.role(filter.role)
            } catch (_: IllegalArgumentException) {
                fail(TeamError.INVALID_INPUT)
            }
        }

        if (filter.status.isNotEmpty()) {
            if (filter.status !in TeamPolicy.attendanceStates) {
                fail(TeamError.INVALID_INPUT)
            }
        }

        val bounds = historyBounds(
            filter.from,
            filter.to,
        )

        return identity.authorizedRead("PRESENCE:READ") { actor ->
            val account = security.account(actor)
                ?: fail(TeamError.FORBIDDEN)

            var signerId = filter.signerId
            var role = filter.role

            if (account.role == "employee") {
                if (
                    signerId != null &&
                    signerId != actor
                ) {
                    fail(TeamError.FORBIDDEN)
                }

                if (
                    role.isNotEmpty() &&
                    role != "employee"
                ) {
                    fail(TeamError.FORBIDDEN)
                }

                signerId = actor
                role = "employee"
            }

            val records = attendance.page(
                PresenceQuery(
                    startInclusive = bounds.startInclusive,
                    endExclusive = bounds.endExclusive,
                    signerId = signerId,
                    role = role,
                    status = filter.status,
                    limit = filter.limit + 1,
                    offset = filter.offset,
                ),
            )

            val visible = records.take(filter.limit)

            AttendancePage(
                items = visible.map { it.view() },
                completedMillis = completedMillis(
                    visible.filter { it.endTime != null },
                ),
                hasMore = records.size > filter.limit,
            )
        }
    }

    suspend fun sign(
        input: PresenceProof,
    ): AttendanceView {
        if (input.signerId <= 0) {
            fail(TeamError.INVALID_INPUT)
        }

        val result =
            identity.authorizedWrite("PRESENCE:READ") { actor ->
                val verified =
                    this@PresenceAuthority.identity.verifyPresenceSecret(
                        repositories = this,
                        accountId = input.signerId,
                        password = input.password,
                        responsibleId = actor,
                    )

                if (!verified) {
                    return@authorizedWrite null
                }

                val account = security.account(
                    input.signerId,
                ) ?: fail(TeamError.NOT_FOUND)

                val employee = employees.find(
                    input.signerId,
                )

                // Only Owner may sign without an HR profile.
                // All other signers need a valid EmployeeRecord.
                val allowed =
                    account.active &&
                        if (employee == null) {
                            account.role == "owner"
                        } else {
                            employee.active &&
                                employee.employment in setOf(
                                    "ACTIVE",
                                    "ABSENT",
                                )
                        }

                if (!allowed) {
                    fail(TeamError.FORBIDDEN)
                }

                val open = attendance.openFor(
                    input.signerId,
                )

                val now = stamp()

                val record = when (input.action) {
                    PresenceAction.ENTER -> {
                        if (open != null) {
                            fail(
                                TeamError.INVALID_TRANSITION,
                            )
                        }

                        PresenceRecord(
                            id = attendance.nextAttendanceId(),
                            signerId = input.signerId,
                            startTime = now,
                            endTime = null,
                            source = "EXPLICIT",
                            status = "VALID",
                            sessionRef = null,
                            originalStartTime = null,
                            originalEndTime = null,
                            correctionReason = null,
                            correctedBy = null,
                            correctedAt = null,
                        ).also {
                            attendance.insert(it)
                        }
                    }

                    PresenceAction.EXIT -> {
                        val current =
                            open ?: fail(
                                TeamError.INVALID_TRANSITION,
                            )

                        if (current.status != "VALID") {
                            fail(
                                TeamError.INVALID_TRANSITION,
                            )
                        }

                        try {
                            TeamPolicy.interval(
                                current.startTime,
                                now,
                            )
                        } catch (_: IllegalArgumentException) {
                            fail(TeamError.INVALID_INTERVAL)
                        }

                        current.copy(
                            endTime = now,
                        ).also {
                            attendance.update(it)
                        }
                    }
                }

                security.appendAudit(
                    SecurityAudit(
                        actorId = actor,
                        responsibleId = actor,
                        action = when (input.action) {
                            PresenceAction.ENTER ->
                                "attendance_clock_in"

                            PresenceAction.EXIT ->
                                "attendance_clock_out"
                        },
                        entity = "attendance",
                        reference = record.id.toString(),
                        success = true,
                        stamp = now,
                    ),
                )

                record.view()
            }

        return result
            ?: fail(TeamError.INVALID_CREDENTIALS)
    }

    suspend fun correct(
        input: AttendanceCorrection,
    ): AttendanceView {
        if (input.attendanceId <= 0) {
            fail(TeamError.INVALID_INPUT)
        }

        val reason =
            try {
                TeamPolicy.correctionReason(
                    input.reason,
                )
            } catch (_: IllegalArgumentException) {
                fail(TeamError.INVALID_INPUT)
            }

        val startMillis: Long
        val endMillis: Long

        try {
            startMillis =
                TeamPolicy.timestamp(input.startTime)

            endMillis =
                TeamPolicy.timestamp(input.endTime)

            TeamPolicy.interval(
                input.startTime,
                input.endTime,
            )
        } catch (_: IllegalArgumentException) {
            fail(TeamError.INVALID_INTERVAL)
        }

        val nowMillis = clock()

        if (
            startMillis > nowMillis ||
            endMillis > nowMillis
        ) {
            fail(TeamError.INVALID_INTERVAL)
        }

        return identity.authorizedWrite(
            "PRESENCE:UPDATE",
        ) { actor ->
            val current =
                attendance.find(input.attendanceId)
                    ?: fail(TeamError.NOT_FOUND)

            val now = stamp(nowMillis)

            val corrected = current.copy(
                startTime = input.startTime,
                endTime = input.endTime,
                status = "CORRECTED",
                originalStartTime =
                    if (current.status == "CORRECTED") {
                        current.originalStartTime
                    } else {
                        current.originalStartTime
                            ?: current.startTime
                    },
                originalEndTime =
                    if (current.status == "CORRECTED") {
                        current.originalEndTime
                    } else {
                        current.originalEndTime
                            ?: current.endTime
                    },
                correctionReason = reason,
                correctedBy = actor,
                correctedAt = now,
            )

            attendance.update(corrected)

            security.appendAudit(
                SecurityAudit(
                    actorId = actor,
                    responsibleId = actor,
                    action = "attendance_corrected",
                    entity = "attendance",
                    reference =
                        input.attendanceId.toString(),
                    success = true,
                    stamp = now,
                ),
            )

            corrected.view()
        }
    }
}
