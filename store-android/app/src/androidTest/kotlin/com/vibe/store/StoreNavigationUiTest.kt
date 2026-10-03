package com.vibe.store

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.vibe.store.api.*
import com.vibe.store.presentation.SecurityApp
import com.vibe.store.presentation.SpatialTheme
import com.vibe.store.presentation.StoreNavigationBar
import com.vibe.store.presentation.StoreNavigationRail
import org.junit.Rule
import org.junit.Test

class StoreNavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun employeeSeesOnlyPermittedPhoneDestinations() {
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            var page by remember { mutableStateOf("home") }
            Column {
                StoreNavigationBar(page, setOf("PRESENCE:READ"), true, true, true, true) { page = it }
                Text(page, Modifier.testTag("navigation-screen"))
            }
        } } }
        compose.onNodeWithTag("store-navigation-bottom").assertExists()
        compose.onNodeWithTag("nav-home").assertExists()
        compose.onNodeWithTag("open-presence").assertExists().performClick()
        compose.onNodeWithTag("navigation-screen").assertTextEquals("today")
        compose.onNodeWithTag("open-catalog").assertDoesNotExist()
        compose.onNodeWithTag("open-team").assertDoesNotExist()
        compose.onNodeWithTag("nav-settings").assertDoesNotExist()
    }

    @Test fun ownerCanUseAllTabletDestinations() {
        val rights = setOf("PRODUCTS:READ", "EMPLOYEES:READ", "PRESENCE:READ", "SETTINGS:READ")
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            var page by remember { mutableStateOf("home") }
            Row {
                StoreNavigationRail(page, rights, true, true, false, true) { page = it }
                Text(page, Modifier.testTag("navigation-screen"))
            }
        } } }
        compose.onNodeWithTag("store-navigation-rail").assertExists()
        for ((tag, route) in listOf(
            "open-catalog" to "catalog", "open-team" to "employees",
            "open-presence" to "today", "nav-settings" to "settings",
            "nav-home" to "home",
        )) {
            compose.onNodeWithTag(tag).performClick()
            compose.onNodeWithTag("navigation-screen").assertTextEquals(route)
        }
    }

    @Test fun authenticatedShellNavigatesToAuthorizedSettings() {
        val owner = PublicIdentity(1, "synthetic-owner", "Test", "Owner", "owner", setOf("SETTINGS:READ"))
        val service = object : IdentityService {
            override suspend fun needsOwner() = false
            override suspend fun bootstrap(input: OwnerRegistration) = error("Not requested")
            override suspend fun login(input: Credentials) = owner
            override suspend fun current() = owner
            override suspend fun switchUser(input: Credentials, confirmDiscard: Boolean) = owner
            override suspend fun logout(confirmDiscard: Boolean) = Unit
            override suspend fun recoveryQuestion(identifier: String): RecoveryQuestion = error("Not requested")
            override suspend fun recover(input: RecoveryProof) = Unit
            override suspend fun definePassword(input: PasswordDefinition) = Unit
            override suspend fun permissions(accountId: Long): PermissionView = error("Not requested")
            override suspend fun restrict(input: DenialChange): PermissionView = error("Not requested")
            override suspend fun settings() = FoundationSettings(storeName = "Synthetic shop")
            override suspend fun configure(input: FoundationSettings) = Unit
            override suspend fun preferences() = DisplayPreferences("fr", "light")
            override suspend fun preferences(input: DisplayPreferences) = Unit
        }
        compose.runOnUiThread { compose.activity.setContent { SecurityApp(service, back = {}) } }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("authenticated-name").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-settings").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("store-name").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-home").assertIsEnabled().performClick()
        compose.onNodeWithTag("authenticated-name").assertExists()
    }

    @Test fun unsavedSettingsBlockTabNavigationUntilDiscarded() {
        val owner = PublicIdentity(1, "synthetic-owner", "Test", "Owner", "owner", setOf("SETTINGS:READ"))
        val service = object : IdentityService {
            override suspend fun needsOwner() = false
            override suspend fun bootstrap(input: OwnerRegistration) = error("Not requested")
            override suspend fun login(input: Credentials) = owner
            override suspend fun current() = owner
            override suspend fun switchUser(input: Credentials, confirmDiscard: Boolean) = owner
            override suspend fun logout(confirmDiscard: Boolean) = Unit
            override suspend fun recoveryQuestion(identifier: String): RecoveryQuestion = error("Not requested")
            override suspend fun recover(input: RecoveryProof) = Unit
            override suspend fun definePassword(input: PasswordDefinition) = Unit
            override suspend fun permissions(accountId: Long): PermissionView = error("Not requested")
            override suspend fun restrict(input: DenialChange): PermissionView = error("Not requested")
            override suspend fun settings() = FoundationSettings(storeName = "Synthetic shop")
            override suspend fun configure(input: FoundationSettings) = Unit
            override suspend fun preferences() = DisplayPreferences("fr", "light")
            override suspend fun preferences(input: DisplayPreferences) = Unit
        }
        compose.runOnUiThread { compose.activity.setContent { SecurityApp(service, back = {}) } }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("authenticated-name").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home-quick-settings").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("store-name").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("store-name").performTextInput(" changed")
        compose.onNodeWithTag("settings-unsaved").assertExists()
        compose.onNodeWithTag("nav-home").assertIsNotEnabled()
        compose.onNodeWithText("Retour").performScrollTo().performClick()
        compose.onNodeWithTag("authenticated-name").assertExists()
        compose.onNodeWithTag("nav-home").assertIsEnabled()
    }

    @Test fun quickAccessUsesEffectivePermissionsOnly() {
        val employee = PublicIdentity(7, "employee", "Synthetic", "Employee", "employee", setOf("PRESENCE:READ"))
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            var destination by remember { mutableStateOf("") }
            Column {
                com.vibe.store.presentation.StoreHome(employee, "STORE", true, true,
                    hasCatalog = true, hasTeam = true, onNavigate = { destination = it },
                    onSwitch = {}, onLogout = {})
                Text(destination, Modifier.testTag("quick-destination"))
            }
        } } }
        compose.onNodeWithTag("home-quick-catalog").assertDoesNotExist()
        compose.onNodeWithTag("home-quick-employees").assertDoesNotExist()
        compose.onNodeWithTag("home-quick-settings").assertDoesNotExist()
        compose.onNodeWithTag("home-quick-today").assertExists().performClick()
        compose.onNodeWithTag("quick-destination").assertTextEquals("today")
    }

    @Test fun sensitiveNavigationCanBeDisabledWithoutHidingRoutes() {
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(false) {
            StoreNavigationBar("today", setOf("EMPLOYEES:READ", "PRESENCE:READ"), false, true, true, false) {
                error("Disabled navigation must not invoke destination")
            }
        } } }
        compose.onNodeWithTag("open-team").assertExists().assertIsNotEnabled()
        compose.onNodeWithTag("open-presence").assertExists().assertIsNotEnabled()
    }
}
