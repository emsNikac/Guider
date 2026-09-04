package com.nikac.guider.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

val NavigationPillListBottomPadding = 132.dp

fun LazyListScope.navigationPillItem(
    itemKey: Any,
    content: @Composable () -> Unit,
) {
    item(key = itemKey) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

/**
 * Fades only the narrow strip of scrolling content that reaches the navigation pill.
 * Drawing a background scrim avoids allocating a full-screen off-screen compositing layer.
 */
@Composable
fun Modifier.navigationPillScrollEffect(): Modifier {
    val density = LocalDensity.current
    val backgroundColor = MaterialTheme.colorScheme.background
    val navigationBarHeight = WindowInsets.navigationBars.getBottom(density).toFloat()
    val pillHeight = with(density) { 64.dp.toPx() }
    val pillBottomMargin = with(density) { 12.dp.toPx() }
    val fadeLead = with(density) { 22.dp.toPx() }

    return this.drawWithCache {
        val viewportHeight = size.height.coerceAtLeast(1f)
        val pillBottom = viewportHeight - navigationBarHeight - pillBottomMargin
        val fadeStart = (pillBottom - pillHeight - fadeLead).coerceIn(0f, viewportHeight)
        val fadeEnd = pillBottom.coerceIn(fadeStart, viewportHeight)
        val scrim = Brush.verticalGradient(
            colors = listOf(
                backgroundColor.copy(alpha = 0f),
                backgroundColor,
            ),
            startY = fadeStart,
            endY = fadeEnd,
        )

        onDrawWithContent {
            drawContent()
            drawRect(
                brush = scrim,
                topLeft = Offset(x = 0f, y = fadeStart),
                size = Size(
                    width = size.width,
                    height = viewportHeight - fadeStart,
                ),
            )
        }
    }
}
