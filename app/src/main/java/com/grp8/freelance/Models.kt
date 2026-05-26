package com.grp8.freelance

import java.time.LocalDate

data class Project(
    val id: Int,
    val name: String,
    val clientName: String,
    val deadlineDate: LocalDate,
    val hoursNeeded: Double,
    val ratePerHour: Int
) {
    val totalIncome: Int get() = (hoursNeeded * ratePerHour).toInt()
}

data class ScheduledProject(
    val project: Project,
    val assignedDate: LocalDate
)

data class ScheduleResult(
    val accepted: List<ScheduledProject>,
    val dropped: List<Project>,
    val totalIncome: Int
)