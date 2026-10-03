package com.vibe.store.application.team

import com.vibe.store.api.AttendanceFilter
import com.vibe.store.api.EmployeeFilter

/*
 * Trusted internal projection joining the authentication account and its
 * optional employee profile. Password verifiers are deliberately absent.
 */
data class EmployeeRecord(
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
    val photo: String?,
    val employeeCode: String,
    val employment: String,
    val address: String?,
    val createdAt: String,
    val updatedAt: String,
    val endDate: String?,
)

data class EmployeeProfileRecord(
    val id: Long,
    val accountId: Long,
    val code: String,
    val firstName: String,
    val lastName: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val phone: String?,
    val email: String?,
    val address: String?,
    val hireDate: String?,
    val endDate: String?,
)

interface EmployeeRecords {
    suspend fun find(accountId: Long): EmployeeRecord?
    suspend fun page(filter: EmployeeFilter): List<EmployeeRecord>

    suspend fun nextEmployeeId(): Long
    suspend fun duplicateCode(code: String, exceptEmployeeId: Long? = null): Boolean

    suspend fun insert(profile: EmployeeProfileRecord)
    suspend fun update(profile: EmployeeProfileRecord)
}

data class PresenceRecord(
    val id: Long,
    val signerId: Long,
    val startTime: String,
    val endTime: String?,
    val source: String,
    val status: String,
    val sessionRef: String?,
    val originalStartTime: String?,
    val originalEndTime: String?,
    val correctionReason: String?,
    val correctedBy: Long?,
    val correctedAt: String?,
)

data class PresenceQuery(
    val startInclusive: String = "",
    val endExclusive: String = "",
    val signerId: Long? = null,
    val role: String = "",
    val status: String = "",
    val limit: Int = 100,
    val offset: Int = 0,
)
interface ProfilePhotoMedia {
    suspend fun encode(selection: com.vibe.store.api.SelectedImage): String
    suspend fun decode(dataUrl: String): com.vibe.store.api.ProductImage
}
interface PresenceRecords {
    suspend fun find(id: Long): PresenceRecord?
    suspend fun page(query: PresenceQuery): List<PresenceRecord>

    suspend fun openFor(signerId: Long): PresenceRecord?
    suspend fun nextAttendanceId(): Long

    suspend fun insert(record: PresenceRecord)
    suspend fun update(record: PresenceRecord)
}
