package com.grp8.freelance

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import java.time.LocalTime

/**
 * The main "home" screen — daily capacity, project list, and the entry
 * point for adding/editing projects before running the scheduler.
 */
@Composable
fun InputScreen(
    viewModel: SchedulerViewModel,
    initialEditProject: Project? = null,
    onEditHandled: () -> Unit = {},
    username: String? = null,
    onSignOut: () -> Unit = {}
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val dailyCap by viewModel.dailyCap.collectAsStateWithLifecycle()
    var showAddDialog  by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<Project?>(null) }

    LaunchedEffect(initialEditProject) {
        if (initialEditProject != null) {
            editingProject = initialEditProject
            onEditHandled()
        }
    }

    if (showAddDialog) {
        AddProjectDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, client, deadline, hours, rate ->
                viewModel.addProject(name, client, deadline, hours, rate)
                showAddDialog = false
            }
        )
    }

    editingProject?.let { proj ->
        EditProjectDialog(
            project = proj,
            onDismiss = { editingProject = null },
            onConfirm = { name, client, deadline, hours, rate ->
                viewModel.updateProject(proj.id, name, client, deadline, hours, rate)
                editingProject = null
            }
        )
    }

    Scaffold(
        containerColor = White,
        bottomBar = {
            Surface(color = White, tonalElevation = 0.dp) {
                Button(
                    onClick = { viewModel.runScheduler() },
                    enabled = projects.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text("Run Scheduler", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFamily)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = 24.dp, bottom = padding.calculateBottomPadding() + 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                // Account chip — shows username (signed in) or "Guest"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            "Freelance\nScheduler",
                            style = MaterialTheme.typography.displayLarge,
                            color = Ink,
                            lineHeight = 34.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Plan your week. Maximize income.",
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

            item { CapacityCard(dailyCap, onChange = { viewModel.setDailyCap(it) }) }

            item {
                Spacer(Modifier.height(4.dp))
                Text("Projects", style = MaterialTheme.typography.titleMedium, color = Ink)
            }

            items(projects, key = { it.id }) { project ->
                ProjectCard(
                    project  = project,
                    onDelete = { viewModel.removeProject(project.id) },
                    onEdit   = { editingProject = project }
                )
            }

            item {
                val hasProjects = projects.isNotEmpty()
                AddProjectButton(compact = hasProjects, onClick = { showAddDialog = true })
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun CapacityCard(value: Double, onChange: (Double) -> Unit) {
    // Recompute every minute so the banner stays accurate without a restart.
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            currentTime = LocalTime.now()
        }
    }

    val todayEffective = Scheduler.todayRemainingHours(value)
    val todayIsReduced = todayEffective < value   // true when clock is eating into today's cap

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("Daily Capacity", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Text("Working hours per day", style = MaterialTheme.typography.bodySmall,
                        color = SlateDeep)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentBlue)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "${value.toInt()} hrs",
                        color = White,
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (h in 1..16) {
                    val active = h <= value.toInt()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(if (active) 10.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (active) AccentBlue else SlateMid)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Slider(
                value = value.toFloat(),
                onValueChange = { onChange(it.toDouble()) },
                valueRange = 1f..16f,
                steps = 14,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = AccentBlue,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("1h", style = MaterialTheme.typography.bodySmall, color = SlateDeep)
                Text("16h", style = MaterialTheme.typography.bodySmall, color = SlateDeep)
            }

            // ----------------------------------------------------------------
            // Real-time banner: only shown when today's effective cap has been
            // reduced by the current wall-clock time.
            // ----------------------------------------------------------------
            if (todayIsReduced) {
                Spacer(Modifier.height(10.dp))
                val bannerText = if (todayEffective == 0.0) {
                    "No time left today — projects will start from tomorrow."
                } else {
                    val h = todayEffective.toInt()
                    val m = ((todayEffective - h) * 60).toInt()
                    val timeStr = when {
                        h > 0 && m > 0 -> "${h}h ${m}m"
                        h > 0          -> "${h}h"
                        else           -> "${m}m"
                    }
                    "Only $timeStr left today — projects needing more will be assigned from tomorrow."
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFF3CD))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        "⏰  $bannerText",
                        fontFamily = InterFamily,
                        fontSize = 12.sp,
                        color = Color(0xFF7A5C00)
                    )
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
                    InfoChip("₱${project.ratePerHour.fmt()}/hr")
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