package com.vibe.store
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import com.vibe.store.presentation.R as UiR

class FoundationSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun adaptiveNativeSmokeAndTypedReturn() {
        val expected = InstrumentationRegistry.getArguments().getString("expectedWidth")
            ?: error("expectedWidth COMPACT/MEDIUM/EXPANDED required")
        compose.onNodeWithTag("foundation-title").assertIsDisplayed()
        compose.onNodeWithTag("brand").assertTextEquals("STORE · 57€|2£!/v9").assertIsDisplayed()
        compose.onNodeWithTag("window-class").assertTextEquals(expected)
        compose.onNodeWithTag("storage-adapter").assertTextEquals("Room / BundledSQLiteDriver")
        compose.onNodeWithTag("about").performClick()
        compose.onNodeWithTag("about-title").assertIsDisplayed()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("foundation-title").assertIsDisplayed()
    }
    @Test fun frenchAndEnglishResourcesExist() {
        fun title(language: String): String {
            val config = Configuration(compose.activity.resources.configuration)
            config.setLocale(Locale.forLanguageTag(language))
            return compose.activity.createConfigurationContext(config).getString(UiR.string.foundation_title)
        }
        assertEquals("Native foundation", title("en"))
        assertEquals("Fondation native", title("fr"))
    }
    @Test fun manifestAndNoDatabase() {
        val context = compose.activity
        assertEquals("com.vibe.store.debug", context.packageName)
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertTrue(context.databaseList().isEmpty())
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertNotNull(intent)
    }
}
