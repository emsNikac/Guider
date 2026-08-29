package com.example.guider.ui.screens.money

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.guider.domain.money.MoneyCalculations
import com.example.guider.domain.money.Spending
import com.example.guider.domain.time.DayKeys
import com.example.guider.ui.components.NavigationPillListBottomPadding
import com.example.guider.ui.components.navigationPillScrollEffect
import com.example.guider.R
import com.example.guider.util.LocalizedFormatters

@Composable
fun MoneyRoute(
    modifier: Modifier = Modifier,
    viewModel: MoneyViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MoneyScreen(
        uiState = uiState,
        onAddSpending = viewModel::addSpending,
        onEditSpending = viewModel::editSpending,
        onDeleteSpending = viewModel::deleteSpending,
        onRestart = viewModel::restart,
        modifier = modifier,
    )
}

@Composable
private fun MoneyScreen(
    uiState: MoneyUiState,
    onAddSpending: (String, Long) -> Unit,
    onEditSpending: (Long, String, Long) -> Unit,
    onDeleteSpending: (Long) -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ledger = uiState.ledger
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var spendingBeingEdited by remember { mutableStateOf<Spending?>(null) }
    var spendingPendingDeletion by remember { mutableStateOf<Spending?>(null) }
    var showRestartDialog by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .navigationPillScrollEffect(),
        contentPadding = PaddingValues(
            start = 24.dp,
            top = 22.dp,
            end = 24.dp,
            bottom = NavigationPillListBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = MONEY_HEADER_KEY) {
            MoneyHeader(onAddSpending = { showAddDialog = true })
        }
        item(key = MONEY_TOTAL_KEY) {
            TotalSpentCard(
                totalMinor = uiState.totalMinor,
                periodStartDayKey = ledger.periodStartDayKey,
            )
        }
        item(key = MONEY_HISTORY_HEADER_KEY) {
            SpendingHistoryHeader(
                spendingCount = ledger.spendings.size,
                restartEnabled = ledger.periodStartDayKey != null,
                onRestart = { showRestartDialog = true },
            )
        }
        if (ledger.spendings.isEmpty()) {
            item(key = MONEY_EMPTY_KEY) {
                EmptySpendingCard(onAddSpending = { showAddDialog = true })
            }
        } else {
            items(
                items = uiState.sortedSpendings,
                key = Spending::id,
                contentType = { MONEY_SPENDING_CONTENT_TYPE },
            ) { spending ->
                SpendingCard(
                    spending = spending,
                    onEdit = { spendingBeingEdited = spending },
                    onDelete = { spendingPendingDeletion = spending },
                )
            }
        }
    }

    if (showAddDialog) {
        SpendingEditorDialog(
            spending = null,
            onDismiss = { showAddDialog = false },
            onSave = { title, amountMinor ->
                onAddSpending(title, amountMinor)
                showAddDialog = false
            },
        )
    }

    spendingBeingEdited?.let { spending ->
        SpendingEditorDialog(
            spending = spending,
            onDismiss = { spendingBeingEdited = null },
            onSave = { title, amountMinor ->
                onEditSpending(spending.id, title, amountMinor)
                spendingBeingEdited = null
            },
        )
    }

    spendingPendingDeletion?.let { spending ->
        DeleteSpendingDialog(
            spending = spending,
            onDismiss = { spendingPendingDeletion = null },
            onDelete = {
                onDeleteSpending(spending.id)
                spendingPendingDeletion = null
            },
        )
    }

    if (showRestartDialog) {
        RestartSpendingDialog(
            onDismiss = { showRestartDialog = false },
            onRestart = {
                onRestart()
                showRestartDialog = false
            },
        )
    }
}

@Composable
private fun MoneyHeader(onAddSpending: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Money management",
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "Track what you spend, not what you own.",
            modifier = Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onAddSpending,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 10.dp),
        ) {
            Text("Add spending")
        }
    }
}

