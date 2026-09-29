package com.vibe.store.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Small shared foundation; no business state or extra rendering layer. */
object SpatialTokens {
    val compactInset = 20.dp
    val wideInset = 32.dp
    val gap = 20.dp
    val panelInset = 24.dp
    val panelRadius = 28.dp
    val touchTarget = 48.dp
    const val transitionMillis = 160
}

private val Light = lightColorScheme(
    primary = Color(0xFF245B57), onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EEE9), onPrimaryContainer = Color(0xFF153D39),
    background = Color(0xFFEDF2F1), onBackground = Color(0xFF172421),
    surface = Color(0xFFF9FCFB), onSurface = Color(0xFF172421),
    surfaceVariant = Color(0xFFDFE9E5), onSurfaceVariant = Color(0xFF435650),
    outlineVariant = Color(0xFFBDCEC7)
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA3D4C8), onPrimary = Color(0xFF103B32),
    primaryContainer = Color(0xFF294B43), onPrimaryContainer = Color(0xFFD7EEE9),
    background = Color(0xFF101B18), onBackground = Color(0xFFE1ECE6),
    surface = Color(0xFF1B2924), onSurface = Color(0xFFE1ECE6),
    surfaceVariant = Color(0xFF2B3C34), onSurfaceVariant = Color(0xFFC0CFC5),
    outlineVariant = Color(0xFF4C6055)
)

@Composable
fun SpatialTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(SpatialTokens.panelRadius)
        ),
        typography = Typography(), content = content
    )
}

@Composable
fun SpatialPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(SpatialTokens.panelInset), content = content)
    }
}
