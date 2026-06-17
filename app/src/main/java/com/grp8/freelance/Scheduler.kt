package com.grp8.freelance

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val REASON_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
private const val MIN_BOOKABLE_HOURS = 0.5   // treat near-midnight slivers as zero capacity

/**
 * Holds everything the backtracking search needs to mutate as it explores
 * assignments. Passed by reference instead of being closed over, so the
 * recursion in [Scheduler.backtrack] stays a pure function of its parameters.
 */
private class SearchState(
    val dayCapacity: MutableMap<LocalDate, Double>
) {
    var bestAssignment: Map<Int, LocalDate?> = emptyMap()
    var bestIncome: Double = -1.0
}

/**
 * Schedules freelance projects into available days to maximize total income,
 * respecting per-day capacity and per-project deadlines.
 *
 * Algorithm: pre-sort by deadline, then backtrack over (assign-day / skip)
 * decisions for each project, keeping the highest-income complete assignment.
 */
object Scheduler {

    fun schedule(projects: List<Project>, dailyCapHours: Double): ScheduleResult {
        if (projects.isEmpty()) return ScheduleResult(emptyList(), emptyList(), 0.0)

        val today = LocalDate.now()
        val ordered = preSort(projects)
        val state = SearchState(dayCapacity = buildCapacityMap(today, ordered, dailyCapHours))

        backtrack(ordered, index = 0, today, assignment = mutableMapOf(), income = 0.0, state)

        return buildResult(today, ordered, state, dailyCapHours)
    }

    // =========================================================================
    // STEP 1 — PRE-SORT
    //
    // Earliest-deadline-first. Visiting tight-deadline projects first lets the
    // backtracker discover infeasible branches sooner, pruning the search tree.
    // =========================================================================
    private fun preSort(projects: List<Project>): List<Project> =
        projects.sortedBy { it.deadlineDate }

    // =========================================================================
    // STEP 2 — BACKTRACKING ASSIGNMENT
    //
    // For each project, try every branch in order:
    //   • ASSIGN → place it on a feasible day, recurse, then undo (backtrack)
    //   • SKIP   → leave it unscheduled, recurse
    // When all projects are decided, keep the assignment if it earns more
    // than the best one seen so far.
    // =========================================================================
    private fun backtrack(
        projects: List<Project>,
        index: Int,
        today: LocalDate,
        assignment: MutableMap<Int, LocalDate?>,
        income: Double,
        state: SearchState
    ) {
        val allProjectsDecided = index == projects.size
        if (allProjectsDecided) {
            recordIfBest(assignment, income, state)
            return
        }

        val project = projects[index]

        for (day in feasibleDaysFor(project, today, state)) {
            assign(project, day, state)
            assignment[index] = day

            backtrack(projects, index + 1, today, assignment, income + project.totalIncome, state)

            unassign(project, day, state)         // <- the "back" in backtrack
            assignment[index] = null
        }

        skip(project, index, projects, today, assignment, income, state)
    }

    /** Every day from today through the project's deadline that has enough free capacity. */
    private fun feasibleDaysFor(project: Project, today: LocalDate, state: SearchState): List<LocalDate> {
        val days = mutableListOf<LocalDate>()
        var day = today
        while (!day.isAfter(project.deadlineDate)) {
            val capacity = state.dayCapacity[day] ?: 0.0
            if (capacity >= project.hoursNeeded) days.add(day)
            day = day.plusDays(1)
        }
        return days
    }

    private fun assign(project: Project, day: LocalDate, state: SearchState) {
        state.dayCapacity[day] = (state.dayCapacity[day] ?: 0.0) - project.hoursNeeded
    }

    private fun unassign(project: Project, day: LocalDate, state: SearchState) {
        state.dayCapacity[day] = (state.dayCapacity[day] ?: 0.0) + project.hoursNeeded
    }

    /** The "leave unscheduled" branch — recurse without consuming any capacity. */
    private fun skip(
        project: Project,
        index: Int,
        projects: List<Project>,
        today: LocalDate,
        assignment: MutableMap<Int, LocalDate?>,
        income: Double,
        state: SearchState
    ) {
        assignment[index] = null
        backtrack(projects, index + 1, today, assignment, income, state)
    }

