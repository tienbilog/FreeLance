package com.grp8.freelance

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.grp8.freelance.ui.theme.*
import java.time.LocalDate

/** Dialog for creating a brand-new potential project (Phase 1). */
@Composable
fun AddProjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, LocalDate, Double, RateType, Double, Double) -> Unit
) {
    ProjectFormDialog(
        title        = "New Potential Project",
        confirmLabel = "Add",
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

/** Dialog for editing an existing potential project, pre-filled with its current values. */
@Composable
fun EditProjectDialog(
    project: Project,
    onDismiss: () -> Unit,
    onConfirm: (String, String, LocalDate, Double, RateType, Double, Double) -> Unit
) {
    ProjectFormDialog(
        title           = "Edit Project",
        confirmLabel    = "Save",
        initialName     = project.name,
        initialClient   = project.clientName,
        initialDeadline = project.deadlineDate,
        initialHours    = project.hoursNeeded.fmt(),
        initialRateType = project.rateType,
        initialRate     = if (project.rateType == RateType.HOURLY) project.ratePerHour.fmt() else "",
        initialFixed    = if (project.rateType == RateType.FIXED) project.fixedAmount.fmt() else "",
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

/**
 * Shared form body used by both [AddProjectDialog] and [EditProjectDialog].
 *
 * Income input switches between two modes via radio buttons:
 *  • Hourly — user enters a rate per hour; income = hours × rate.
 *  • Fixed  — user enters a flat total; income = that amount regardless of hours.
 * Estimated hours is always required either way, since the scheduler needs it
 * to reserve capacity on a calendar day.
 */
@Composable
private fun ProjectFormDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    initialClient: String = "",
    initialDeadline: LocalDate? = null,
    initialHours: String = "",
    initialRateType: RateType = RateType.HOURLY,
    initialRate: String = "",
    initialFixed: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, String, LocalDate, Double, RateType, Double, Double) -> Unit
) {
    var name     by remember { mutableStateOf(initialName) }
    var client   by remember { mutableStateOf(initialClient) }
    var deadline by remember { mutableStateOf(initialDeadline) }
    var hours    by remember { mutableStateOf(initialHours) }
    var rateType by remember { mutableStateOf(initialRateType) }
    var rate     by remember { mutableStateOf(initialRate) }
    var fixed    by remember { mutableStateOf(initialFixed) }

    var nameError   by remember { mutableStateOf(false) }
    var clientError by remember { mutableStateOf(false) }
    var dateError   by remember { mutableStateOf(false) }
    var hoursError  by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }

    fun tryConfirm() {
        nameError   = name.isBlank()
        clientError = client.isBlank()
        dateError   = deadline == null
        hoursError  = hours.isBlank() || hours.toDoubleOrNull() == null || hours.toDouble() <= 0

        val amountText = if (rateType == RateType.HOURLY) rate else fixed
        amountError = amountText.isBlank() || amountText.toDoubleOrNull() == null || amountText.toDouble() <= 0

        if (nameError || clientError || dateError || hoursError || amountError) return

        onConfirm(
            name.trim(), client.trim(), deadline!!, hours.toDouble(), rateType,
            if (rateType == RateType.HOURLY) rate.toDouble() else 0.0,
            if (rateType == RateType.FIXED)  fixed.toDouble() else 0.0
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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

                DialogField("Est. hours needed", hours,
                    onValueChange = { hours = it; hoursError = false },
                    keyboardType = KeyboardType.Decimal,
                    isError = hoursError,
                    errorMsg = if (hours.isBlank()) "Required" else "Numbers only"
                )

                // -----------------------------------------------------------------
                // Rate type selector — hourly vs. fixed. Swaps the input field
                // below based on which radio button is active.
                // -----------------------------------------------------------------
                Column {
                    Text("Payment type", style = MaterialTheme.typography.bodySmall, color = SlateDeep)
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        RateTypeOption(
                            label = "Hourly rate",
                            selected = rateType == RateType.HOURLY,
                            onSelect = { rateType = RateType.HOURLY; amountError = false },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        RateTypeOption(
                            label = "Fixed rate",
                            selected = rateType == RateType.FIXED,
                            onSelect = { rateType = RateType.FIXED; amountError = false },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (rateType == RateType.HOURLY) {
                    DialogField("₱ per hour", rate,
                        onValueChange = { rate = it; amountError = false },
                        keyboardType = KeyboardType.Decimal,
                        isError = amountError,
                        errorMsg = if (rate.isBlank()) "Required" else "Numbers only"
                    )
                } else {
                    DialogField("₱ fixed total", fixed,
                        onValueChange = { fixed = it; amountError = false },
                        keyboardType = KeyboardType.Decimal,
                        isError = amountError,
                        errorMsg = if (fixed.isBlank()) "Required" else "Numbers only"
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

/** A single radio-style option used by the hourly/fixed rate-type selector. */
@Composable
private fun RateTypeOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .background(
                color = if (selected) AccentBlueSoft else SlateCard,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = AccentBlue, unselectedColor = SlateDeep),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, fontFamily = InterFamily, fontSize = 12.sp, color = Ink)
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