package com.vibe.store.api

enum class PresenceAction {
    ENTER,
    EXIT,
}

data class EmployeeFilter(
    val search: String = "",
    val role: String = "",
    val employment: String = "",
    val active: Boolean? = null,
    val limit: Int = 100,
    val offset: Int = 0,
)

data class EmployeeSummary(
    val accountId: Long,
    val employeeId: Long,
    val username: String,
    val firstName: String,
    val lastName: String,
    val role: String,
    val active: Boolean,
    val employeeCode: String,
    val employment: String,
    val hasPhoto: Boolean,
)

data class EmployeeDetail(
    val accountId: Long,
    val employeeId: Long,
    val username: String,
    val firstName: String,
    val lastName: String,
    val initials: String,
    val email: String?,
    val phone: String?,
    val hireDate: String?,
    val role: String,
    val active: Boolean,
    val employeeCode: String,
    val employment: String,
    val address: String?,
    val endDate: String?,
    val hasPhoto: Boolean,
)

/*
 * Password is input-only.
 * This is deliberately not a data class: generated toString() must not expose it.
 */
class EmployeeCreation(
    val username: String,
    val firstName: String,
    val lastName: String,
    val email: String?,
    val phone: String?,
    val hireDate: String?,
    val role: String,
    val employeeCode: String,
    val employment: String,
    val address: String?,
    val manualPassword: String?,
    val generateTemporaryPassword: Boolean,
    val photo: SelectedImage? = null,
)

data class EmployeeUpdate(
    val accountId: Long,
    val username: String,
    val firstName: String,
    val lastName: String,
    val email: String?,
    val phone: String?,
    val hireDate: String?,
    val active: Boolean,
    val role: String,
    val employeeCode: String,
    val employment: String,
    val address: String?,
    val endDate: String?,
)

class ProfilePhotoChange(
    val replacement: SelectedImage? = null,
    val remove: Boolean = false,
)

class PasswordChange(
    val accountId: Long,
    val manualPassword: String?,
    val generateTemporaryPassword: Boolean,
)
class TemporaryPassword(val value: String) {
    override fun toString(): String = "TemporaryPassword(REDACTED)"
}

data class EmployeeWrite(
    val employee: EmployeeDetail,
    val temporaryPassword: TemporaryPassword? = null,
)

data class AttendanceFilter(
    val from: String = "",
    val to: String = "",
    val signerId: Long? = null,
    val role: String = "",
    val status: String = "",
    val limit: Int = 100,
    val offset: Int = 0,
)

data class AttendanceView(
    val id: Long,
    val signerId: Long,
    val startTime: String,
    val endTime: String?,
    val status: String,
    val source: String,
    val originalStartTime: String?,
    val originalEndTime: String?,
    val correctionReason: String?,
    val correctedBy: Long?,
    val correctedAt: String?,
)

data class AttendancePage(
    val items: List<AttendanceView>,
    val completedMillis: Long,
    val hasMore: Boolean,
)
data class DailyAttendanceFilter(
    val search: String = "",
    val role: String = "",
    val employment: String = "",
    val limit: Int = 100,
    val offset: Int = 0,
)

data class DailyAttendanceRow(
    val accountId: Long,
    val employeeId: Long?,
    val username: String,
    val firstName: String,
    val lastName: String,
    val role: String,
    val active: Boolean,
    val employment: String?,
    val attendance: AttendanceView?,
    val completedMillis: Long,
    val nextAction: PresenceAction?,
)

data class DailyAttendancePage(
    val rows: List<DailyAttendanceRow>,
    val hasMore: Boolean,
)

/*
 * The signer proves their own password.
 * The connected facilitator is never supplied by the UI.
 */
class PresenceProof(
    val signerId: Long,
    val password: String,
    val action: PresenceAction,
)

data class AttendanceCorrection(
    val attendanceId: Long,
    val startTime: String,
    val endTime: String,
    val reason: String,
)

enum class TeamError {
    INVALID_INPUT,
    INVALID_CREDENTIALS,
    NOT_FOUND,
    FORBIDDEN,
    CONFLICT,
    CASH_OPEN,
    INVALID_TRANSITION,
    INVALID_INTERVAL,
    INVALID_MEDIA,
    MEDIA_TOO_LARGE,
    MEDIA_UNAVAILABLE,
}

class TeamFailure(val code: TeamError) : Exception(code.name)

interface TeamService {
    suspend fun employees(filter: EmployeeFilter = EmployeeFilter()): List<EmployeeSummary>
    suspend fun employee(accountId: Long): EmployeeDetail

    suspend fun create(input: EmployeeCreation): EmployeeWrite
    suspend fun update(input: EmployeeUpdate): EmployeeDetail
    suspend fun password(input: PasswordChange): TemporaryPassword?

    suspend fun photo(accountId: Long): ProductImage?
    suspend fun photo(accountId: Long, change: ProfilePhotoChange): EmployeeDetail

    suspend fun today(
        filter: DailyAttendanceFilter = DailyAttendanceFilter(),
    ): DailyAttendancePage
    suspend fun attendance(filter: AttendanceFilter = AttendanceFilter()): AttendancePage

    suspend fun sign(input: PresenceProof): AttendanceView
    suspend fun correct(input: AttendanceCorrection): AttendanceView
}
