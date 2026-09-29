package com.vibe.store.presentation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowWidthSizeClass
import com.vibe.store.api.FoundationService
import com.vibe.store.api.FoundationSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable private data object Foundation
@Serializable private data object About

class FoundationViewModel(service: FoundationService) : ViewModel() {
    private val mutable = MutableStateFlow(service.inspect())
    val state = mutable.asStateFlow()
}
private class FoundationFactory(private val service: FoundationService) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == FoundationViewModel::class.java)
        @Suppress("UNCHECKED_CAST")
        return FoundationViewModel(service) as T
    }
}

@Composable
fun FoundationApp(service: FoundationService) {
    val model: FoundationViewModel = viewModel(factory = remember(service) { FoundationFactory(service) })
    val state by model.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val width = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    val widthLabel = when (width) {
        WindowWidthSizeClass.COMPACT -> "COMPACT"
        WindowWidthSizeClass.MEDIUM -> "MEDIUM"
        else -> "EXPANDED"
    }
    SpatialTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // Compose uses the system animator duration scale, including zero.
            NavHost(navController = nav, startDestination = Foundation,
                enterTransition = { fadeIn(tween(SpatialTokens.transitionMillis)) },
                exitTransition = { fadeOut(tween(SpatialTokens.transitionMillis)) },
                popEnterTransition = { fadeIn(tween(SpatialTokens.transitionMillis)) },
                popExitTransition = { fadeOut(tween(SpatialTokens.transitionMillis)) }
            ) {
                composable<Foundation> {
                    SmokeContent(state, widthLabel, false) { nav.navigate(About) { launchSingleTop = true } }
                }
                composable<About> { SmokeContent(state, widthLabel, true) { nav.popBackStack() } }
            }
        }
    }
}

@Composable
private fun SmokeContent(state: FoundationSnapshot, width: String, about: Boolean, navigate: () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = if (width == "EXPANDED") 1120.dp else 760.dp)
                .fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(if (width == "COMPACT") SpatialTokens.compactInset else SpatialTokens.wideInset),
            verticalArrangement = Arrangement.spacedBy(SpatialTokens.gap)
        ) {
            Text(stringResource(R.string.brand), Modifier.testTag("brand"),
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(if (about) R.string.about_title else R.string.foundation_title),
                Modifier.testTag(if (about) "about-title" else "foundation-title"),
                style = MaterialTheme.typography.headlineMedium)
            if (width == "EXPANDED") {
                Row(horizontalArrangement = Arrangement.spacedBy(SpatialTokens.gap)) {
                    FoundationSummary(width, Modifier.weight(1f))
                    StoragePanel(state, Modifier.weight(1.25f))
                }
            } else {
                FoundationSummary(width, Modifier.fillMaxWidth())
                StoragePanel(state, Modifier.fillMaxWidth())
            }
            if (about) Text(stringResource(R.string.about_detail))
            // Material buttons retain standard focus, ripple, pressed and disabled states.
            Button(onClick = navigate, modifier = Modifier.heightIn(min = SpatialTokens.touchTarget).testTag(if (about) "back" else "about")) {
                Text(stringResource(if (about) R.string.back else R.string.about_action))
            }
        }
    }
}

@Composable
private fun FoundationSummary(width: String, modifier: Modifier) {
    SpatialPanel(modifier) {
        Text(stringResource(R.string.layout_label), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
            Text(width, Modifier.padding(horizontal = 16.dp, vertical = 10.dp).testTag("window-class"),
                style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.foundation_notice), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun StoragePanel(state: FoundationSnapshot, modifier: Modifier) {
    SpatialPanel(modifier) {
        Text(stringResource(R.string.storage_label), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(state.storageAdapter, Modifier.testTag("storage-adapter"), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.no_database), Modifier.testTag("no-database"),
            style = MaterialTheme.typography.bodyLarge)
    }
}