@Composable
private fun TotalSpentCard(
    totalMinor: Long,
    periodStartDayKey: Int?,
) {
    val periodLabel = remember(periodStartDayKey) {
        periodStartDayKey?.let { "Money spent since ${formatDayKey(it)}" }
            ?: "Your tracking period starts with the first spending."
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("Total spent", style = MaterialTheme.typography.labelLarge)
            Text(
                text = formatMoney(totalMinor),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = periodLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun SpendingHistoryHeader(
    spendingCount: Int,
    restartEnabled: Boolean,
    onRestart: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Spending history", style = MaterialTheme.typography.titleLarge)
            Text(
                text = if (spendingCount == 1) {
                    "1 documented transaction"
                } else {
                    "$spendingCount documented transactions"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onRestart,
            enabled = restartEnabled,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text("Restart total")
        }
    }
}

@Composable
private fun SpendingCard(
    spending: Spending,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = spending.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatSpendingDate(spending.createdAtEpochMillis)} · Tap to edit",
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatMoney(spending.amountMinor),
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.delete_ic),
                    contentDescription = "Delete ${spending.title}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DeleteSpendingDialog(
    spending: Spending,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete spending?") },
        text = {
            Text(
                text = "Delete “${spending.title}” (${formatMoney(spending.amountMinor)}) from the total?",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun EmptySpendingCard(onAddSpending: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("No spending documented", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Add a purchase to begin your total.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAddSpending) { Text("Add first spending") }
        }
    }
}

@Composable
private fun SpendingEditorDialog(
    spending: Spending?,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit,
) {
    var title by rememberSaveable(spending?.id) {
        mutableStateOf(spending?.title.orEmpty())
    }
    var amountInput by rememberSaveable(spending?.id) {
        mutableStateOf(spending?.let { MoneyCalculations.minorToInput(it.amountMinor) }.orEmpty())
    }
    val amountMinor = remember(amountInput) {
        MoneyCalculations.parseAmountToMinor(amountInput)
    }
    val localizedCurrencySymbol = remember { currencySymbol() }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (spending == null) "Add spending" else "Edit spending") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Purchase or spending title") },
                    placeholder = { Text("Lunch") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Amount") },
                    prefix = { Text(localizedCurrencySymbol) },
                    supportingText = {
                        if (amountInput.isNotBlank() && amountMinor == null) {
                            Text("Enter an amount greater than zero with up to 2 decimals.")
                        }
                    },
                    isError = amountInput.isNotBlank() && amountMinor == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { amountMinor?.let { onSave(title.trim(), it) } },
                enabled = title.isNotBlank() && amountMinor != null,
            ) {
                Text(if (spending == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun RestartSpendingDialog(
    onDismiss: () -> Unit,
    onRestart: () -> Unit,
) {
    var confirmationArmed by rememberSaveable { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Restart spending total?", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "This clears the documented spending history, resets the total to zero, and starts a new tracking period today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    if (confirmationArmed) {
                        Button(
                            onClick = onRestart,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Text("Are you sure?")
                        }
                    } else {
                        TextButton(
                            onClick = { confirmationArmed = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Confirm restart")
                        }
                    }
                }
            }
        }
    }
}

private fun formatMoney(amountMinor: Long): String =
    LocalizedFormatters.formatCurrency(amountMinor)

private fun currencySymbol(): String =
    LocalizedFormatters.currencySymbol()

private fun formatDayKey(dayKey: Int): String =
    LocalizedFormatters.formatDate("MMMM d, yyyy", DayKeys.toEpochMillis(dayKey))

private fun formatSpendingDate(epochMillis: Long): String =
    LocalizedFormatters.formatDate("MMM d, yyyy", epochMillis)

private const val MONEY_HEADER_KEY = "money_header"
private const val MONEY_TOTAL_KEY = "money_total"
private const val MONEY_HISTORY_HEADER_KEY = "money_history_header"
private const val MONEY_EMPTY_KEY = "money_empty"
private const val MONEY_SPENDING_CONTENT_TYPE = "money_spending"
