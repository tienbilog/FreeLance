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
 * Freelancers control their own time though, so this screen lets the user:
 *
 *  • Log actual hours worked on a project (may differ from the original estimate)
 *  • Check a project off as done — early completions surface an "ahead of
 *    schedule" prompt offering to re-optimize the remaining queue
 *  • See a "behind schedule" banner if logged hours are running over estimate,
 *    with the option to grant a one-time capacity boost on a future day
 */
@Composable
fun ToDoScreen(viewModel: SchedulerViewModel) {
    val allProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val scheduled = allProjects
        .filter { it.status == ProjectStatus.SCHEDULED }
        .sortedBy { it.assignedDate }
    val pace by viewModel.paceStatus.collectAsStateWithLifecycle()

    var editingHoursFor by remember { mutableStateOf<Project?>(null) }
    var showCatchUpFor   by remember { mutableStateOf<PaceStatus.Behind?>(null) }

    // Surface the "ahead" prompt the moment pace flips to Ahead.
    LaunchedEffect(pace) {
        if (pace is PaceStatus.Behind) showCatchUpFor = pace as PaceStatus.Behind
    }

    editingHoursFor?.let { proj ->
        LogHoursDialog(
            project = proj,
            onDismiss = { editingHoursFor = null },
            onConfirm = { hours ->
                viewModel.logHours(proj.id, hours)
                editingHoursFor = null
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
                    "Your committed schedule. Log hours as you go.",
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

            // ---- Behind-schedule persistent banner (until resolved via dialog) ----
            if (pace is PaceStatus.Behind) {
                val behind = pace as PaceStatus.Behind
                item {
                    BehindBanner(
                        hoursOver = behind.hoursOver,
                        onCatchUp = { showCatchUpFor = behind }
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

            val byDate = scheduled.groupBy { it.assignedDate!! }
            byDate.keys.sorted().forEach { date ->
                item {
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                                " · ${date.format(DATE_FMT)}",
                        style = MaterialTheme.typography.titleMedium, color = Ink,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(byDate[date]!!, key = { it.id }) { project ->
                    ToDoCard(
                        project = project,
                        onLogHours = { editingHoursFor = project },
                        onMarkDone = { viewModel.markDone(project.id) }
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
                "You finished with ${hoursSaved.fmt()}h to spare. Want to move up your " +
                        "upcoming projects to take advantage of the extra time?",
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
private fun BehindBanner(hoursOver: Double, onCatchUp: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE5E5)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("⏳ You're falling behind",
                style = MaterialTheme.typography.titleMedium, color = AccentRed)
            Spacer(Modifier.height(4.dp))
            Text(
                "You're ${hoursOver.fmt()}h behind your original estimates. Increase your " +
                        "capacity on a day you're free to help catch up.",
                style = MaterialTheme.typography.bodySmall, color = InkSoft
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onCatchUp,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) {
                Text("Add catch-up time", fontFamily = InterFamily, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ToDoCard(
    project: Project,
    onLogHours: () -> Unit,
    onMarkDone: () -> Unit
) {
    val overEstimate = project.hoursLogged > project.hoursNeeded

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
                    InfoChip(
                        if (project.hoursLogged > 0.0)
                            "⏱ ${project.hoursLogged.fmt()}/${project.hoursNeeded.fmt()}h"
                        else
                            "⏱ est. ${project.hoursNeeded.fmt()}h"
                    )
                }
                if (overEstimate) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Running ${(project.hoursLogged - project.hoursNeeded).fmt()}h over estimate",
                        style = MaterialTheme.typography.bodySmall, color = AccentRed
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onLogHours,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Log hours", fontFamily = InterFamily, fontSize = 12.sp, color = AccentBlue)
                }
            }
        }
    }
}

/** Dialog for entering how many hours have actually been worked on a project so far. */
@Composable
private fun LogHoursDialog(
    project: Project,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var hours by remember { mutableStateOf(if (project.hoursLogged > 0.0) project.hoursLogged.fmt() else "") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log hours — ${project.name}") },
        text = {
            Column {
                Text(
                    "Originally estimated at ${project.hoursNeeded.fmt()}h. How many hours " +
                            "have you actually worked so far?",
                    style = MaterialTheme.typography.bodySmall, color = SlateDeep
                )
                Spacer(Modifier.height(10.dp))
                DialogField(
                    "Hours worked", hours,
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
                if (parsed == null || parsed < 0) { error = true; return@TextButton }
                onConfirm(parsed)
            }) { Text("Save", color = AccentBlue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SlateDeep) }
        }
    )
}

/**
 * Dialog for granting a one-time capacity boost to a specific day to help
 * catch up on overrun hours. The chosen boost only applies to that single
 * date for this reschedule — it does not change the user's default daily cap.
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
        title = { Text("Catch up on ${hoursOver.fmt()}h") },
        text = {
            Column {
                Text(
                    "Pick a day you're free to take on extra hours. This boost applies " +
                            "once, just for that day.",
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