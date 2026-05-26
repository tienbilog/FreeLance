package com.grp8.freelance

// A project the freelancer wants to schedule
data class Project(
    val id: Int,
    val name: String,
    val deadlineDay: Int,    // must finish by this day (e.g. day 3)
    val hoursNeeded: Double, // total hours the project takes
    val ratePerHour: Int     // hourly rate in ₱
) {
    // Total income this project earns if accepted
    val totalIncome: Int get() = (hoursNeeded * ratePerHour).toInt()
}

// One project assigned to a specific day
data class ScheduledProject(
    val project: Project,
    val assignedDay: Int
)

// The full result returned by the scheduler
data class ScheduleResult(
    val accepted: List<ScheduledProject>,
    val dropped: List<Project>,
    val totalIncome: Int,
    val log: List<String>  // step-by-step trace for the algorithm log
)