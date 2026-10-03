package com.vibe.store

import androidx.activity.compose.setContent
import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.vibe.store.api.*
import com.vibe.store.presentation.SpatialTheme
import com.vibe.store.presentation.TeamScreen
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

/** Synthetic presentation checks; no production database or sensitive user data. */
class TeamPresentationUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    /** G-EVIDENCE: real window resizing, synthetic people, no persistent mutations. */
    @Test fun adaptiveTeamWindowAndEvidence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)
        ).bufferedReader().use { it.readText() }
        val original = Regex("Override size: (\\d+x\\d+)")
            .find(shell("wm size"))?.groupValues?.get(1)
        val density = compose.activity.resources.displayMetrics.density
        val base = Fixture()
        val service = object : TeamService by base {
            override suspend fun employees(filter: EmployeeFilter) = (2L..4L).map {
                base.member.copy(accountId = it, firstName = "Marie $it")
            }
            override suspend fun employee(accountId: Long) = base.details.copy(accountId = accountId)
        }
        fun render(activity: MainActivity) {
            activity.setContent { SpatialTheme(false) {
                TeamScreen(service, setOf("EMPLOYEES:READ", "EMPLOYEES:UPDATE"), 1,
                    "owner", true, "employees", { it(null) }) { }
            } }
        }
        val app = compose.activity.application
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { if (activity is MainActivity) render(activity) }
            override fun onActivityCreated(activity: Activity, saved: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, out: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        try {
            compose.runOnUiThread { render(compose.activity) }
            for ((dp, mode, columns) in listOf(
                Triple(599, "COMPACT", 1), Triple(600, "MEDIUM", 2),
                Triple(839, "MEDIUM", 2), Triple(840, "EXPANDED", 3),
                Triple(599, "COMPACT", 1),
            )) {
                shell("wm size ${(dp * density).roundToInt()}x${(1100 * density).roundToInt()}")
                compose.waitUntil(10_000) {
                    var actual = 0f
                    compose.activityRule.scenario.onActivity {
                        actual = it.windowManager.currentWindowMetrics.bounds.width() / it.resources.displayMetrics.density
                    }
                    kotlin.math.abs(actual - dp) < 0.1f &&
                        compose.onAllNodesWithTag("team-layout-$mode").fetchSemanticsNodes().size == 1
                }
                compose.onNodeWithTag("team-list").performScrollToNode(hasTestTag("team-user-2"))
                val card = compose.onNodeWithTag("team-user-2").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val list = compose.onNodeWithTag("team-list").fetchSemanticsNode().boundsInRoot
                assertTrue("Actual card width reflects $columns columns", kotlin.math.abs(card.width * columns / list.width - 1f) < 0.08f)
                for (id in 2L until 2L + columns) {
                    val bounds = compose.onNodeWithTag("team-user-$id").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    assertTrue("Cards share the same row", kotlin.math.abs(bounds.top - card.top) < 1f)
                }
                compose.waitForIdle()
                val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                File(instrumentation.targetContext.getExternalFilesDir(null), "i05-$mode.png").outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                bitmap.recycle()
                compose.onNode(hasText("Voir la fiche") and hasAnyAncestor(hasTestTag("team-user-2")))
                    .performScrollTo().performClick()
                compose.onNodeWithTag("employee-editor").assertExists()
                for (field in listOf("employee-first", "employee-last", "employee-code")) {
                    compose.onNodeWithTag(field).performScrollTo().assertIsDisplayed()
                }
                compose.onNodeWithTag("employee-cancel").assertIsDisplayed().performClick()
                compose.onNodeWithTag("team-layout-$mode").assertIsDisplayed()
            }
            assertNull(base.proof)
            assertNull(base.correction)
        } finally {
            shell(if (original == null) "wm size reset" else "wm size $original")
            app.unregisterActivityLifecycleCallbacks(callbacks)
        }
    }

    private class Fixture : TeamService {
        val member = EmployeeSummary(2, 4, "synthetic", "Marie", "Durand", "employee",
            true, "EMP-0004", "ACTIVE", false)
        val details = EmployeeDetail(2, 4, "synthetic", "Marie", "Durand", "MD", null,
            null, null, "employee", true, "EMP-0004", "ACTIVE", null, null, false)
        val record = AttendanceView(3, 2, "2026-10-01T09:00:00.000Z", "2026-10-01T17:00:00.000Z",
            "CORRECTED", "EXPLICIT", "2026-10-01T09:05:00.000Z",
            "2026-10-01T17:00:00.000Z", "Synthetic correction", 1,
            "2026-10-01T18:00:00.000Z")
        var proof: PresenceProof? = null
        var correction: AttendanceCorrection? = null
        @Volatile var employeesQueried = false
        @Volatile var historyQueried = false
        override suspend fun employees(filter: EmployeeFilter): List<EmployeeSummary> {
            employeesQueried = true
            return listOf(member)
        }
        override suspend fun employee(accountId: Long) = details
        override suspend fun create(input: EmployeeCreation): EmployeeWrite = error("Not requested")
        override suspend fun update(input: EmployeeUpdate) = error("Not requested")
        override suspend fun password(input: PasswordChange): TemporaryPassword? = error("Not requested")
        override suspend fun photo(accountId: Long): ProductImage? = null
        override suspend fun photo(accountId: Long, change: ProfilePhotoChange): EmployeeDetail =
            error("Not requested")
        override suspend fun today(filter: DailyAttendanceFilter) = DailyAttendancePage(emptyList(), false)
        override suspend fun attendance(filter: AttendanceFilter): AttendancePage {
            historyQueried = true
            return AttendancePage(listOf(record), 28_800_000L, false)
        }
        override suspend fun sign(input: PresenceProof): AttendanceView {
            proof = input
            return record
        }
        override suspend fun correct(input: AttendanceCorrection): AttendanceView {
            correction = input
            return record
        }
    }

    @Test fun employeeCardSeparatesIdentityRoleAndAccess() {
        val fixture = Fixture()
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            TeamScreen(fixture, setOf("EMPLOYEES:READ", "EMPLOYEES:UPDATE"), 1, "owner",
                true, "employees", { it(null) }) { }
        } } }
        compose.waitUntil(10_000) { fixture.employeesQueried }
        compose.waitForIdle()
        compose.onNodeWithTag("team-list").performScrollToNode(hasTestTag("team-user-2"))
        compose.onNodeWithTag("team-avatar-2").assertExists()
        compose.onNodeWithTag("team-role-2").assertExists()
        compose.onNodeWithTag("team-employment-2").assertExists()
        compose.onNodeWithTag("team-access-2").assertDoesNotExist()
        compose.onNodeWithText("EMP-0004").assertExists()
        assertNull(fixture.proof)
    }

    @Test fun historyDefaultsToReadableDatesAndKeepsVerbatimAuditOnDemand() {
        val fixture = Fixture()
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            TeamScreen(fixture, setOf("PRESENCE:READ"), 1, "owner", true,
                "history", { it(null) }) { }
        } } }
        compose.waitUntil(10_000) { fixture.historyQueried }
        compose.waitForIdle()
        compose.onNodeWithTag("attendance-history")
            .performScrollToNode(hasTestTag("attendance-3"))
        compose.onNodeWithTag("attendance-status-3").assertExists()
        compose.onNodeWithTag("attendance-audit-3").assertDoesNotExist()
        compose.onNodeWithTag("attendance-details-3").performScrollTo().performClick()
        compose.onNodeWithTag("attendance-audit-3").assertExists()
        compose.onNodeWithText("UTC: 2026-10-01T09:00:00.000Z → 2026-10-01T17:00:00.000Z")
            .assertExists()
        compose.onNodeWithTag("attendance-details-3").performScrollTo().performClick()
        compose.onNodeWithTag("attendance-audit-3").assertDoesNotExist()
        assertNull(fixture.correction)
    }

    @Test fun editorGroupsFieldsAndAllowsMissingPhotoAndAutomaticReference() {
        val fixture = Fixture()
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            TeamScreen(fixture, setOf("EMPLOYEES:READ", "EMPLOYEES:UPDATE"), 1, "owner",
                true, "employees", { it(null) }) { }
        } } }
        compose.waitUntil(10_000) { fixture.employeesQueried }
        compose.onNodeWithTag("team-list")
            .performScrollToNode(hasTestTag("team-create"))
        compose.onNodeWithTag("team-create").performClick()
        compose.onNodeWithTag("employee-editor").assertExists()
        for (section in listOf("identity", "contact", "employment", "access", "password", "photo", "actions")) {
            compose.onNodeWithTag("employee-section-$section").assertExists()
        }
        compose.onNodeWithTag("employee-code").assertExists()
        compose.onNodeWithTag("employee-save").assertIsNotEnabled()
        compose.onNodeWithTag("employee-username").performScrollTo().performTextInput("synthetic_new")
        compose.onNodeWithTag("employee-first").performScrollTo().performTextInput("Camille")
        compose.onNodeWithTag("employee-last").performScrollTo().performTextInput("Martin")
        compose.waitForIdle()
        // Neither a manually entered reference nor a photo is needed to enable creation.
        compose.onNodeWithTag("employee-save").assertIsEnabled()
        compose.onNodeWithTag("employee-cancel").assertExists()
        assertNull(fixture.proof)
    }
}
