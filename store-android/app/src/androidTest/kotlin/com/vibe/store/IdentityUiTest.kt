package com.vibe.store

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import com.vibe.store.api.*
import com.vibe.store.presentation.PasswordField
import com.vibe.store.presentation.SpatialTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class IdentityUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<StoreApplication>()
    private fun signedIn() = runBlocking {
        if (app.identity.needsOwner()) app.identity.bootstrap(OwnerRegistration("i03-ui-owner", "i03@example.invalid", "I03", "Synthetic", "Synthetic-123", "Synthetic question", "Synthetic answer"))
        if (app.identity.current() == null) app.identity.login(Credentials("i03-ui-owner", "Synthetic-123"))
    }
    @Test fun continuousPasswordEntryAndVisibility() {
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme {
            var value by remember { mutableStateOf("") }
            PasswordField("Password", value, false, false) { value = it }
        } } }
        val field = compose.onNodeWithTag("password")
        fun assertRenderedPassword(expected: String) {
            val layouts = mutableListOf<TextLayoutResult>()
            field.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                assertTrue(action(layouts))
            }
            assertTrue("Expected password visual transformation", layouts.any { it.layoutInput.text.text == expected })
        }
        field.performClick(); field.performTextInput("Abc"); field.performTextInput("123"); field.performTextInput("é!")
        field.assertTextContains("Abc123é!"); field.assertIsFocused()
        field.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        assertRenderedPassword("•".repeat(8))
        compose.onNodeWithTag("password-eye").performClick()
        // KeyboardType.Password keeps sensitive semantics even when text is visible.
        field.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        assertRenderedPassword("Abc123é!")
        compose.onNodeWithTag("password-eye").performClick()
        field.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        assertRenderedPassword("•".repeat(8))
    }
    @Test fun rotationBackgroundAndProtectedNavigation() {
        signedIn(); val sameAuthority = app.identity
        compose.onNodeWithTag("access-store").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("authenticated-name").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("authenticated-name").assertTextEquals("I03 Synthetic")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("authenticated-name").fetchSemanticsNodes().isNotEmpty() }
        assertSame(sameAuthority, app.identity)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("authenticated-name").fetchSemanticsNodes().isNotEmpty() }
        assertNotNull(runBlocking { app.identity.current() })
    }
    @Test fun failedLoginRetryAndRecoveryCleanForm() {
        signedIn(); runBlocking { app.identity.logout() }
        compose.onNodeWithTag("access-store").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("identifier").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("identifier").performTextInput("i03-ui-owner")
        compose.onNodeWithTag("password").performTextInput("wrong")
        compose.onNodeWithTag("login-submit").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("security-error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Mot de passe oublié").performScrollTo().performClick()
        compose.onNodeWithTag("identifier").performTextInput("i03-ui-owner")
        compose.onNodeWithText("Continuer").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("answer").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("answer").performTextInput("Synthetic answer")
        compose.onNodeWithTag("password").performTextInput("Synthetic-123")
        compose.onNodeWithText("Modifier").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("login-title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("identifier").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithTag("password").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithTag("identifier").performTextInput("i03-ui-owner")
        compose.onNodeWithTag("password").performTextInput("Synthetic-")
        compose.onNodeWithTag("password").performTextInput("123")
        compose.onNodeWithTag("login-submit").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("authenticated-name").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun applicationSessionNotRestoredAfterRealDeath() = runBlocking {
        val phase = InstrumentationRegistry.getArguments().getString("appRestartPhase")
        require(phase in setOf("prepare", "verify"))
        val evidence = app.getSharedPreferences("i03-synthetic-test-evidence", 0)
        if (phase == "prepare") {
            signedIn(); assertNotNull(app.identity.current())
            assertTrue(evidence.edit().putInt("previous-process", android.os.Process.myPid()).commit())
            println("I03 APP PREPARED PID=${android.os.Process.myPid()}")
        } else {
            assertNotEquals(evidence.getInt("previous-process", -1), android.os.Process.myPid())
            assertNull(app.identity.current())
            try { app.identity.settings(); fail("Unauthenticated navigation must not authorize") }
            catch (failure: SecurityFailure) { assertEquals(SecurityError.FORBIDDEN, failure.code) }
            println("I03 APP VERIFIED PID=${android.os.Process.myPid()}")
        }
    }
}
