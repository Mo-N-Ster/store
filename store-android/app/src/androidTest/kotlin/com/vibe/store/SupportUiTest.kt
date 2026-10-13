package com.vibe.store

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.vibe.store.api.*
import com.vibe.store.presentation.SupportScreen
import com.vibe.store.presentation.SpatialTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** I08 UI integration: help and role visibility are presentation-only, never authorization. */
class SupportUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val store = object : SupportService {
        override suspend fun recipients() = emptyList<SupportAccount>()
        override suspend fun messages(offset: Int, unreadOnly: Boolean) = emptyList<LocalMessage>()
        override suspend fun send(draft: MessageDraft): LocalMessage = error("not part of UI smoke")
        override suspend fun markRead(id: Long) = Unit
        override suspend fun hideMessage(id: Long) = Unit
        override suspend fun alerts(offset: Int) = emptyList<LocalAlert>()
        override suspend fun markAlertRead(id: Long) = Unit
        override suspend fun resolveAlert(id: Long) = Unit
        override suspend fun audit(filter: AuditFilter) = emptyList<AuditEntry>()
        override suspend fun diagnostics() = SupportHealth(2, true, true, true, 1, 0, 0, null, null)
    }
    private val identity = object : IdentityService {
        override suspend fun needsOwner() = false
        override suspend fun bootstrap(input: OwnerRegistration): PublicIdentity = error("unused")
        override suspend fun login(input: Credentials): PublicIdentity = error("unused")
        override suspend fun current(): PublicIdentity? = null
        override suspend fun switchUser(input: Credentials, confirmDiscard: Boolean): PublicIdentity = error("unused")
        override suspend fun logout(confirmDiscard: Boolean) = Unit
        override suspend fun recoveryQuestion(identifier: String): RecoveryQuestion = error("unused")
        override suspend fun recover(input: RecoveryProof) = Unit
        override suspend fun definePassword(input: PasswordDefinition) = Unit
        override suspend fun permissions(accountId: Long): PermissionView = error("unused")
        override suspend fun restrict(input: DenialChange): PermissionView = error("unused")
        override suspend fun settings(): FoundationSettings = FoundationSettings()
        override suspend fun configure(input: FoundationSettings) = Unit
        override suspend fun preferences(): DisplayPreferences = DisplayPreferences()
        override suspend fun preferences(input: DisplayPreferences) = Unit
    }
    private fun show(role: String, rights: Set<String> = emptySet()) {
        compose.runOnUiThread { compose.activity.setContent {
            SpatialTheme(false) {
                SupportScreen(store, identity, PublicIdentity(3, "test", "Synthetic", "User", role, rights),
                    french = true, onSettings = {}, onBack = {}, onNavigationLock = {})
            }
        } }
        compose.waitForIdle()
    }
    @Test fun employeeSeesChatHelpButNoAdministrationOrAlerts() {
        show("employee")
        compose.onNodeWithTag("i08-support").assertExists()
        compose.onNodeWithTag("i08-tab-chat").assertExists()
        compose.onNodeWithTag("i08-tab-help").assertExists()
        compose.onNodeWithTag("i08-tab-admin").assertDoesNotExist()
        compose.onNodeWithTag("i08-tab-audit").assertDoesNotExist()
        compose.onNodeWithTag("i08-tab-help").performClick()
        compose.onNodeWithTag("i08-help-search").assertExists()
    }
    @Test fun ownerHasAdminAuditAndDiagnosticTabs() {
        show("owner", setOf("ADMINISTRATION:READ", "ADMINISTRATION:UPDATE"))
        compose.onNodeWithTag("i08-tab-admin").assertExists()
        compose.onNodeWithTag("i08-tab-audit").assertExists()
        compose.onNodeWithTag("i08-tab-diagnostics").assertExists()
    }

    @Test fun contextualReportLinkOpensTheMatchingOfflineHelpArticle() {
        compose.runOnUiThread { compose.activity.setContent {
            SpatialTheme(false) {
                SupportScreen(store, identity, PublicIdentity(3, "test", "Synthetic", "User", "employee", emptySet()),
                    french = false, onSettings = {}, onBack = {}, onNavigationLock = {}, initialHelpTopic = "reports")
            }
        } }
        compose.waitForIdle()
        compose.onNodeWithTag("i08-tab-help").assertExists()
        compose.onNodeWithText("Understanding reports").assertExists()
        compose.onNodeWithText("First setup").assertDoesNotExist()
        compose.onNodeWithTag("help-show-all-topics").assertTextContains("Show all help topics").performClick()
        compose.onNodeWithText("First setup").assertExists()
        compose.onNodeWithTag("i08-help-search").performTextInput("validated revenue")
        compose.onNodeWithText("Understanding reports").assertExists()
        compose.onNodeWithTag("help-category-reports").assertExists()
    }
}
