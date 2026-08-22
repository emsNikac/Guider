package com.example.guider.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
 * Applying the mask to the viewport avoids dimming an entire tall list item at once.
 */
@Composable
fun Modifier.navigationPillScrollEffect(): Modifier {
    val density = LocalDensity.current
    val navigationBarHeight = WindowInsets.navigationBars.getBottom(density).toFloat()
    val pillHeight = with(density) { 64.dp.toPx() }
    val pillBottomMargin = with(density) { 12.dp.toPx() }
    val fadeLead = with(density) { 22.dp.toPx() }

    return this
        .graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }
        .drawWithCache {
            val viewportHeight = size.height.coerceAtLeast(1f)
            val pillBottom = viewportHeight - navigationBarHeight - pillBottomMargin
            val fadeStart = (pillBottom - pillHeight - fadeLead).coerceIn(0f, viewportHeight)
            val fadeEnd = pillBottom.coerceIn(fadeStart, viewportHeight)
            val startFraction = fadeStart / viewportHeight
            val endFraction = fadeEnd / viewportHeight
            val mask = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Black,
                    startFraction to Color.Black,
                    endFraction to Color.Transparent,
                    1f to Color.Transparent,
                ),
            )

            onDrawWithContent {
                drawContent()
                drawRect(
                    brush = mask,
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}
