package com.grp8.freelance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grp8.freelance.ui.theme.*
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * PHASE 4 — Income.
 *
 * The moment a to-do item is checked off as done, its earnings land here.
 * Shows a month-by-month breakdown of completed projects and what they paid,
 * plus the running total earned across all time.
 */
@Composable
fun IncomeScreen(viewModel: SchedulerViewModel) {
    val allProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val done = allProjects.filter { it.status == ProjectStatus.DONE }
    val totalEverEarned = done.sumOf { it.totalIncome }

    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    val summary = viewModel.incomeForMonth(selectedMonth)
    val availableMonths = viewModel.availableIncomeMonths()

    Scaffold(containerColor = White) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = 24.dp, bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("Income", style = MaterialTheme.typography.displayLarge, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Earnings from completed projects.",
                    style = MaterialTheme.typography.bodyMedium, color = SlateDeep
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AccentBlueSoft),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Total earned (all time)",
                            style = MaterialTheme.typography.bodySmall, color = AccentBlue)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "₱${totalEverEarned.money()}",
                            fontFamily = InterFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            color = AccentBlue
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${done.size} project${if (done.size == 1) "" else "s"} completed",
                            style = MaterialTheme.typography.bodySmall, color = AccentBlue
                        )
                    }
                }
            }

            item {
                // ---- Month switcher ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = Ink)
                    }
                    Text(
                        "${selectedMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${selectedMonth.year}",
                        style = MaterialTheme.typography.titleMedium, color = Ink
                    )
                    IconButton(
                        onClick = { selectedMonth = selectedMonth.plusMonths(1) },
                        enabled = selectedMonth < YearMonth.now()
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Next month",
                            tint = if (selectedMonth < YearMonth.now()) Ink else SlateMid
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("This month's total",
                            style = MaterialTheme.typography.bodyMedium, color = SlateDeep)
                        Text(
                            "₱${summary.totalIncome.money()}",
                            fontFamily = InterFamily, fontWeight = FontWeight.Bold,
                            fontSize = 16.sp, color = AccentGreen
                        )
                    }
                }
            }

            if (summary.entries.isEmpty()) {
                item {
                    Text(
                        if (availableMonths.isEmpty())
                            "No completed projects yet. Finish a to-do item to see it here."
                        else
                            "No completed projects in this month.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateDeep
                    )
                }
            } else {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("Completed Projects", style = MaterialTheme.typography.titleMedium, color = Ink)
                }
                items(summary.entries, key = { it.project.id }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateMid),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(entry.project.name,
                                    style = MaterialTheme.typography.titleMedium, color = Ink)
                                Text(
                                    "${entry.project.clientName} · completed ${entry.completedDate.format(DATE_FMT)}",
                                    style = MaterialTheme.typography.bodySmall, color = SlateDeep
                                )
                            }
                            Text(
                                "₱${entry.amount.money()}",
                                fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp, color = AccentGreen
                            )
                        }
                    }
                }
            }

            item {
                var showDeleteAllDialog by remember { mutableStateOf(false) }
                if (done.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    TextButton(
                        onClick = { showDeleteAllDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete All Income Records", color = AccentRed, fontFamily = InterFamily, fontWeight = FontWeight.Medium)
                    }
                }
                if (showDeleteAllDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteAllDialog = false },
                        title = { Text("Delete all income records?") },
                        text = { Text("This action cannot be undone. All income data will be permanently removed.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showDeleteAllDialog = false
                                viewModel.deleteAllIncomeRecords()
                            }) { Text("Delete", color = AccentRed) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel", color = SlateDeep) }
                        }
                    )
                }
            }
        }
    }
}