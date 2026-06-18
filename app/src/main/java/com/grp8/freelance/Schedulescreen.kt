package com.grp8.freelance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grp8.freelance.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * PHASE 2 — Schedule.
 *
 * Runs the backtracking optimizer over Phase 1's potential projects and shows
 * a preview: which jobs would be accepted and when, which ones can't be
 * accepted (and why), and the total income that combination would earn.
 * Nothing is final until the user taps "Accept this schedule" — at which
 * point accepted projects move to the To-Do tab and become real commitments.
 */
@Composable
fun ScheduleScreen(
    viewModel: SchedulerViewModel,
    onAccepted: () -> Unit
) {
    val allProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val potential = allProjects.filter { it.status == ProjectStatus.POTENTIAL }
    val suggested by viewModel.suggested.collectAsStateWithLifecycle()

    Scaffold(containerColor = White) { padding ->
        if (suggested == null) {
            // ---- Empty / pre-run state ----
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Suggested Schedule", style = MaterialTheme.typography.displayLarge, color = Ink)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (potential.isEmpty())
                        "Add some potential projects in the Optimizer tab first."
                    else
                        "Run the optimizer to see which of your ${potential.size} potential " +
                                "project(s) fit your schedule, and which don't.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateDeep,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.runOptimizer() },
                    enabled = potential.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    modifier = Modifier.height(52.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Recalculate Schedule", fontFamily = InterFamily, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            val result = suggested!!
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp,
                    top = padding.calculateTopPadding() + 20.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text("Suggested Schedule", style = MaterialTheme.typography.displayLarge, color = Ink)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("Total income", "₱${result.totalIncome.fmt()}",
                            AccentBlueSoft, AccentBlue, Modifier.weight(1f))
                        MetricCard("Can accept", "${result.accepted.size}", SlateCard, Ink,
                            Modifier.weight(1f))
                        MetricCard("Can't accept", "${result.unscheduled.size}",
                            if (result.unscheduled.isEmpty()) SlateCard else Color(0xFFFFE5E5),
                            if (result.unscheduled.isEmpty()) Ink else AccentRed,
                            Modifier.weight(1f))
                    }
                }

                if (result.accepted.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text("Proposed Schedule", style = MaterialTheme.typography.titleMedium, color = Ink)
                    }
                    val flattened = result.accepted.flatMap { sp ->
                        sp.assignments.map { (date, hours) ->
                            date to Pair(sp, hours)
                        }
                    }
                    val byDate = flattened.groupBy({ it.first }, { it.second })
                    items(byDate.keys.sorted()) { date ->
                        ScheduleDayCard(date = date, items = byDate[date] ?: emptyList())
                    }
                }

                if (result.unscheduled.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text("Can't Accept These", style = MaterialTheme.typography.titleMedium, color = Ink)
                    }
                    items(result.unscheduled) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE5E5)),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text("⚠", color = AccentRed, fontSize = 16.sp,
                                    modifier = Modifier.padding(end = 10.dp, top = 2.dp))
                                Column {
                                    Text(item.project.name,
                                        style = MaterialTheme.typography.titleMedium, color = Ink)
                                    Text(
                                        "${item.project.clientName} · due ${item.project.deadlineDate.format(DATE_FMT)}",
                                        style = MaterialTheme.typography.bodySmall, color = SlateDeep
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(item.constraint,
                                        style = MaterialTheme.typography.bodySmall, color = AccentRed)
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.discardSuggestion() },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Discard", fontFamily = InterFamily)
                        }
                        Button(
                            onClick = {
                                viewModel.acceptSchedule()
                                onAccepted()
                            },
                            enabled = result.accepted.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) {
                            Text("Accept Schedule", fontFamily = InterFamily, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(label: String, value: String, bg: Color, valueColor: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = SlateDeep)
            Spacer(Modifier.height(4.dp))
            Text(value, fontFamily = InterFamily, fontWeight = FontWeight.Bold,
                fontSize = 17.sp, color = valueColor)
        }
    }
}

@Composable
fun ScheduleDayCard(date: LocalDate, items: List<Pair<ScheduledProject, Double>>) {
    val totalHours  = items.sumOf { it.second }
    val totalIncome = items.sumOf { (it.second / it.first.project.hoursNeeded) * it.first.project.totalIncome }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        date.format(DateTimeFormatter.ofPattern("EEEE")),
                        style = MaterialTheme.typography.titleMedium, color = Ink
                    )
                    Text(
                        date.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                        style = MaterialTheme.typography.bodySmall, color = SlateDeep
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("₱${totalIncome.fmt()}", fontFamily = InterFamily,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AccentBlue)
                    Text("${totalHours.fmt()}h total",
                        style = MaterialTheme.typography.bodySmall, color = SlateDeep)
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = SlateMid, thickness = 0.5.dp)
            Spacer(Modifier.height(10.dp))

            items.forEach { (sp, hoursForDay) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(White)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sp.project.name, style = MaterialTheme.typography.bodyLarge,
                            color = Ink, fontWeight = FontWeight.Medium)
                        Text(sp.project.clientName,
                            style = MaterialTheme.typography.bodySmall, color = SlateDeep)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val incomeForDay = (hoursForDay / sp.project.hoursNeeded) * sp.project.totalIncome
                        Text("₱${incomeForDay.fmt()}",
                            fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp, color = Ink)
                        Text(
                            "${hoursForDay.fmt()}h" +
                                    if (sp.project.rateType == RateType.HOURLY)
                                        " @ ₱${sp.project.ratePerHour.fmt()}/hr" else " · fixed",
                            style = MaterialTheme.typography.bodySmall, color = SlateDeep
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}