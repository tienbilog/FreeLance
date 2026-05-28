package com.grp8.freelance

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grp8.freelance.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

// ── Entry point ───────────────────────────────────────────────

@Composable
fun SchedulerApp(viewModel: SchedulerViewModel) {
    val result by viewModel.result.collectAsStateWithLifecycle()
    if (result != null) {
        ResultsScreen(result = result!!, onBack = { viewModel.clearResult() })
    } else {
        InputScreen(viewModel = viewModel)
    }
}

// ── Input Screen ──────────────────────────────────────────────

@Composable
fun InputScreen(viewModel: SchedulerViewModel) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val dailyCap by viewModel.dailyCap.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddProjectDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, client, deadline, hours, rate ->
                viewModel.addProject(name, client, deadline, hours, rate)
                showAddDialog = false
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
                Spacer(Modifier.height(20.dp))
            }

            // Capacity card
            item { CapacityCard(dailyCap, onChange = { viewModel.setDailyCap(it) }) }

            item {
                Spacer(Modifier.height(4.dp))
                Text("Projects", style = MaterialTheme.typography.titleMedium, color = Ink)
            }

            // Project cards
            items(projects, key = { it.id }) { project ->
                ProjectCard(project = project, onDelete = { viewModel.removeProject(project.id) })
            }

            // Add button — half-size after first project
            item {
                val hasProjects = projects.isNotEmpty()
                AddProjectButton(compact = hasProjects, onClick = { showAddDialog = true })
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── Capacity Card (custom slider) ─────────────────────────────

@Composable
fun CapacityCard(value: Double, onChange: (Double) -> Unit) {
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
                // Big hour badge
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

            // Segmented dot track
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
        }
    }
}

// ── Project Card ──────────────────────────────────────────────

@Composable
fun ProjectCard(project: Project, onDelete: () -> Unit) {
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
            // X button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Remove",
                    tint = SlateDeep, modifier = Modifier.size(18.dp))
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

// ── Add Project Button ────────────────────────────────────────

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

// ── Add Project Dialog ────────────────────────────────────────

@Composable
fun AddProjectDialog(onDismiss: () -> Unit, onConfirm: (String, String, LocalDate, Double, Double) -> Unit) {
    var name     by remember { mutableStateOf("") }
    var client   by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf<LocalDate?>(null) }
    var hours    by remember { mutableStateOf("") }
    var rate     by remember { mutableStateOf("") }
    var nameError   by remember { mutableStateOf(false) }
    var clientError by remember { mutableStateOf(false) }
    var dateError  by remember { mutableStateOf(false) }
    var hoursError by remember { mutableStateOf(false) }
    var rateError  by remember { mutableStateOf(false) }

    fun tryConfirm() {
        nameError   = name.isBlank()
        clientError = client.isBlank()
        dateError   = deadline == null
        hoursError = hours.isBlank() || hours.toDoubleOrNull() == null || hours.toDouble() <= 0
        rateError  = rate.isBlank() || rate.toDoubleOrNull() == null || rate.toDouble() <= 0
        if (nameError || clientError || dateError || hoursError || rateError) return
        onConfirm(name.trim(), client.trim(), deadline!!, hours.toDouble(), rate.toDouble())
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("New Project", style = MaterialTheme.typography.titleLarge, color = Ink)

                DialogField("Project name", name,
                    onValueChange = { name = it; nameError = false },
                    isError = nameError,
                    errorMsg = "Project name is required"
                )
                DialogField("Client name", client,
                    onValueChange = { client = it; clientError = false },
                    isError = clientError,
                    errorMsg = "Client name is required"
                )

                // ── Date Picker Button ──
                val context = LocalContext.current
                val today = LocalDate.now()
                Column {
                    OutlinedButton(
                        onClick = {
                            val d = deadline ?: today
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    deadline = LocalDate.of(year, month + 1, day)
                                    dateError = false
                                },
                                d.year, d.monthValue - 1, d.dayOfMonth
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, if (dateError) AccentRed else SlateMid
                        )
                    ) {
                        Text(
                            text = deadline?.format(DATE_FMT) ?: "Select Deadline",
                            fontFamily = InterFamily,
                            color = if (deadline != null) Ink else SlateDeep
                        )
                    }
                    if (dateError) {
                        Text("Please select a deadline", color = AccentRed, fontSize = 11.sp,
                            fontFamily = InterFamily, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DialogField("Est. hours", hours,
                        onValueChange = { hours = it; hoursError = false },
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Decimal,
                        isError = hoursError,
                        errorMsg = if (hours.isBlank()) "Required" else "Numbers only"
                    )
                    DialogField("₱/hour", rate,
                        onValueChange = { rate = it; rateError = false },
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Decimal,
                        isError = rateError,
                        errorMsg = if (rate.isBlank()) "Required" else "Numbers only"
                    )
                }

                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)) {
                        Text("Cancel", fontFamily = InterFamily)
                    }
                    Button(onClick = { tryConfirm() }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                        Text("Add", fontFamily = InterFamily)
                    }
                }
            }
        }
    }
}

@Composable
fun DialogField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorMsg: String = "",
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, fontFamily = InterFamily, fontSize = 12.sp) },
            isError = isError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = SlateMid
            )
        )
        if (isError) {
            Text(errorMsg, color = AccentRed, fontSize = 11.sp,
                fontFamily = InterFamily, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
        }
    }
}

// ── Results Screen ────────────────────────────────────────────

@Composable
fun ResultsScreen(result: ScheduleResult, onBack: () -> Unit) {
    Scaffold(
        containerColor = White,
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    Text("Schedule Results", style = MaterialTheme.typography.titleMedium, color = Ink)
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
            // Metrics
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

            // Schedule header
            item {
                Text("Schedule", style = MaterialTheme.typography.titleMedium, color = Ink)
            }

            // Day cards
            val byDate = result.accepted.groupBy { it.assignedDate }
            items(byDate.keys.sorted()) { date ->
                ScheduleDayCard(date = date, items = byDate[date] ?: emptyList())
            }

            // Dropped
            if (result.dropped.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("Dropped Projects", style = MaterialTheme.typography.titleMedium, color = Ink)
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
                            Column {
                                Text(dropped.project.name, style = MaterialTheme.typography.titleMedium,
                                    color = Ink)
                                Text("${dropped.project.clientName} · due ${dropped.project.deadlineDate.format(DATE_FMT)}",
                                    style = MaterialTheme.typography.bodySmall, color = SlateDeep)
                                Spacer(Modifier.height(4.dp))
                                Text(dropped.reason, style = MaterialTheme.typography.bodySmall,
                                    color = AccentRed)
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
    val totalHours = items.sumOf { it.project.hoursNeeded }
    val totalIncome = items.sumOf { it.project.totalIncome }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Date header
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

            // Project rows
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