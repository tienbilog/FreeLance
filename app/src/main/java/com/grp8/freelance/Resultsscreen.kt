package com.grp8.freelance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grp8.freelance.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Shows the computed schedule: accepted projects by day, plus any dropped projects. */
@Composable
fun ResultsScreen(
    result: ScheduleResult,
    onBack: () -> Unit,
    onEditProject: (Project) -> Unit
) {
    Scaffold(
        containerColor = White,
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    Text("Schedule Results",
                        style = MaterialTheme.typography.titleMedium, color = Ink)
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Back", color = AccentBlue, fontFamily = InterFamily)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = White)
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Total income", "₱${result.totalIncome.fmt()}",
                        AccentBlueSoft, AccentBlue, Modifier.weight(1f))
                    MetricCard("Accepted", "${result.accepted.size}", SlateCard, Ink,
                        Modifier.weight(1f))
                    MetricCard("Dropped", "${result.dropped.size}",
                        if (result.dropped.isEmpty()) SlateCard else Color(0xFFFFE5E5),
                        if (result.dropped.isEmpty()) Ink else AccentRed,
                        Modifier.weight(1f))
                }
            }

            item {
                Text("Schedule", style = MaterialTheme.typography.titleMedium, color = Ink)
            }

            val byDate = result.accepted.groupBy { it.assignedDate }
            items(byDate.keys.sorted()) { date ->
                ScheduleDayCard(date = date, items = byDate[date] ?: emptyList())
            }

            if (result.dropped.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("Dropped Projects",
                        style = MaterialTheme.typography.titleMedium, color = Ink)
                }
                items(result.dropped) { dropped ->
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
                            Text("✗", color = AccentRed, fontSize = 16.sp,
                                modifier = Modifier.padding(end = 10.dp, top = 2.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dropped.project.name,
                                    style = MaterialTheme.typography.titleMedium, color = Ink)
                                Text(
                                    "${dropped.project.clientName} · due ${dropped.project.deadlineDate.format(DATE_FMT)}",
                                    style = MaterialTheme.typography.bodySmall, color = SlateDeep
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(dropped.reason,
                                    style = MaterialTheme.typography.bodySmall, color = AccentRed)

                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { onEditProject(dropped.project) },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, AccentRed),
                                    contentPadding = PaddingValues(
                                        horizontal = 12.dp, vertical = 4.dp
                                    ),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = AccentRed,
                                        modifier = Modifier
                                            .size(13.dp)
                                            .padding(end = 4.dp)
                                    )
                                    Text(
                                        "Edit project",
                                        color = AccentRed,
                                        fontFamily = InterFamily,
                                        fontSize = 12.sp
                                    )
                                }
                            }
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
fun ScheduleDayCard(date: LocalDate, items: List<ScheduledProject>) {
    val totalHours  = items.sumOf { it.project.hoursNeeded }
    val totalIncome = items.sumOf { it.project.totalIncome }

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

            items.forEach { sp ->
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
                        Text("₱${sp.project.totalIncome.fmt()}",
                            fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp, color = Ink)
                        Text("${sp.project.hoursNeeded.fmt()}h @ ₱${sp.project.ratePerHour}/hr",
                            style = MaterialTheme.typography.bodySmall, color = SlateDeep)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}