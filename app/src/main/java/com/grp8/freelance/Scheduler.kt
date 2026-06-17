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
 *
 * Used in two modes:
 *  • Phase 1→2: [schedule] — optimize a fresh batch of POTENTIAL projects.
 *  • Phase 3:   [reschedule] — re-optimize only the still-unfinished SCHEDULED
 *               projects, honoring hours already logged and any one-off
 *               capacity boosts the user grants to specific catch-up days.
 */
object Scheduler {

    fun schedule(projects: List<Project>, dailyCapHours: Double): ScheduleResult {
        if (projects.isEmpty()) return ScheduleResult(emptyList(), emptyList(), 0.0)

        val today = LocalDate.now()
        val ordered = preSort(projects)
        val state = SearchState(dayCapacity = buildCapacityMap(today, ordered, dailyCapHours))

        backtrack(ordered, index = 0, today, assignment = mutableMapOf(), income = 0.0, state)

        return buildResult(today, ordered, state, dailyCapHours, emptyMap())
    }

    /**
     * Re-optimizes only the remaining (not yet done) work for Phase 3.
     *
     * [remainingProjects] should use [Project.hoursRemaining] as their effective
     * workload — callers typically pass projects with `hoursNeeded` already
     * substituted to `hoursRemaining` so the backtracker schedules what's left,
     * not the original full estimate.
     *
     * [capacityOverrides] lets the user grant a one-time capacity boost to a
     * specific future date (the "I'll work extra on Thursday to catch up" case)
     * without permanently changing their daily cap.
     */
    fun reschedule(
        remainingProjects: List<Project>,
        dailyCapHours: Double,
        capacityOverrides: Map<LocalDate, Double> = emptyMap()
    ): ScheduleResult {
        if (remainingProjects.isEmpty()) return ScheduleResult(emptyList(), emptyList(), 0.0)

        val today = LocalDate.now()
        val ordered = preSort(remainingProjects)
        val baseCapacity = buildCapacityMap(today, ordered, dailyCapHours).toMutableMap()
        capacityOverrides.forEach { (date, hours) -> baseCapacity[date] = hours }

        val state = SearchState(dayCapacity = baseCapacity)
        backtrack(ordered, index = 0, today, assignment = mutableMapOf(), income = 0.0, state)

        return buildResult(today, ordered, state, dailyCapHours, capacityOverrides)
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
        val workload = effectiveHours(project)

        for (day in feasibleDaysFor(project, workload, today, state)) {
            assign(workload, day, state)
            assignment[index] = day

            backtrack(projects, index + 1, today, assignment, income + project.totalIncome, state)

            unassign(workload, day, state)        // <- the "back" in backtrack
            assignment[index] = null
        }

        skip(index, projects, today, assignment, income, state)
    }

    /** Hours this project still needs scheduled — full estimate, or remainder if partially logged. */
    private fun effectiveHours(project: Project): Double =
        if (project.hoursLogged > 0.0) project.hoursRemaining else project.hoursNeeded

    /** Every day from today through the project's deadline that has enough free capacity. */
    private fun feasibleDaysFor(
        project: Project,
        workload: Double,
        today: LocalDate,
        state: SearchState
    ): List<LocalDate> {
        val days = mutableListOf<LocalDate>()
        var day = today
        while (!day.isAfter(project.deadlineDate)) {
            val capacity = state.dayCapacity[day] ?: 0.0
            if (capacity >= workload) days.add(day)
            day = day.plusDays(1)
        }
        return days
    }

    private fun assign(workload: Double, day: LocalDate, state: SearchState) {
        state.dayCapacity[day] = (state.dayCapacity[day] ?: 0.0) - workload
    }

    private fun unassign(workload: Double, day: LocalDate, state: SearchState) {
        state.dayCapacity[day] = (state.dayCapacity[day] ?: 0.0) + workload
    }

    /** The "leave unscheduled" branch — recurse without consuming any capacity. */
    private fun skip(
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
        dailyCapHours: Double,
        capacityOverrides: Map<LocalDate, Double>
    ): ScheduleResult {
        val finalCapacity = buildCapacityMap(today, projects, dailyCapHours)
        capacityOverrides.forEach { (date, hours) -> finalCapacity[date] = hours }
        projects.forEachIndexed { i, proj ->
            state.bestAssignment[i]?.let { day ->
                val workload = effectiveHours(proj)
                finalCapacity[day] = (finalCapacity[day] ?: 0.0) - workload
            }
        }

        val accepted    = mutableListOf<ScheduledProject>()
        val unscheduled = mutableListOf<UnscheduledProject>()

        projects.forEachIndexed { i, proj ->
            val day = state.bestAssignment[i]
            if (day != null) {
                accepted.add(ScheduledProject(proj, day))
            } else {
                unscheduled.add(UnscheduledProject(proj, constraintReason(proj, today, dailyCapHours, finalCapacity)))
            }
        }

        accepted.sortBy { it.assignedDate }
        return ScheduleResult(accepted, unscheduled, state.bestIncome.coerceAtLeast(0.0))
    }

    /**
     * Explains *why* a potential project can't currently be accepted — phrased
     * around the two real constraints (deadline, estimated hours) rather than
     * generic rejection language.
     */
    private fun constraintReason(
        proj: Project,
        today: LocalDate,
        dailyCapHours: Double,
        finalCapacity: Map<LocalDate, Double>
    ): String = when {
        proj.hoursNeeded > dailyCapHours -> {
            val needed = proj.hoursNeeded.fmt()
            val cap    = dailyCapHours.fmt()
            "You can't accept this job — it needs ${needed}h in a single day, but your daily " +
                    "capacity is only ${cap}h. Raise your daily cap to at least ${needed}h."
        }

        proj.deadlineDate.isBefore(today) -> {
            "You can't accept this job — its deadline (${proj.deadlineDate.format(REASON_FMT)}) " +
                    "has already passed. Update the deadline to a future date."
        }

        else -> {
            val earliestFreeSlot = finalCapacity.entries
                .filter { (date, hours) -> !date.isBefore(today) && hours >= proj.hoursNeeded }
                .minByOrNull { it.key }

            if (earliestFreeSlot != null && earliestFreeSlot.key.isAfter(proj.deadlineDate)) {
                "You can't accept this job — its deadline (${proj.deadlineDate.format(REASON_FMT)}) " +
                        "is too tight given your other commitments. The earliest open slot is " +
                        "${earliestFreeSlot.key.format(REASON_FMT)}."
            } else {
                "You can't accept this job alongside your other potential projects — accepting it " +
                        "would mean less total income than your current best combination. Try " +
                        "raising your daily cap, or removing a lower-value project."
            }
        }
    }
}