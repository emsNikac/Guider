package com.example.guider.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.example.guider.ui.GuiderDestination

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
internal fun GuiderBottomBar(
    selectedDestination: GuiderDestination,
    onDestinationSelected: (GuiderDestination) -> Unit,
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(4.dp)
                .drawWithCache {
                    val cornerRadius = 30.dp.toPx()
                    val scrim = Brush.verticalGradient(
                        colors = listOf(
                            backgroundColor.copy(alpha = 0.38f),
                            backgroundColor.copy(alpha = 0.78f),
                        ),
                    )
                    onDrawBehind {
                        drawRoundRect(
                            brush = scrim,
                            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                        )
                    }
                },
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 8.dp,
            tonalElevation = 2.dp,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                val destinations = GuiderDestination.entries
                val itemWidth = maxWidth / destinations.size
                val selectedIndex = destinations.indexOf(selectedDestination)
                val indicatorOffset = animateDpAsState(
                    targetValue = itemWidth * selectedIndex,
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                    label = "Bottom navigation position",
                )

                Box(
                    modifier = Modifier
                        .offset { IntOffset(indicatorOffset.value.roundToPx(), 0) }
                        .width(itemWidth)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    destinations.forEach { destination ->
                        BottomBarItem(
                            destination = destination,
                            selected = destination == selectedDestination,
                            onClick = { onDestinationSelected(destination) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    destination: GuiderDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(
                role = Role.Tab,
                onClickLabel = "Open ${destination.label}",
                onClick = onClick,
            )
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(destination.iconRes),
            contentDescription = destination.label,
            modifier = Modifier.size(25.dp),
            tint = iconColor,
        )
    }
}
