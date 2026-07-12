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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
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
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "List potential jobs. Weigh income vs. time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Guest controls and username removed per requirements.
                }
                Spacer(Modifier.height(20.dp))
            }

            item {
                WeeklyScheduleCard(
                    schedule  = weeklySchedule,
                    onChange  = { viewModel.setWeeklySchedule(it) },
                    onSave    = { newSchedule -> viewModel.saveWeeklySchedule(newSchedule) }
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text("Potential Projects", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }

            if (potential.isEmpty()) {
                item {
                    Text(
                        "Add a job you're considering — you'll see if it fits your schedule " +
                                "once you run the optimizer in the Schedule tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        Text("Delete All Projects", color = MaterialTheme.colorScheme.error, fontFamily = InterFamily, fontWeight = FontWeight.Medium)
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
                            }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Compact Weekly Schedule - The user configures capacity for a single selected day.
 */
@Composable
fun WeeklyScheduleCard(
    schedule: Map<DayOfWeek, Double>,
    onChange: (Map<DayOfWeek, Double>) -> Unit,
    onSave: (Map<DayOfWeek, Double>) -> Unit
) {
    val todayDayOfWeek = LocalDate.now().dayOfWeek
    val allDays = DayOfWeek.values()
    val daysOfWeek = (0..6).map { i ->
        allDays[(todayDayOfWeek.ordinal + i) % 7]
    }
    
    var selectedDay by remember { mutableStateOf(daysOfWeek.first()) }
    var showEditDialog by remember { mutableStateOf(false) }
    
    var tempSchedule by remember { mutableStateOf(schedule) }

    LaunchedEffect(showEditDialog) {
        if (showEditDialog) {
            tempSchedule = schedule
        }
    }

    if (showEditDialog) {
        val hours = tempSchedule[selectedDay] ?: 0.0

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Capacity") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { 
                            val currentIndex = daysOfWeek.indexOf(selectedDay)
                            selectedDay = daysOfWeek[(currentIndex - 1 + 7) % 7]
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Day", tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            selectedDay.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { 
                            val currentIndex = daysOfWeek.indexOf(selectedDay)
                            selectedDay = daysOfWeek[(currentIndex + 1) % 7]
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Day", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                val updated = tempSchedule.toMutableMap()
                                val newHours = (hours - 1.0).coerceAtLeast(0.0)
                                if (newHours == 0.0) updated.remove(selectedDay) else updated[selectedDay] = newHours
                                tempSchedule = updated
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Text("–", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            "${hours.toInt()} hrs",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            modifier = Modifier.width(64.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        IconButton(
                            onClick = {
                                val updated = tempSchedule.toMutableMap()
                                updated[selectedDay] = (hours + 1.0).coerceAtMost(24.0)
                                tempSchedule = updated
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Text("+", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange(tempSchedule)
                    onSave(tempSchedule)
                    showEditDialog = false
                }) { Text("Save", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
            Text(
                "Weekly Capacity",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(daysOfWeek) { day ->
                    val hrs = schedule[day] ?: 0.0
                    val isConfigured = hrs > 0.0
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isConfigured) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                selectedDay = day
                                showEditDialog = true
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                color = if (isConfigured) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${hrs.toInt()}h",
                                color = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
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
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(project.clientName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        tint = MaterialTheme.colorScheme.primary,
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add project",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (compact) 16.dp else 24.dp)
                    )
                }
                if (!compact) {
                    Spacer(Modifier.width(10.dp))
                    Text("Add a project", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}