package com.grp8.freelance

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.grp8.freelance.ui.theme.*
import java.time.LocalDate

/** Dialog for creating a brand-new project. */
@Composable
fun AddProjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, LocalDate, Double, Double) -> Unit
) {
    ProjectFormDialog(
        title    = "New Project",
        confirmLabel = "Add",
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

/** Dialog for editing an existing project, pre-filled with its current values. */
@Composable
fun EditProjectDialog(
    project: Project,
    onDismiss: () -> Unit,
    onConfirm: (String, String, LocalDate, Double, Double) -> Unit
) {
    ProjectFormDialog(
        title        = "Edit Project",
        confirmLabel = "Save",
        initialName     = project.name,
        initialClient   = project.clientName,
        initialDeadline = project.deadlineDate,
        initialHours    = project.hoursNeeded.fmt(),
        initialRate     = project.ratePerHour.fmt(),
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

/** Shared form body used by both [AddProjectDialog] and [EditProjectDialog]. */
@Composable
private fun ProjectFormDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    initialClient: String = "",
    initialDeadline: LocalDate? = null,
    initialHours: String = "",
    initialRate: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, String, LocalDate, Double, Double) -> Unit
) {
    var name     by remember { mutableStateOf(initialName) }
    var client   by remember { mutableStateOf(initialClient) }
    var deadline by remember { mutableStateOf(initialDeadline) }
    var hours    by remember { mutableStateOf(initialHours) }
    var rate     by remember { mutableStateOf(initialRate) }
    var nameError   by remember { mutableStateOf(false) }
    var clientError by remember { mutableStateOf(false) }
    var dateError   by remember { mutableStateOf(false) }
    var hoursError  by remember { mutableStateOf(false) }
    var rateError   by remember { mutableStateOf(false) }

    fun tryConfirm() {
        nameError   = name.isBlank()
        clientError = client.isBlank()
        dateError   = deadline == null
        hoursError  = hours.isBlank() || hours.toDoubleOrNull() == null || hours.toDouble() <= 0
        rateError   = rate.isBlank()  || rate.toDoubleOrNull()  == null || rate.toDouble()  <= 0
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
                Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)

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

                val context = LocalContext.current
                val today   = LocalDate.now()
                Column {
                    OutlinedButton(
                        onClick = {
                            val d = deadline ?: today
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    deadline  = LocalDate.of(year, month + 1, day)
                                    dateError = false
                                },
                                d.year, d.monthValue - 1, d.dayOfMonth
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
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
                            fontFamily = InterFamily,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp))
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
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", fontFamily = InterFamily)
                    }
                    Button(
                        onClick = { tryConfirm() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Text(confirmLabel, fontFamily = InterFamily)
                    }
                }
            }
        }
    }
}

/** A labeled text field with inline validation error display, used inside [ProjectFormDialog]. */
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
                focusedBorderColor   = AccentBlue,
                unfocusedBorderColor = SlateMid
            )
        )
        if (isError) {
            Text(errorMsg, color = AccentRed, fontSize = 11.sp,
                fontFamily = InterFamily,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp))
        }
    }
}