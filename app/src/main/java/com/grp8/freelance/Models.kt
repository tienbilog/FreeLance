package com.grp8.freelance

import java.time.LocalDate
import java.time.YearMonth

// =============================================================================
// RATE TYPE — how a project's income is calculated
// =============================================================================
enum class RateType { HOURLY, FIXED }

// =============================================================================
// PROJECT LIFECYCLE STATUS — drives which tab a project shows up in
//
//   POTENTIAL  → Phase 1 (Optimizer): candidate jobs not yet committed to
//   SCHEDULED  → Phase 3 (To-Do): accepted into the working schedule
//   DONE       → Phase 4 (Income): completed, income locked into the month
// =============================================================================
enum class ProjectStatus { POTENTIAL, SCHEDULED, DONE }

enum class TaskStatus { NOT_STARTED, ONGOING, COMPLETED }

data class Subtask(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val isCompleted: Boolean = false
)

data class Project(
    val id: Int,
    val name: String,
    val clientName: String,
    val deadlineDate: LocalDate,
    val hoursNeeded: Double,          // always required — used for scheduling capacity math
    val rateType: RateType = RateType.HOURLY,
    val ratePerHour: Double = 0.0,    // used when rateType == HOURLY
    val fixedAmount: Double = 0.0,    // used when rateType == FIXED
    val status: ProjectStatus = ProjectStatus.POTENTIAL,
    val assignedDates: Map<LocalDate, Double> = emptyMap(), // date -> hours allocated
    val hoursLogged: Double = 0.0,            // actual hours worked so far (Phase 3)
    val completedDate: LocalDate? = null,     // set once marked done (Phase 3 → 4)
    val subtasks: List<Subtask> = emptyList(),
    val taskStatus: TaskStatus = TaskStatus.NOT_STARTED,
    val scheduleWarning: String? = null,      // explicitly warns if not fully scheduled
    val completedAssignments: Set<LocalDate> = emptySet()
) {
    /** Total income this project earns, regardless of rate type. */
    val totalIncome: Double get() = when (rateType) {
        RateType.HOURLY -> hoursNeeded * ratePerHour
        RateType.FIXED  -> fixedAmount
    }

    /** Hours still believed remaining, based on what's been logged so far. */
    val hoursRemaining: Double get() = (hoursNeeded - hoursLogged).coerceAtLeast(0.0)

    /** Progress percentage based on completed subtasks and work sessions. */
    val progress: Float get() {
        val totalSessions = assignedDates.size
        val completedSessionsCount = completedAssignments.size
        
        val totalSubtasksCount = subtasks.size
        val completedSubtasksCount = subtasks.count { it.isCompleted }
        
        val totalItems = totalSessions + totalSubtasksCount
        if (totalItems == 0) return 0f
        
        return (completedSessionsCount + completedSubtasksCount).toFloat() / totalItems
    }
}

data class ScheduledProject(
    val project: Project,
    val assignments: Map<LocalDate, Double>
)

/** A potential project that the optimizer could not fit into the schedule. */
data class UnscheduledProject(
    val project: Project,
    val constraint: String   // human-readable reason: deadline or capacity related
)

data class ScheduleResult(
    val accepted: List<ScheduledProject>,
    val unscheduled: List<UnscheduledProject>,
    val totalIncome: Double
)

// =============================================================================
// PHASE 3 — TO-DO LIST adjustments
// =============================================================================

/** Result of checking whether the user is ahead of or behind their plan. */

// =============================================================================
// PHASE 4 — MONTHLY INCOME
// =============================================================================

data class IncomeEntry(
    val project: Project,
    val completedDate: LocalDate,
    val amount: Double
)

data class MonthlyIncomeSummary(
    val month: YearMonth,
    val entries: List<IncomeEntry>
) {
    val totalIncome: Double get() = entries.sumOf { it.amount }
}

// =============================================================================
// WEEKLY SCHEDULE — per-day capacity for the current calendar week (Mon–Sun).
//
// Replaces a single flat daily cap: the user picks which days they're
// dedicating time to work and how many hours each of those days.
// =============================================================================

fun Int.formatted(): String = "%,d".format(this)
fun Double.fmt(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString() else "%.1f".format(this)
fun Double.money(): String = "%,.2f".format(this)