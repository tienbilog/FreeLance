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
                    MetricCard("Can't Accept", "${result.unscheduled.size}",
                        if (result.unscheduled.isEmpty()) SlateCard else Color(0xFFFFE5E5),
                        if (result.unscheduled.isEmpty()) Ink else AccentRed,
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

            if (result.unscheduled.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("Can't Accept These",
                        style = MaterialTheme.typography.titleMedium, color = Ink)
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.project.name,
                                    style = MaterialTheme.typography.titleMedium, color = Ink)
                                Text(
                                    "${item.project.clientName} · due ${item.project.deadlineDate.format(DATE_FMT)}",
                                    style = MaterialTheme.typography.bodySmall, color = SlateDeep
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(item.constraint,
                                    style = MaterialTheme.typography.bodySmall, color = AccentRed)
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { onEditProject(item.project) },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, AccentRed),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null,
                                        tint = AccentRed, modifier = Modifier.size(13.dp).padding(end = 4.dp))
                                    Text("Edit project", color = AccentRed,
                                        fontFamily = InterFamily, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}