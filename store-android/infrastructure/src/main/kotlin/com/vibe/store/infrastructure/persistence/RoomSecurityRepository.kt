package com.vibe.store.infrastructure.persistence

import com.vibe.store.application.security.*
import com.vibe.store.domain.RolePolicy
import java.text.SimpleDateFormat
import java.util.*

internal class RoomSecurityRepository(private val dao: StoreDao, private val check: suspend (Boolean) -> Unit) : SecurityRepository {
    private fun timestamp(value: Long?): String? = value?.let { format().format(Date(it)) }
    private fun format() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }
    private fun millis(value: String?): Long? = value?.let { runCatching { format().parse(it)?.time }.getOrNull() }
    private suspend fun record(user: UserEntity) = AuthAccount(user.id, user.username, user.email, user.firstName, user.lastName, user.role, user.active, dao.employment(user.id), user.passwordHash, user.securityQuestion, user.securityAnswerHash, user.failedLoginAttempts, millis(user.lockedUntil), user.failedRecoveryAttempts, millis(user.recoveryLockedUntil))
    override suspend fun accounts(): List<AuthAccount> { check(false); return dao.accounts().map { record(it) } }
    override suspend fun account(id: Long): AuthAccount? { check(false); return dao.account(id)?.let { record(it) } }
    override suspend fun createOwner(input: NewOwner): Long {
        check(true)
        val existing = dao.accounts(); check(existing.none { it.role == "owner" })
        val roles = dao.roles().toMutableList(); val permissions = dao.permissions().toMutableList()
        RolePolicy.codes.sorted().forEach { code ->
            if (permissions.none { "${it.module}:${it.action}" == code }) {
                val permission = PermissionEntity((permissions.maxOfOrNull { it.id } ?: 0) + 1, code.substringBefore(':'), code.substringAfter(':'), input.stamp)
                dao.permission(permission); permissions += permission
            }
        }
        listOf("owner", "manager", "employee").forEach { name ->
            if (roles.none { it.code == name }) {
                val role = RoleEntity((roles.maxOfOrNull { it.id } ?: 0) + 1, name, name, true, input.stamp)
                dao.role(role); roles += role
                permissions.filter { "${it.module}:${it.action}" in RolePolicy.inherited(name) }.forEach { dao.rolePermission(RolePermissionEntity(role.id, it.id)) }
            }
        }
        val id = (existing.maxOfOrNull { it.id } ?: 0) + 1
        dao.user(UserEntity(id, input.username, input.verifier, "owner", input.firstName, input.lastName,
            "${input.firstName.take(1)}${input.lastName.take(1)}".uppercase(Locale.ROOT), input.stamp,
            email = input.email, securityQuestion = input.question, securityAnswerHash = input.recoveryVerifier))
        dao.userRole(UserRoleEntity(id, roles.single { it.code == "owner" }.id, input.stamp))
        return id
    }
    override suspend fun createManaged(input: NewManagedAccount): Long {
        check(true)
        require(input.role in setOf("manager", "employee"))

        val existing = dao.accounts()
        val role = checkNotNull(dao.roles().singleOrNull { it.code == input.role })
        val id = (existing.maxOfOrNull { it.id } ?: 0) + 1

        dao.user(
            UserEntity(
                id = id,
                username = input.username,
                passwordHash = input.verifier,
                role = input.role,
                firstName = input.firstName,
                lastName = input.lastName,
                initials = input.initials,
                createdAt = input.stamp,
                email = input.email,
                phone = input.phone,
                hireDate = input.hireDate,
                active = input.active,
                photo = input.photo,
            ),
        )

        dao.putUserRole(UserRoleEntity(id, role.id, input.stamp))
        return id
    }

    override suspend fun updateManaged(input: ManagedAccountChange) {
        check(true)

        val current = checkNotNull(dao.account(input.accountId))

        if (current.role == "owner") {
            require(input.role == "owner" && input.active)
        } else {
            require(input.role in setOf("manager", "employee"))
        }

        val role = checkNotNull(dao.roles().singleOrNull { it.code == input.role })

        dao.updateUser(
            current.copy(
                username = input.username,
                email = input.email,
                firstName = input.firstName,
                lastName = input.lastName,
                initials = input.initials,
                role = input.role,
                phone = input.phone,
                hireDate = input.hireDate,
                active = input.active,
                photo = input.photo,
            ),
        )

        dao.putUserRole(
            UserRoleEntity(
                userId = current.id,
                roleId = role.id,
                assignedAt = input.stamp,
            ),
        )
    }
    override suspend fun inherited(id: Long): Set<String> { check(false); return dao.inherited(id).toSet() }
    override suspend fun denied(id: Long): Set<String> { check(false); return dao.denied(id).toSet() }
    override suspend fun replaceDenials(id: Long, codes: Set<String>) {
        check(true); val permissions = dao.permissions().associateBy { "${it.module}:${it.action}" }
        require(codes.all { it in permissions }); dao.deleteDenials(id)
        codes.forEach { dao.denial(DenialEntity(id, permissions.getValue(it).id)) }
    }
    override suspend fun loginResult(id: Long, failures: Int, until: Long?, successAt: String?) {
        check(true); val user = checkNotNull(dao.account(id))
        dao.updateUser(user.copy(failedLoginAttempts = failures, lockedUntil = timestamp(until), lastLoginAt = successAt ?: user.lastLoginAt))
    }
    override suspend fun recoveryResult(id: Long, failures: Int, until: Long?, verifier: String?) {
        check(true); val user = checkNotNull(dao.account(id))
        dao.updateUser(if (verifier == null) user.copy(failedRecoveryAttempts = failures, recoveryLockedUntil = timestamp(until))
            else user.copy(passwordHash = verifier, failedRecoveryAttempts = 0, recoveryLockedUntil = null, failedLoginAttempts = 0, lockedUntil = null))
    }
    override suspend fun hasOpenCash(id: Long): Boolean { check(false); return dao.openCash(id) != null }
    override suspend fun appendAudit(event: SecurityAudit) {
        check(true)
        val responsible = event.responsibleId?.let { dao.account(it) }
        val cash = responsible?.let { dao.openCash(it.id) }
        dao.audit(AuditEntity(dao.nextAuditId(), event.actorId, event.action, event.entity, event.reference, null,
            if (event.success) "SUCCESS" else "FAILURE", event.stamp, responsible?.id,
            responsible?.let { "${it.firstName} ${it.lastName}" }, cash?.reference,
            cash?.let { com.vibe.store.domain.ExactMoney.add(it.openingAmount, dao.capturedPayments(it.id)) },
            cash?.let { dao.settingValue("currency") ?: "EUR" }))
    }
}
