package com.grp8.freelance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SchedulerApp(viewModel: SchedulerViewModel) {
    val result by viewModel.result.collectAsStateWithLifecycle()

    // Show results screen if we have a result, otherwise show input screen
    if (result != null) {
        ResultsScreen(
            result = result!!,
            onBack = { viewModel.clearResult() }
        )
    } else {
        InputScreen(viewModel = viewModel)
    }
}

// ── Input Screen ──────────────────────────────────────────────

@Composable
fun InputScreen(viewModel: SchedulerViewModel) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val dailyCap by viewModel.dailyCap.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { viewModel.runScheduler() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    enabled = projects.isNotEmpty()
                ) {
                    Text("Run Scheduler", fontSize = 16.sp)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding)
        ) {
            item {
                Text("Freelance Scheduler", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
            }

            // Daily cap slider
            item {
                CapSlider(
                    value = dailyCap,
                    onChange = { viewModel.setDailyCap(it) }
                )
            }

            item {
                Text("Projects", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            // One card per project
            items(projects, key = { it.id }) { project ->
                ProjectCard(
                    project = project,
                    onUpdate = { viewModel.updateProject(it) },
                    onDelete = { viewModel.removeProject(project.id) }
                )
            }

            item {
                OutlinedButton(
                    onClick = { viewModel.addProject() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Add project")
                }
            }
        }
    }
}

@Composable
fun CapSlider(value: Double, onChange: (Double) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Daily hour capacity", fontWeight = FontWeight.Medium)
                Text("${value.toInt()} hrs/day", color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { onChange(it.toDouble()) },
                valueRange = 2f..16f,
                steps = 13,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ProjectCard(
    project: Project,
    onUpdate: (Project) -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = project.name,
                    onValueChange = { onUpdate(project.copy(name = it)) },
                    label = { Text("Project name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete project")
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    label = "Deadline (day)",
                    value = project.deadlineDay.toString(),
                    modifier = Modifier.weight(1f),
                    onValueChange = {
                        it.toIntOrNull()?.let { d -> onUpdate(project.copy(deadlineDay = d.coerceIn(1, 30))) }
                    }
                )
                NumberField(
                    label = "Hours needed",
                    value = project.hoursNeeded.toString(),
                    modifier = Modifier.weight(1f),
                    onValueChange = {
                        it.toDoubleOrNull()?.let { h -> onUpdate(project.copy(hoursNeeded = h.coerceIn(0.5, 80.0))) }
                    }
                )
                NumberField(
                    label = "₱/hour",
                    value = project.ratePerHour.toString(),
                    modifier = Modifier.weight(1f),
                    onValueChange = {
                        it.toIntOrNull()?.let { r -> onUpdate(project.copy(ratePerHour = r.coerceAtLeast(1))) }
                    }
                )
            }
        }
    }
}

@Composable
fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier
    )
}

// ── Results Screen ────────────────────────────────────────────

@Composable
fun ResultsScreen(result: ScheduleResult, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Schedule Results") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding)
        ) {
            // Summary metrics
            item { SummaryCards(result) }

            // Accepted projects grouped by day
            item {
                Text("Day-by-day schedule", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            val byDay = result.accepted.groupBy { it.assignedDay }
            items(byDay.keys.sorted()) { day ->
                DayCard(day = day, items = byDay[day] ?: emptyList())
            }

            // Dropped projects
            if (result.dropped.isNotEmpty()) {
                item {
                    Text("Dropped projects", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    result.dropped.forEach { proj ->
                        Text(
                            "✗ ${proj.name} — deadline day ${proj.deadlineDay}, ${proj.hoursNeeded}h needed",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Algorithm log
            item {
                Text("Algorithm trace", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        result.log.forEach { line ->
                            val color = when {
                                line.startsWith("  ✗") -> MaterialTheme.colorScheme.error
                                line.startsWith("  Assign") -> MaterialTheme.colorScheme.primary
                                line.startsWith("✓") -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(line, fontSize = 11.sp, color = color)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCards(result: ScheduleResult) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard("Total income",  "₱${result.totalIncome.formatted()}", Modifier.weight(1f))
        MetricCard("Accepted",      "${result.accepted.size} projects",    Modifier.weight(1f))
        MetricCard("Dropped",       "${result.dropped.size} projects",     Modifier.weight(1f))
    }
}

@Composable
fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DayCard(day: Int, items: List<ScheduledProject>) {
    val totalHours = items.sumOf { it.project.hoursNeeded }
    val totalIncome = items.sumOf { it.project.totalIncome }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Day $day", fontWeight = FontWeight.Bold)
                Text("${totalHours.fmt()}h · ₱${totalIncome.formatted()}", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            items.forEach { sp ->
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Text("• ${sp.project.name}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("${sp.project.hoursNeeded.fmt()}h @ ₱${sp.project.ratePerHour}/hr",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}