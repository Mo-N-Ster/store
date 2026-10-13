package com.vibe.store

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.vibe.store.presentation.ContextualHelp
import com.vibe.store.presentation.SpatialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ContextualHelpUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun explanationIsProgressiveAccessibleDismissibleAndCanOpenOfflineHelp() {
        val opened = java.util.concurrent.atomic.AtomicBoolean()
        compose.runOnUiThread { compose.activity.setContent {
            SpatialTheme(dark = false) {
                Column {
                    Text("Total", Modifier.testTag("metric-title"))
                    ContextualHelp("Total", "Recognized validated sales", french = false,
                        onOpenHelp = { opened.set(true) }, testTag = "metric-help")
                }
            }
        } }
        compose.onNodeWithTag("metric-help-action").assertContentDescriptionEquals("Information about Total")
            .assertExists().performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused().performClick()
        compose.onNodeWithTag("metric-help-details").assertExists()
        compose.onNodeWithTag("metric-help-explanation").assertTextContains("Recognized validated sales")
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("metric-help-details").assertDoesNotExist()
        compose.onNodeWithTag("metric-help-action").performClick()
        compose.onNodeWithTag("metric-help-open-help").performClick()
        assertEquals(true, opened.get())
        compose.onNodeWithTag("metric-help-details").assertDoesNotExist()
    }

    @Test fun frenchAndEnglishResourceVariantsAreAvailableOffline() {
        val context = compose.activity
        val id = com.vibe.store.presentation.R.string.help_article_reports_title
        val french = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.FRENCH)
        }).resources.getString(id)
        val english = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.ENGLISH)
        }).resources.getString(id)
        val frenchMetric = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.FRENCH)
        }).resources.getString(com.vibe.store.presentation.R.string.help_kpi_revenue_body)
        val englishMetric = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.ENGLISH)
        }).resources.getString(com.vibe.store.presentation.R.string.help_kpi_revenue_body)
        assertEquals("Comprendre les rapports", french)
        assertEquals("Understanding reports", english)
        assert(frenchMetric.contains("Somme des revenus reconnus"))
        assert(englishMetric.contains("Sum of recognized revenue"))
    }

    @Test fun disclosureUsesDarkThemeAndRemainsDismissible() {
        compose.runOnUiThread { compose.activity.setContent { SpatialTheme(dark = true) {
            ContextualHelp("Stock", "Current catalog stock", french = true, testTag = "dark-help")
        } } }
        compose.onNodeWithTag("dark-help-action").assertContentDescriptionEquals("Informations sur Stock").performClick()
        compose.onNodeWithTag("dark-help-explanation").assertTextContains("Current catalog stock")
        compose.onNodeWithText("Fermer").performClick()
        compose.onNodeWithTag("dark-help-details").assertDoesNotExist()
    }
}
