package com.grp8.freelance

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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

@Composable
fun ToDoScreen(viewModel: SchedulerViewModel) {
    val allProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val scheduled = allProjects.filter { it.status == ProjectStatus.SCHEDULED }
    val pace by viewModel.paceStatus.collectAsStateWithLifecycle()

    var completingAssignment by remember { mutableStateOf<Pair<Project, LocalDate>?>(null) }
    var showCatchUpFor     by remember { mutableStateOf<PaceStatus.Behind?>(null) }

    LaunchedEffect(pace) {
        if (pace is PaceStatus.Behind) showCatchUpFor = pace as PaceStatus.Behind
    }

    completingAssignment?.let { (proj, date) ->
        CompleteAssignmentDialog(
            project = proj,
            date = date,
            onDismiss = { completingAssignment = null },
            onConfirm = { actualHours ->
                viewModel.completeAssignment(proj.id, date, actualHours)
                completingAssignment = null
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

    val totalCount = scheduled.size
    val completedCount = scheduled.count { it.taskStatus == TaskStatus.COMPLETED }
    val ongoingCount = scheduled.count { it.taskStatus == TaskStatus.ONGOING }
    val overallProgress = if (totalCount > 0) {
        scheduled.map { it.progress }.average().toFloat()
    } else 0f

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = 24.dp, bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("To-Do List", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Your committed schedule. Check off projects as you finish them.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                DashboardSummaryCard(totalCount, ongoingCount, completedCount, overallProgress)
            }

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
                        "Nothing scheduled yet. Accept a suggested schedule from the Schedule tab to populate your to-do list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val stalledProjects = scheduled.filter { it.assignedDates.isEmpty() && it.taskStatus != TaskStatus.COMPLETED }

            if (stalledProjects.isNotEmpty()) {
                item {
                    Text(
                        "Needs Attention",
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(stalledProjects, key = { "stalled_${it.id}" }) { project ->
                    ToDoCard(
                        project = project,
                        date = LocalDate.now(),
                        hoursForDay = 0.0,
                        viewModel = viewModel,
                        onMarkDone = { completingAssignment = project to LocalDate.now() }
                    )
                }
            }

            val flattened = scheduled.flatMap { proj ->
                proj.assignedDates.map { (date, hours) -> date to Pair(proj, hours) }
            }
            val byDate = flattened.groupBy({ it.first }, { it.second })
            byDate.keys.sorted().forEach { date ->
                item {
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                                " · ${date.format(DATE_FMT)}",
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(byDate[date]!!, key = { "${date}_${it.first.id}" }) { (project, hoursForDay) ->
                    ToDoCard(
                        project = project,
                        date = date,
                        hoursForDay = hoursForDay,
                        viewModel = viewModel,
                        onMarkDone = { completingAssignment = project to date }
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardSummaryCard(total: Int, ongoing: Int, completed: Int, progress: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$total", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ongoing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$ongoing", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Completed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$completed", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Overall Progress", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(6.dp))
            val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ToDoCard(
    project: Project,
    date: LocalDate,
    hoursForDay: Double,
    viewModel: SchedulerViewModel,
    onMarkDone: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var subtaskTitle by remember { mutableStateOf("") }
    
    val statusColor = when (project.taskStatus) {
        TaskStatus.NOT_STARTED -> MaterialTheme.colorScheme.onSurfaceVariant
        TaskStatus.ONGOING -> MaterialTheme.colorScheme.primary
        TaskStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
    }

    val isAssignmentComplete = project.completedAssignments.contains(date)

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                IconButton(onClick = onMarkDone, modifier = Modifier.size(32.dp), enabled = !isAssignmentComplete) {
                    Icon(
                        if (isAssignmentComplete) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Mark done",
                        tint = if (isAssignmentComplete) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(project.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        // Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(statusColor.copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                project.taskStatus.name.replace("_", " "),
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor
                            )
                        }
                    }
                    Text(project.clientName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    if (project.scheduleWarning != null) {
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text("⚠", color = MaterialTheme.colorScheme.error, fontSize = 14.sp, modifier = Modifier.padding(end = 6.dp))
                                Text(project.scheduleWarning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // Progress Bar
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        val animatedProgress by animateFloatAsState(targetValue = project.progress, label = "progress")
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${(project.progress * 100).toInt()}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        InfoChip("📅 due ${project.deadlineDate.format(DATE_FMT)}")
                        InfoChip("⏱ ${hoursForDay.fmt()}h today")
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(24.dp)) {
                            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = "Expand subtasks")
                        }
                    }
                }
            }
            
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(8.dp))
                
                project.subtasks.forEach { sub ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Checkbox(
                            checked = sub.isCompleted,
                            onCheckedChange = { viewModel.toggleSubtask(project.id, sub.id) },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Text(sub.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.removeSubtask(project.id, sub.id) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                
                // Add new subtask
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 12.dp, end = 4.dp)) {
                    OutlinedTextField(
                        value = subtaskTitle,
                        onValueChange = { subtaskTitle = it },
                        placeholder = { Text("Add subtask...", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (subtaskTitle.isNotBlank()) {
                                viewModel.addSubtask(project.id, subtaskTitle)
                                subtaskTitle = ""
                            }
                        },
                        modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onMarkDone,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Text("Mark project as done", fontFamily = InterFamily, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun AheadBanner(hoursSaved: Double, onMoveUp: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("🚀 You're ahead of schedule",
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(
                "You finished that project ${hoursSaved.fmt()}h faster than estimated. Want to " +
                        "re-run the optimizer so your remaining projects can move up?",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                    Text("Not now", fontFamily = InterFamily, fontSize = 13.sp)
                }
                Button(
                    onClick = onMoveUp,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Move projects up", fontFamily = InterFamily, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun CompleteAssignmentDialog(
    project: Project,
    date: LocalDate,
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
                val scheduledHours = project.assignedDates[date] ?: 0.0
                Text(
                    "This was scheduled for ${scheduledHours.fmt()}h today. How many hours did " +
                            "it actually take you?",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
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
            }) { Text("Done", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}

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
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    upcomingDays.forEach { day ->
                        val selected = day == selectedDay
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent)
                                .clickable { selectedDay = day }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
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
            }) { Text("Apply", color = MaterialTheme.colorScheme.secondary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}