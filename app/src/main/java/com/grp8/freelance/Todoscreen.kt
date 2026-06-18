package com.grp8.freelance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grp8.freelance.ui.theme.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * PHASE 3 — To-Do List.
 *
 * The committed, working schedule. Projects here are real obligations — they
 * came from an accepted suggestion in Phase 2 and can't simply be dropped.
 *
 * Checking a project off as done immediately asks how many hours it actually
 * took. That's compared against the original estimate right there:
 *  • Finished in FEWER hours → "ahead of schedule" — offers to re-run the
 *    optimizer so remaining projects can move up and take advantage of it.
 *  • Took MORE hours → "behind schedule" — suggests boosting a future day's
 *    capacity (one-time) to make up the difference.
 */
@Composable
fun ToDoScreen(viewModel: SchedulerViewModel) {
    val allProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val scheduled = allProjects
        .filter { it.status == ProjectStatus.SCHEDULED }
    val pace by viewModel.paceStatus.collectAsStateWithLifecycle()

    var completingProject by remember { mutableStateOf<Project?>(null) }
    var showCatchUpFor     by remember { mutableStateOf<PaceStatus.Behind?>(null) }

    // Surface the catch-up dialog the moment pace flips to Behind.
    LaunchedEffect(pace) {
        if (pace is PaceStatus.Behind) showCatchUpFor = pace as PaceStatus.Behind
    }

    completingProject?.let { proj ->
        CompleteProjectDialog(
            project = proj,
            onDismiss = { completingProject = null },
            onConfirm = { actualHours ->
                viewModel.completeProject(proj.id, actualHours)
                completingProject = null
            }
        )
    }

    showCatchUpFor?.let { behind ->
        CatchUpDialog(
            hoursOver = behind.hoursOver,
            onDismiss = { showCatchUpFor = null; viewModel.acknowledgePace() },
            onConfirm = { date, extraHours ->
                viewModel.rescheduleRemaining(date to extraHours)
                showCatchUpFor = null
            }
        )
    }

    Scaffold(containerColor = White) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = 24.dp, bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("To-Do List", style = MaterialTheme.typography.displayLarge, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Your committed schedule. Check off projects as you finish them.",
                    style = MaterialTheme.typography.bodyMedium, color = SlateDeep
                )
            }

            // ---- Ahead-of-schedule prompt ----
            if (pace is PaceStatus.Ahead) {
                val ahead = pace as PaceStatus.Ahead
                item {
                    AheadBanner(
                        hoursSaved = ahead.hoursSaved,
                        onMoveUp = { viewModel.rescheduleRemaining(); viewModel.acknowledgePace() },
                        onDismiss = { viewModel.acknowledgePace() }
                    )
                }
            }

            if (scheduled.isEmpty()) {
                item {
                    Text(
                        "Nothing scheduled yet. Accept a suggested schedule from the " +
                                "Schedule tab to populate your to-do list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateDeep
                    )
                }
            }

            val flattened = scheduled.flatMap { proj ->
                proj.assignedDates.map { (date, hours) ->
                    date to Pair(proj, hours)
                }
            }
            val byDate = flattened.groupBy({ it.first }, { it.second })
            byDate.keys.sorted().forEach { date ->
                item {
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                                " · ${date.format(DATE_FMT)}",
                        style = MaterialTheme.typography.titleMedium, color = Ink,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(byDate[date]!!, key = { "${date}_${it.first.id}" }) { (project, hoursForDay) ->
                    ToDoCard(
                        project = project,
                        hoursForDay = hoursForDay,
                        onMarkDone = { completingProject = project }
                    )
                }
            }
        }
    }
}

@Composable
private fun AheadBanner(hoursSaved: Double, onMoveUp: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F3EA)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("🚀 You're ahead of schedule",
                style = MaterialTheme.typography.titleMedium, color = AccentGreen)
            Spacer(Modifier.height(4.dp))
            Text(
                "You finished that project ${hoursSaved.fmt()}h faster than estimated. Want to " +
                        "re-run the optimizer so your remaining projects can move up?",
                style = MaterialTheme.typography.bodySmall, color = InkSoft
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                    Text("Not now", fontFamily = InterFamily, fontSize = 13.sp)
                }
                Button(
                    onClick = onMoveUp,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    Text("Move projects up", fontFamily = InterFamily, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ToDoCard(
    project: Project,
    hoursForDay: Double,
    onMarkDone: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            IconButton(onClick = onMarkDone, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Mark done",
                    tint = AccentGreen
                )
            }
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(project.clientName, style = MaterialTheme.typography.bodySmall, color = SlateDeep)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoChip("📅 due ${project.deadlineDate.format(DATE_FMT)}")
                    InfoChip("⏱ ${hoursForDay.fmt()}h today")
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onMarkDone,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Mark as done", fontFamily = InterFamily, fontSize = 12.sp, color = AccentGreen)
                }
            }
        }
    }
}

/**
 * Shown the instant a project is checked off. Asks how many hours it
 * actually took — that single number is what determines ahead/behind.
 */
@Composable
private fun CompleteProjectDialog(
    project: Project,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var hours by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finish — ${project.name}") },
        text = {
            Column {
                Text(
                    "This was estimated at ${project.hoursNeeded.fmt()}h. How many hours did " +
                            "it actually take you?",
                    style = MaterialTheme.typography.bodySmall, color = SlateDeep
                )
                Spacer(Modifier.height(10.dp))
                DialogField(
                    "Actual hours", hours,
                    onValueChange = { hours = it; error = false },
                    keyboardType = KeyboardType.Decimal,
                    isError = error,
                    errorMsg = "Enter a valid number"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = hours.toDoubleOrNull()
                if (parsed == null || parsed <= 0) { error = true; return@TextButton }
                onConfirm(parsed)
            }) { Text("Done", color = AccentGreen) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SlateDeep) }
        }
    )
}

/**
 * Dialog for granting a one-time capacity boost to a specific day to help
 * catch up on overrun hours. The chosen boost only applies to that single
 * date for this reschedule — it does not change the user's weekly schedule.
 */
@Composable
private fun CatchUpDialog(
    hoursOver: Double,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, Double) -> Unit
) {
    val today = LocalDate.now()
    val upcomingDays = (0..6).map { today.plusDays(it.toLong()) }
    var selectedDay by remember { mutableStateOf(upcomingDays.first()) }
    var extraHours  by remember { mutableStateOf(hoursOver.fmt()) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("You're ${hoursOver.fmt()}h behind") },
        text = {
            Column {
                Text(
                    "That took longer than estimated. Pick a day you're free to take on " +
                            "extra hours to help catch up — this boost applies once, just for that day.",
                    style = MaterialTheme.typography.bodySmall, color = SlateDeep
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateCard, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    upcomingDays.forEach { day ->
                        val selected = day == selectedDay
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) AccentBlue else Color.Transparent)
                                .clickable { selectedDay = day }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                color = if (selected) White else Ink,
                                fontFamily = InterFamily,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                DialogField(
                    "Extra hours that day", extraHours,
                    onValueChange = { extraHours = it; error = false },
                    keyboardType = KeyboardType.Decimal,
                    isError = error,
                    errorMsg = "Enter a valid number"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = extraHours.toDoubleOrNull()
                if (parsed == null || parsed <= 0) { error = true; return@TextButton }
                onConfirm(selectedDay, parsed)
            }) { Text("Apply", color = AccentBlue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now", color = SlateDeep) }
        }
    )
}