package com.grp8.freelance

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * PHASE 1 — Optimizer.
 *
 * Where the user lists potential jobs they're considering, before committing
 * to anything. Nothing here is a real obligation yet — it's a "what if I take
 * these on" workspace. Running the optimizer (Phase 2 tab) is what turns this
 * list into an actual suggested schedule.
 */
@Composable
fun OptimizerScreen(
    viewModel: SchedulerViewModel,
    username: String?,
    onSignOut: () -> Unit
) {
    val allProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val potential = allProjects.filter { it.status == ProjectStatus.POTENTIAL }
    val weeklySchedule by viewModel.weeklySchedule.collectAsStateWithLifecycle()

    var showAddDialog  by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<Project?>(null) }

    if (showAddDialog) {
        AddProjectDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, client, deadline, hours, rateType, rate, fixed ->
                viewModel.addPotentialProject(name, client, deadline, hours, rateType, rate, fixed)
                showAddDialog = false
            }
        )
    }

    editingProject?.let { proj ->
        EditProjectDialog(
            project = proj,
            onDismiss = { editingProject = null },
            onConfirm = { name, client, deadline, hours, rateType, rate, fixed ->
                viewModel.updatePotentialProject(proj.id, name, client, deadline, hours, rateType, rate, fixed)
                editingProject = null
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            "Optimizer",
                            style = MaterialTheme.typography.displayLarge,
                            color = Ink
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "List potential jobs. Weigh income vs. time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateDeep
                        )
                    }
                    var showSignOutDialog by remember { mutableStateOf(false) }
                    AssistChip(
                        onClick = { showSignOutDialog = true },
                        label = {
                            Text(
                                text = if (username != null) "@$username" else "Guest",
                                fontFamily = InterFamily,
                                fontSize = 12.sp
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = SlateCard,
                            labelColor = if (username != null) AccentBlue else SlateDeep
                        )
                    )
                    if (showSignOutDialog) {
                        AlertDialog(
                            onDismissRequest = { showSignOutDialog = false },
                            title = { Text(if (username != null) "Sign out?" else "Leave guest mode?") },
                            text  = {
                                Text(
                                    if (username != null)
                                        "You'll be taken back to the sign-in screen."
                                    else
                                        "Your guest data will stay on this device. You can sign in or continue as guest again."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showSignOutDialog = false
                                    onSignOut()
                                }) { Text("Yes", color = AccentBlue) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showSignOutDialog = false }) {
                                    Text("Cancel", color = SlateDeep)
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            item {
                WeeklyScheduleCard(
                    schedule  = weeklySchedule,
                    onChange  = { viewModel.setWeeklySchedule(it) },
                    onSave    = { viewModel.saveWeeklySchedule(weeklySchedule) }
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text("Potential Projects", style = MaterialTheme.typography.titleMedium, color = Ink)
            }

            if (potential.isEmpty()) {
                item {
                    Text(
                        "Add a job you're considering — you'll see if it fits your schedule " +
                                "once you run the optimizer in the Schedule tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateDeep
                    )
                }
            }

            items(potential, key = { it.id }) { project ->
                ProjectCard(
                    project  = project,
                    onDelete = { viewModel.removePotentialProject(project.id) },
                    onEdit   = { editingProject = project }
                )
            }

            item {
                AddProjectButton(compact = potential.isNotEmpty(), onClick = { showAddDialog = true })
            }

            item {
                var showDeleteAllDialog by remember { mutableStateOf(false) }
                if (allProjects.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    TextButton(
                        onClick = { showDeleteAllDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete All Projects", color = AccentRed, fontFamily = InterFamily, fontWeight = FontWeight.Medium)
                    }
                }
                if (showDeleteAllDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteAllDialog = false },
                        title = { Text("Delete all projects?") },
                        text = { Text("This action cannot be undone. All schedules and assignments will be cleared.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showDeleteAllDialog = false
                                viewModel.deleteAllProjects()
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

/**
 * "Set Weekly Schedule" — the user picks which days of the week they're
 * dedicating time to work, and how many hours each. Unselected days fall
 * back to a small emergency capacity rather than zero, since unplanned free
 * time still happens. This pattern repeats for all future weeks.
 */
@Composable
fun WeeklyScheduleCard(
    schedule: Map<DayOfWeek, Double>,
    onChange: (Map<DayOfWeek, Double>) -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text("Weekly Schedule", style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(
                "Pick the days you're working each week, and how many hours. This repeats for future weeks.",
                style = MaterialTheme.typography.bodySmall, color = SlateDeep
            )
            Spacer(Modifier.height(14.dp))

            DayOfWeek.values().forEach { dayOfWeek ->
                val isSelected = schedule.containsKey(dayOfWeek)
                val hours      = schedule[dayOfWeek] ?: 0.0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Day toggle chip
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) AccentBlue else White)
                            .clickable {
                                val updated = schedule.toMutableMap()
                                if (isSelected) updated.remove(dayOfWeek) else updated[dayOfWeek] = 4.0
                                onChange(updated)
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            fontFamily = InterFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) White else Ink
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    if (isSelected) {
                        // Hour stepper for a selected work day
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = {
                                    val updated = schedule.toMutableMap()
                                    updated[dayOfWeek] = (hours - 1.0).coerceAtLeast(1.0)
                                    onChange(updated)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("–", fontSize = 18.sp, color = AccentBlue)
                            }
                            Text(
                                "${hours.toInt()}h",
                                fontFamily = InterFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = Ink,
                                modifier = Modifier.width(36.dp)
                            )
                            IconButton(
                                onClick = {
                                    val updated = schedule.toMutableMap()
                                    updated[dayOfWeek] = (hours + 1.0).coerceAtMost(16.0)
                                    onChange(updated)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("+", fontSize = 18.sp, color = AccentBlue)
                            }
                        }
                    } else {
                        Text(
                            "Tap day to add hours",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateDeep,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Days you don't pick fall back to ${UNSELECTED_DAY_FALLBACK_HOURS.toInt()}h, " +
                        "for emergencies.",
                style = MaterialTheme.typography.bodySmall,
                color = SlateDeep
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Capacity Settings", fontFamily = InterFamily, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun ProjectCard(
    project: Project,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(project.clientName, style = MaterialTheme.typography.bodySmall, color = SlateDeep)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoChip("📅 ${project.deadlineDate.format(DATE_FMT)}")
                    InfoChip("⏱ ${project.hoursNeeded.fmt()}h")
                    InfoChip(
                        if (project.rateType == RateType.HOURLY)
                            "₱${project.ratePerHour.fmt()}/hr"
                        else
                            "₱${project.fixedAmount.fmt()} fixed"
                    )
                }
            }
            Row {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = AccentBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = SlateDeep,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun InfoChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SlateMid)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = InkSoft)
    }
}

@Composable
fun AddProjectButton(compact: Boolean, onClick: () -> Unit) {
    val height = if (compact) 52.dp else 108.dp

    OutlinedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = White),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 26.dp else 40.dp)
                        .clip(CircleShape)
                        .background(AccentBlueSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add project",
                        tint = AccentBlue,
                        modifier = Modifier.size(if (compact) 16.dp else 24.dp)
                    )
                }
                if (!compact) {
                    Spacer(Modifier.width(10.dp))
                    Text("Add a project", style = MaterialTheme.typography.titleMedium,
                        color = AccentBlue)
                }
            }
        }
    }
}