    private fun recordIfBest(assignment: Map<Int, LocalDate?>, income: Double, state: SearchState) {
        if (income > state.bestIncome) {
            state.bestIncome = income
            state.bestAssignment = assignment.toMap()
        }
    }

    // =========================================================================
    // REAL-TIME CAPACITY
    //
    // Today's capacity is capped by however many hours remain before midnight.
    // A near-zero remainder (e.g. 11:58 PM) is rounded down to zero so projects
    // are never wedged into a useless sliver of today.
    // =========================================================================
    internal fun todayRemainingHours(dailyCapHours: Double): Double {
        val now         = LocalTime.now()
        val secondsLeft = (24 * 3600L) - (now.hour * 3600L + now.minute * 60L + now.second)
        val hoursLeft   = secondsLeft / 3600.0
        val effective   = minOf(dailyCapHours, hoursLeft).coerceAtLeast(0.0)
        return if (effective < MIN_BOOKABLE_HOURS) 0.0 else effective
    }

    /** Day → available-hours map spanning today through the latest deadline. */
    private fun buildCapacityMap(
        today: LocalDate,
        projects: List<Project>,
        dailyCapHours: Double
    ): MutableMap<LocalDate, Double> {
        val lastDeadline = projects.maxOf { it.deadlineDate }
        val totalDays    = ChronoUnit.DAYS.between(today, lastDeadline).toInt() + 1

        val map = mutableMapOf<LocalDate, Double>()
        var day = today
        repeat(totalDays) {
            map[day] = if (day == today) todayRemainingHours(dailyCapHours) else dailyCapHours
            day = day.plusDays(1)
        }
        return map
    }

    // =========================================================================
    // RESULT ASSEMBLY
    // =========================================================================
    private fun buildResult(
        today: LocalDate,
        projects: List<Project>,
        state: SearchState,
        dailyCapHours: Double
    ): ScheduleResult {
        val finalCapacity = buildCapacityMap(today, projects, dailyCapHours)
        projects.forEachIndexed { i, proj ->
            state.bestAssignment[i]?.let { day ->
                finalCapacity[day] = (finalCapacity[day] ?: 0.0) - proj.hoursNeeded
            }
        }

        val accepted = mutableListOf<ScheduledProject>()
        val dropped  = mutableListOf<DroppedProject>()

        projects.forEachIndexed { i, proj ->
            val day = state.bestAssignment[i]
            if (day != null) {
                accepted.add(ScheduledProject(proj, day))
            } else {
                dropped.add(DroppedProject(proj, rejectionReason(proj, today, dailyCapHours, finalCapacity)))
            }
        }

        accepted.sortBy { it.assignedDate }
        return ScheduleResult(accepted, dropped, state.bestIncome.coerceAtLeast(0.0))
    }

    private fun rejectionReason(
        proj: Project,
        today: LocalDate,
        dailyCapHours: Double,
        finalCapacity: Map<LocalDate, Double>
    ): String = when {
        proj.hoursNeeded > dailyCapHours -> {
            val needed = proj.hoursNeeded.fmt()
            val cap    = dailyCapHours.fmt()
            "Needs ${needed}h in one day, but your daily cap is ${cap}h. " +
                    "Raise your daily cap to at least ${needed}h to schedule this."
        }

        proj.deadlineDate.isBefore(today) -> {
            "Deadline has already passed (${proj.deadlineDate.format(REASON_FMT)}). " +
                    "Update the deadline to a future date."
        }

        else -> {
            val earliestFreeSlot = finalCapacity.entries
                .filter { (date, hours) -> !date.isBefore(today) && hours >= proj.hoursNeeded }
                .minByOrNull { it.key }

            if (earliestFreeSlot != null && earliestFreeSlot.key.isAfter(proj.deadlineDate)) {
                "All days before ${proj.deadlineDate.format(REASON_FMT)} were filled by " +
                        "earlier-deadline projects. Extend the deadline to " +
                        "${earliestFreeSlot.key.format(REASON_FMT)} to fit this in."
            } else {
                "Skipping this project allowed higher-earning work to fit the schedule, " +
                        "or your schedule is fully booked. Raise your daily cap or remove " +
                        "another project to fit this in."
            }
        }
    }
}

fun Int.formatted(): String = "%,d".format(this)
fun Double.fmt(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString() else "%.1f".format(this)