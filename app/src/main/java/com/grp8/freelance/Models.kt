package com.grp8.freelance

import java.time.LocalDate

data class Project(
    val id: Int,
    val name: String,
    val clientName: String,
    val deadlineDate: LocalDate,
    val hoursNeeded: Double,
    val ratePerHour: Double
) {
    val totalIncome: Double get() = hoursNeeded * ratePerHour
}

data class ScheduledProject(
    val project: Project,
    val assignedDate: LocalDate
)

data class DroppedProject(
    val project: Project,
    val reason: String
)

data class ScheduleResult(
    val accepted: List<ScheduledProject>,
    val dropped: List<DroppedProject>,
    val totalIncome: Double
)