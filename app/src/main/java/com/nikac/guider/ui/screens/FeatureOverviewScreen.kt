package com.nikac.guider.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nikac.guider.ui.components.NavigationPillListBottomPadding
import com.nikac.guider.ui.components.navigationPillItem
import com.nikac.guider.ui.components.navigationPillScrollEffect
import com.nikac.guider.domain.collections.ImmutableListSnapshot

@Composable
fun FeatureOverviewScreen(
    title: String,
    subtitle: String,
    cardTitle: String,
    cardBody: String,
    features: ImmutableListSnapshot<String>,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .navigationPillScrollEffect(),
        contentPadding = PaddingValues(
            start = 24.dp,
            top = 28.dp,
            end = 24.dp,
            bottom = NavigationPillListBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        navigationPillItem("${title}_header") {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        navigationPillItem("${title}_primary_card") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Text(
                        text = cardTitle,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = cardBody,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        navigationPillItem("${title}_features_header") {
            Text(
                text = "Planned foundation",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        itemsIndexed(
            items = features,
            key = { index, feature -> "${title}_feature_${index}_$feature" },
            contentType = { _, _ -> FEATURE_ROW_CONTENT_TYPE },
        ) { _, feature ->
            FeatureRow(feature)
        }

        navigationPillItem("${title}_footer") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    text = "This tab is ready for its feature build in the next steps.",
                    modifier = Modifier.padding(18.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val FEATURE_ROW_CONTENT_TYPE = "feature_row"

@Composable
private fun FeatureRow(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            Text(
                text = text,
                modifier = Modifier.padding(start = 14.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
