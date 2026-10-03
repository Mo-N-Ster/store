package com.vibe.store

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.vibe.store.api.*
import com.vibe.store.presentation.SpatialTheme
import com.vibe.store.presentation.TeamScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic UI fixture only; no production database, password or remote access. */
class TeamUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private class Service : TeamService {
        val owner = DailyAttendanceRow(1, null, "owner", "Test", "Owner", "owner", true, null,
            null, 0, PresenceAction.ENTER)
        val member = EmployeeSummary(2, 4, "synthetic", "Test", "Employee", "employee", true, "T-002", "ACTIVE", false)
        val detail = EmployeeDetail(2, 4, "synthetic", "Test", "Employee", "TE", null, null, null,
            "employee", true, "T-002", "ACTIVE", null, null, false)
        var creation: EmployeeCreation? = null
        var proof: PresenceProof? = null
        var signed = false

        override suspend fun employees(filter: EmployeeFilter): List<EmployeeSummary> =
            if (filter.search.isBlank() || member.username.contains(filter.search)) listOf(member) else emptyList()
        override suspend fun employee(accountId: Long) = detail
        override suspend fun create(input: EmployeeCreation): EmployeeWrite {
            creation = input
            return EmployeeWrite(detail, TemporaryPassword("Synthetic-temp-123"))
        }
        override suspend fun update(input: EmployeeUpdate) = detail
        override suspend fun password(input: PasswordChange) = TemporaryPassword("Synthetic-temp-123")
        override suspend fun photo(accountId: Long): ProductImage? = null
        override suspend fun photo(accountId: Long, change: ProfilePhotoChange) = detail
        @Volatile var todayQueried = false
        override suspend fun today(filter: DailyAttendanceFilter): DailyAttendancePage {
            todayQueried = true
            return DailyAttendancePage(
                listOf(owner.copy(nextAction = if (signed) PresenceAction.EXIT else PresenceAction.ENTER)), false)
        }
        override suspend fun attendance(filter: AttendanceFilter) = AttendancePage(emptyList(), 0L, false)
        override suspend fun sign(input: PresenceProof): AttendanceView {
            proof = input; signed = true
            return AttendanceView(3, input.signerId, "2026-10-01T09:00:00.000Z", null, "VALID", "EXPLICIT",
                null, null, null, null, null)
        }
        override suspend fun correct(input: AttendanceCorrection): AttendanceView = error("not requested")
    }

    @Test fun createEmployeeWithContinuousTextAndOneShotTemporarySecret() {
        val service = Service()
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            TeamScreen(service, setOf("EMPLOYEES:READ", "EMPLOYEES:UPDATE"), 1, "owner", true,
                "employees", { it(null) }) { }
        } } }
        compose.onNodeWithTag("team-create").performScrollTo().performClick()
        try {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("employee-username")
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: Throwable) {
            val tree = compose.onRoot(useUnmergedTree = true)
                .printToString(maxDepth = 30)

            throw AssertionError(
                "Employee form not visible after create click: $tree",
                failure,
            )
        }
        compose.onNodeWithTag("employee-username").performScrollTo().performTextInput("new")
        compose.onNodeWithTag("employee-username").performScrollTo().performTextInput("-member")
        compose.onNodeWithTag("employee-first").performScrollTo().performTextInput("Nina")
        compose.onNodeWithTag("employee-last").performScrollTo().performTextInput("Test")
        compose.onNodeWithTag("employee-code").performScrollTo().performTextInput("T-004")
        compose.onNodeWithText("Choisir une photo").performScrollTo().performClick()
        compose.onNodeWithText("Aucune nouvelle photo").assertExists()
        // Finish editing through the real Android IME action. No retry or test-only hide.
        compose.onNodeWithTag("employee-code").performImeAction()
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            val bounds = compose.onNodeWithTag("employee-save")
                .fetchSemanticsNode().boundsInWindow
            bounds.width > 0f && bounds.height > 0f
        }
        compose.onNodeWithTag("employee-save").assertIsEnabled().performClick()
        compose.waitUntil(10_000) {
            compose.runOnUiThread { service.creation != null }
        }
        assertEquals("new-member", service.creation!!.username)
        assertNull(service.creation!!.photo)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("temporary-password").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Fermer").performClick()
        compose.onNodeWithTag("temporary-password").assertDoesNotExist()
    }

    @Test fun personalProofNeverOccursOnScreenOpenAndUsesSignersPassword() {
        val service = Service()
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            TeamScreen(service, setOf("PRESENCE:READ"), 1, "owner", true, "today", { it(null) }) { }
        } } }
        compose.waitUntil(10_000) { service.todayQueried }
        compose.waitForIdle()
        assertNull(service.proof)
        compose.onNodeWithTag("today-list")
            .performScrollToNode(hasTestTag("presence-user-1"))
        compose.onNodeWithTag("presence-user-1").assertExists()
        assertNull(service.proof)
        compose.onNodeWithTag("presence-sign-1").performScrollTo().performClick()
        try {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag(
                    "presence-password",
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: Throwable) {
            val tree = runCatching {
                compose.onRoot(useUnmergedTree = true)
                    .printToString(maxDepth = 30)
            }.getOrElse { "Semantics unavailable: ${it.message}" }

            throw AssertionError(
                "Attendance password field not visible after signing click: $tree",
                failure,
            )
        }
        compose.onNodeWithTag("presence-password", useUnmergedTree = true).performTextInput("My-")
        compose.onNodeWithTag("presence-password", useUnmergedTree = true).performTextInput("own-password")
        compose.onNodeWithTag("presence-confirm").performClick()
        compose.waitUntil(10_000) { service.proof != null }
        assertEquals(1L, service.proof!!.signerId)
        assertEquals("My-own-password", service.proof!!.password)
        assertEquals(PresenceAction.ENTER, service.proof!!.action)
        compose.onNodeWithTag("presence-password", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("correction-confirm").assertDoesNotExist()
    }

    @Test fun employeeCannotSeeAdministrativeControls() {
        val service = Service()
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            TeamScreen(service, setOf("PRESENCE:READ"), 2, "employee", false, "today", { it(null) }) { }
        } } }
        compose.onNodeWithTag("team-tab").assertDoesNotExist()
        compose.onNodeWithTag("team-create").assertDoesNotExist()
        compose.onNodeWithTag("correction-confirm").assertDoesNotExist()
    }
}
