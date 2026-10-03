package com.vibe.store.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Navigation is presentation only. Each destination rechecks authority in its service. */
private data class StoreDestination(val route: String, val fr: String, val en: String) {
    val tag: String get() = when (route) {
        "catalog" -> "open-catalog"
        "employees" -> "open-team"
        "today" -> "open-presence"
        else -> "nav-$route"
    }
}

private fun availableDestinations(permissions: Set<String>, hasCatalog: Boolean, hasTeam: Boolean): List<StoreDestination> =
    buildList {
        add(StoreDestination("home", "Accueil", "Home"))
        if (hasCatalog && "PRODUCTS:READ" in permissions) add(StoreDestination("catalog", "Catalogue", "Catalog"))
        if (hasTeam && "EMPLOYEES:READ" in permissions) add(StoreDestination("employees", "Équipe", "Team"))
        if (hasTeam && "PRESENCE:READ" in permissions) add(StoreDestination("today", "Présences", "Attendance"))
        if ("SETTINGS:READ" in permissions) add(StoreDestination("settings", "Réglages", "Settings"))
    }

/** All navigation targets are filtered by capabilities; no authorization is done in the UI. */
@Composable
fun StoreNavigationBar(
    page: String, permissions: Set<String>, hasCatalog: Boolean, hasTeam: Boolean,
    french: Boolean, enabled: Boolean, onNavigate: (String) -> Unit,
) {
    NavigationBar(Modifier.testTag("store-navigation-bottom")) {
        availableDestinations(permissions, hasCatalog, hasTeam).forEach { destination ->
            NavigationBarItem(
                modifier = Modifier.testTag(destination.tag),
                selected = page == destination.route,
                enabled = enabled,
                onClick = { if (page != destination.route) onNavigate(destination.route) },
                icon = { StoreDestinationIcon(destination.route, page == destination.route) },
                label = { Text(if (french) destination.fr else destination.en, maxLines = 1) },
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
fun StoreNavigationRail(
    page: String, permissions: Set<String>, hasCatalog: Boolean, hasTeam: Boolean,
    french: Boolean, enabled: Boolean, onNavigate: (String) -> Unit,
) {
    NavigationRail(Modifier.safeDrawingPadding().testTag("store-navigation-rail")) {
        Spacer(Modifier.height(12.dp))
        availableDestinations(permissions, hasCatalog, hasTeam).forEach { destination ->
            NavigationRailItem(
                modifier = Modifier.testTag(destination.tag),
                selected = page == destination.route,
                enabled = enabled,
                onClick = { if (page != destination.route) onNavigate(destination.route) },
                icon = { StoreDestinationIcon(destination.route, page == destination.route) },
                label = { Text(if (french) destination.fr else destination.en, maxLines = 1) },
                alwaysShowLabel = true,
            )
        }
    }
}

/** Small vector icons avoid dependency on an additional icon package or platform fonts. */
@Composable
private fun StoreDestinationIcon(route: String, selected: Boolean) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(24.dp)) {
        val u = size.minDimension / 24f
        val line = Stroke(width = 1.8f * u)
        fun point(x: Int, y: Int) = Offset(x * u, y * u)
        when (route) {
            "home" -> {
                val roof = Path().apply {
                    moveTo(3f * u, 11f * u); lineTo(12f * u, 3f * u); lineTo(21f * u, 11f * u)
                }
                drawPath(roof, color, style = line)
                drawLine(color, point(5, 10), point(5, 21), strokeWidth = line.width)
                drawLine(color, point(19, 10), point(19, 21), strokeWidth = line.width)
                drawLine(color, point(5, 21), point(19, 21), strokeWidth = line.width)
                drawLine(color, point(10, 21), point(10, 15), strokeWidth = line.width)
                drawLine(color, point(14, 21), point(14, 15), strokeWidth = line.width)
                drawLine(color, point(10, 15), point(14, 15), strokeWidth = line.width)
            }
            "catalog" -> {
                for (x in listOf(3, 13)) for (y in listOf(3, 13)) {
                    drawRect(color, topLeft = point(x, y), size = androidx.compose.ui.geometry.Size(8f * u, 8f * u), style = line)
                }
            }
            "employees" -> {
                drawCircle(color, 3f * u, point(9, 8), style = line)
                drawCircle(color, 2.5f * u, point(17, 9), style = line)
                drawArc(color, 180f, 180f, false, point(2, 12), androidx.compose.ui.geometry.Size(14f * u, 10f * u), style = line)
                drawArc(color, 185f, 150f, false, point(12, 13), androidx.compose.ui.geometry.Size(10f * u, 8f * u), style = line)
            }
            "today" -> {
                drawCircle(color, 9f * u, point(12, 12), style = line)
                drawLine(color, point(12, 12), point(12, 6), strokeWidth = line.width)
                drawLine(color, point(12, 12), point(17, 15), strokeWidth = line.width)
            }
            "settings" -> {
                for (y in listOf(6, 12, 18)) {
                    drawLine(color, point(3, y), point(21, y), strokeWidth = line.width)
                }
                drawCircle(color, 2f * u, point(9, 6), style = line)
                drawCircle(color, 2f * u, point(16, 12), style = line)
                drawCircle(color, 2f * u, point(9, 18), style = line)
            }
        }
    }
}
