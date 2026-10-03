package com.vibe.store.application.team

import com.vibe.store.api.*
import com.vibe.store.application.persistence.ReadRepositories
import com.vibe.store.application.persistence.TransactionRepositories
import com.vibe.store.application.security.*
import com.vibe.store.domain.IdentityPolicy
import com.vibe.store.domain.TeamPolicy
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal class TeamAccountAuthority(
    private val identity: IdentityAuthority,
    private val media: ProfilePhotoMedia,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    private fun fail(code: TeamError): Nothing = throw TeamFailure(code)

    private inline fun <T> validated(block: () -> T): T =
        try {
            block()
        } catch (_: IllegalArgumentException) {
            fail(TeamError.INVALID_INPUT)
        }

    private fun stamp(): String =
        SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.ROOT,
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }.format(Date(clock()))

    private fun initials(firstName: String, lastName: String): String =
        "${firstName.first()}${lastName.first()}".uppercase(Locale.ROOT)

    private data class NormalizedProfile(
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
        val endDate: String?,
    )

    private fun normalize(
        username: String,
        firstName: String,
        lastName: String,
        email: String?,
        phone: String?,
        hireDate: String?,
        role: String,
        employeeCode: String,
        employment: String,
        address: String?,
        endDate: String?,
        allowGeneratedCode: Boolean = false,
    ): NormalizedProfile = validated {
        NormalizedProfile(
            username = TeamPolicy.requiredText(username),
            firstName = TeamPolicy.requiredText(firstName),
            lastName = TeamPolicy.requiredText(lastName),
            email = TeamPolicy.email(email),
            phone = TeamPolicy.optionalText(phone),
            hireDate = TeamPolicy.optionalText(hireDate),
            role = TeamPolicy.role(role),
            employeeCode = if (allowGeneratedCode && employeeCode.isBlank()) ""
                else TeamPolicy.requiredText(employeeCode),
            employment = TeamPolicy.employment(employment),
            address = TeamPolicy.optionalText(address),
            endDate = TeamPolicy.optionalText(endDate),
        )
    }

    private fun EmployeeRecord.summary() = EmployeeSummary(
        accountId = accountId,
        employeeId = employeeId,
        username = username,
        firstName = firstName,
        lastName = lastName,
        role = role,
        active = active,
        employeeCode = employeeCode,
        employment = employment,
        hasPhoto = photo != null,
    )

    private fun EmployeeRecord.detail() = EmployeeDetail(
        accountId = accountId,
        employeeId = employeeId,
        username = username,
        firstName = firstName,
        lastName = lastName,
        initials = initials,
        email = email,
        phone = phone,
        hireDate = hireDate,
        role = role,
        active = active,
        employeeCode = employeeCode,
        employment = employment,
        address = address,
        endDate = endDate,
        hasPhoto = photo != null,
    )

    private fun identityKeys(
        username: String,
        email: String?,
        firstName: String,
        lastName: String,
    ): Set<String> =
        listOfNotNull(
            username,
            email,
            "$firstName $lastName",
            "$lastName $firstName",
        ).map(IdentityPolicy::fold)
            .filter(String::isNotEmpty)
            .toSet()

    private fun identityKeys(account: AuthAccount): Set<String> =
        identityKeys(
            account.username,
            account.email,
            account.firstName,
            account.lastName,
        )

    private suspend fun ReadRepositories.ensureUniqueIdentity(
        profile: NormalizedProfile,
        exceptId: Long? = null,
    ) {
        val requested = identityKeys(
            profile.username,
            profile.email,
            profile.firstName,
            profile.lastName,
        )

        val collision = security.accounts()
            .asSequence()
            .filter { it.id != exceptId }
            .any { existing ->
                identityKeys(existing).any(requested::contains)
            }

        if (collision) fail(TeamError.CONFLICT)
    }

    private suspend fun ReadRepositories.guardCreate(
        actorId: Long,
        role: String,
    ) {
        val actor = security.account(actorId) ?: fail(TeamError.FORBIDDEN)

        when (actor.role) {
            "owner" -> if (role !in setOf("manager", "employee")) {
                fail(TeamError.FORBIDDEN)
            }

            "manager" -> if (role != "employee") {
                fail(TeamError.FORBIDDEN)
            }

            else -> fail(TeamError.FORBIDDEN)
        }
    }

    private suspend fun ReadRepositories.guardTarget(
        actorId: Long,
        target: AuthAccount,
        resultingRole: String,
    ) {
        val actor = security.account(actorId) ?: fail(TeamError.FORBIDDEN)

        if (target.role == "owner") {
            fail(TeamError.FORBIDDEN)
        }

        when (actor.role) {
            "owner" -> if (resultingRole !in setOf("manager", "employee")) {
                fail(TeamError.FORBIDDEN)
            }

            "manager" -> {
                if (target.role != "employee" || resultingRole != "employee") {
                    fail(TeamError.FORBIDDEN)
                }
            }

            else -> fail(TeamError.FORBIDDEN)
        }
    }

    private fun temporarySecret(): String {
        val alphabet =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"

        return buildString {
            append("Store-")
            repeat(16) {
                append(alphabet[random.nextInt(alphabet.length)])
            }
            append('!')
        }
    }

    private fun password(
        manual: String?,
        generate: Boolean,
    ): Pair<String, TemporaryPassword?> {
        if ((manual != null) == generate) {
            fail(TeamError.INVALID_INPUT)
        }

        val value = manual ?: temporarySecret()

        validated {
            TeamPolicy.password(value)
        }

        return value to if (generate) TemporaryPassword(value) else null
    }

    suspend fun employees(
        filter: EmployeeFilter = EmployeeFilter(),
    ): List<EmployeeSummary> {
        if (filter.offset < 0 || filter.limit !in 1..200) {
            fail(TeamError.INVALID_INPUT)
        }

        if (filter.role.isNotEmpty()) {
            validated { TeamPolicy.role(filter.role) }
        }

        if (filter.employment.isNotEmpty()) {
            validated { TeamPolicy.employment(filter.employment) }
        }

        return identity.authorizedRead("EMPLOYEES:READ") {
            employees.page(filter).map { it.summary() }
        }
    }

    suspend fun employee(accountId: Long): EmployeeDetail {
        if (accountId <= 0) fail(TeamError.INVALID_INPUT)

        return identity.authorizedRead("EMPLOYEES:READ") {
            employees.find(accountId)?.detail()
                ?: fail(TeamError.NOT_FOUND)
        }
    }

    suspend fun create(input: EmployeeCreation): EmployeeWrite {
        val profile = normalize(
            username = input.username,
            firstName = input.firstName,
            lastName = input.lastName,
            email = input.email,
            phone = input.phone,
            hireDate = input.hireDate,
            role = input.role,
            employeeCode = input.employeeCode,
            employment = input.employment,
            address = input.address,
            endDate = null,
            allowGeneratedCode = true,
        )

        if (profile.role == "owner") {
            fail(TeamError.FORBIDDEN)
        }

        identity.authorizedRead("EMPLOYEES:UPDATE") { actor ->
            guardCreate(actor, profile.role)
        }

        val (secret, temporary) = password(
            input.manualPassword,
            input.generateTemporaryPassword,
        )

        val verifier = identity.hashManagedSecret(secret)
        val photo = input.photo?.let { media.encode(it) }

        val detail = identity.authorizedWrite("EMPLOYEES:UPDATE") { actor ->
            guardCreate(actor, profile.role)
            ensureUniqueIdentity(profile)

            // Allocate inside the authorized write transaction: no race, no reservation
            // on a rejected create, and legacy/manual codes remain untouched.
            val employeeId = employees.nextEmployeeId()
            var assignedCode = profile.employeeCode
            if (assignedCode.isEmpty()) {
                var suffix = employeeId
                var attempts = 0
                while (true) {
                    assignedCode = "EMP-" + suffix.toString().padStart(4, '0')
                    if (!employees.duplicateCode(assignedCode)) break
                    if (suffix == Long.MAX_VALUE || ++attempts >= 1000) {
                        fail(TeamError.CONFLICT)
                    }
                    suffix++
                }
            } else if (employees.duplicateCode(assignedCode)) {
                fail(TeamError.CONFLICT)
            }

            val now = stamp()

            val accountId = security.createManaged(
                NewManagedAccount(
                    username = profile.username,
                    email = profile.email,
                    firstName = profile.firstName,
                    lastName = profile.lastName,
                    initials = initials(profile.firstName, profile.lastName),
                    role = profile.role,
                    verifier = verifier,
                    phone = profile.phone,
                    hireDate = profile.hireDate,
                    active = true,
                    photo = photo,
                    stamp = now,
                ),
            )

            employees.insert(
                EmployeeProfileRecord(
                    id = employeeId,
                    accountId = accountId,
                    code = assignedCode,
                    firstName = profile.firstName,
                    lastName = profile.lastName,
                    status = profile.employment,
                    createdAt = now,
                    updatedAt = now,
                    phone = profile.phone,
                    email = profile.email,
                    address = profile.address,
                    hireDate = profile.hireDate,
                    endDate = null,
                ),
            )

            security.appendAudit(
                SecurityAudit(
                    actorId = actor,
                    responsibleId = actor,
                    action = "employee_create",
                    entity = "employee",
                    reference = accountId.toString(),
                    success = true,
                    stamp = now,
                ),
            )

            employees.find(accountId)?.detail()
                ?: fail(TeamError.CONFLICT)
        }

        return EmployeeWrite(detail, temporary)
    }

    suspend fun update(input: EmployeeUpdate): EmployeeDetail {
        if (input.accountId <= 0) fail(TeamError.INVALID_INPUT)

        val profile = normalize(
            username = input.username,
            firstName = input.firstName,
            lastName = input.lastName,
            email = input.email,
            phone = input.phone,
            hireDate = input.hireDate,
            role = input.role,
            employeeCode = input.employeeCode,
            employment = input.employment,
            address = input.address,
            endDate = input.endDate,
        )

        return identity.authorizedWrite("EMPLOYEES:UPDATE") { actor ->
            val target = security.account(input.accountId)
                ?: fail(TeamError.NOT_FOUND)

            val current = employees.find(input.accountId)
                ?: fail(TeamError.NOT_FOUND)

            guardTarget(actor, target, profile.role)
            val protectedChange =
                target.role != profile.role ||
                    target.active != input.active ||
                    current.employment != profile.employment ||
                    current.endDate != profile.endDate

            if (protectedChange && security.hasOpenCash(target.id)) {
                fail(TeamError.CASH_OPEN)
            }
            ensureUniqueIdentity(profile, input.accountId)

            if (
                employees.duplicateCode(
                    profile.employeeCode,
                    current.employeeId,
                )
            ) {
                fail(TeamError.CONFLICT)
            }

            val now = stamp()

            security.updateManaged(
                ManagedAccountChange(
                    accountId = input.accountId,
                    username = profile.username,
                    email = profile.email,
                    firstName = profile.firstName,
                    lastName = profile.lastName,
                    initials = initials(
                        profile.firstName,
                        profile.lastName,
                    ),
                    role = profile.role,
                    phone = profile.phone,
                    hireDate = profile.hireDate,
                    active = input.active,
                    photo = current.photo,
                    stamp = now,
                ),
            )

            employees.update(
                EmployeeProfileRecord(
                    id = current.employeeId,
                    accountId = input.accountId,
                    code = profile.employeeCode,
                    firstName = profile.firstName,
                    lastName = profile.lastName,
                    status = profile.employment,
                    createdAt = current.createdAt,
                    updatedAt = now,
                    phone = profile.phone,
                    email = profile.email,
                    address = profile.address,
                    hireDate = profile.hireDate,
                    endDate = profile.endDate,
                ),
            )

            security.appendAudit(
                SecurityAudit(
                    actorId = actor,
                    responsibleId = actor,
                    action = "employee_update",
                    entity = "employee",
                    reference = input.accountId.toString(),
                    success = true,
                    stamp = now,
                ),
            )

            employees.find(input.accountId)?.detail()
                ?: fail(TeamError.CONFLICT)
        }
    }

    suspend fun password(
        input: PasswordChange,
    ): TemporaryPassword? {
        if (input.accountId <= 0) fail(TeamError.INVALID_INPUT)

        val (secret, temporary) = password(
            input.manualPassword,
            input.generateTemporaryPassword,
        )

        identity.definePassword(
            PasswordDefinition(
                accountId = input.accountId,
                newPassword = secret,
            ),
        )

        return temporary
    }

    suspend fun photo(accountId: Long): ProductImage? {
        if (accountId <= 0) fail(TeamError.INVALID_INPUT)

        val dataUrl = identity.authorizedRead("EMPLOYEES:READ") {
            employees.find(accountId)?.photo
                ?: return@authorizedRead null
        }

        return dataUrl?.let { media.decode(it) }
    }

    suspend fun photo(
        accountId: Long,
        change: ProfilePhotoChange,
    ): EmployeeDetail {
        if (accountId <= 0) fail(TeamError.INVALID_INPUT)

        val replacing = change.replacement != null

        if (replacing == change.remove) {
            fail(TeamError.INVALID_INPUT)
        }

        identity.authorizedRead("EMPLOYEES:UPDATE") { actor ->
            val target = security.account(accountId)
                ?: fail(TeamError.NOT_FOUND)

            guardTarget(actor, target, target.role)

            if (employees.find(accountId) == null) {
                fail(TeamError.NOT_FOUND)
            }
        }

        val encoded = change.replacement?.let {
            media.encode(it)
        }

        return identity.authorizedWrite("EMPLOYEES:UPDATE") { actor ->
            val target = security.account(accountId)
                ?: fail(TeamError.NOT_FOUND)

            val current = employees.find(accountId)
                ?: fail(TeamError.NOT_FOUND)

            guardTarget(actor, target, target.role)

            val now = stamp()

            security.updateManaged(
                ManagedAccountChange(
                    accountId = target.id,
                    username = target.username,
                    email = target.email,
                    firstName = target.firstName,
                    lastName = target.lastName,
                    initials = initials(
                        target.firstName,
                        target.lastName,
                    ),
                    role = target.role,
                    phone = current.phone,
                    hireDate = current.hireDate,
                    active = target.active,
                    photo = if (change.remove) null else encoded,
                    stamp = now,
                ),
            )

            security.appendAudit(
                SecurityAudit(
                    actorId = actor,
                    responsibleId = actor,
                    action = if (change.remove) {
                        "profile_photo_remove"
                    } else {
                        "profile_photo_update"
                    },
                    entity = "employee",
                    reference = accountId.toString(),
                    success = true,
                    stamp = now,
                ),
            )

            employees.find(accountId)?.detail()
                ?: fail(TeamError.CONFLICT)
        }
    }
}
