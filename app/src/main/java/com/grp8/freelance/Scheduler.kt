package com.grp8.freelance

import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val REASON_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
private const val MIN_BOOKABLE_HOURS = 1.0   // minimum allocatable block of time

/**
 * Holds everything the backtracking search needs to mutate as it explores
 * assignments. Passed by reference instead of being closed over, so the
 * recursion in [Scheduler.backtrack] stays a pure function of its parameters.
 */
private class SearchState(
    val dayCapacity: MutableMap<LocalDate, Double>
) {
    var bestAssignment: Map<Int, Map<LocalDate, Double>> = emptyMap()
    var bestIncome: Double = -1.0
}

/**
 * Schedules freelance projects into available days to maximize total income,
 * respecting per-day capacity and per-project deadlines.
 *
 * Algorithm: pre-sort by deadline, then backtrack over (assign-day / skip)
 * decisions for each project, keeping the highest-income complete assignment.
 *
 * Capacity is supplied per calendar date via [weeklyCapacity] (the user's
 * "set weekly schedule" picks, e.g. Mon 6h / Wed 4h / Fri 8h / others 1h
 * fallback). Any date beyond what's explicitly provided — i.e. past the
 * current week — falls back to [fallbackHours] so the search still has a
 * horizon to schedule into for far-out deadlines.
 *
 * Used in two modes:
 *  • Phase 1→2: [schedule] — optimize a fresh batch of POTENTIAL projects.
 *  • Phase 3:   [reschedule] — re-optimize only the still-unfinished SCHEDULED
 *               projects, honoring hours already logged and any one-off
 *               capacity boosts the user grants to specific catch-up days.
 */
object Scheduler {

    fun schedule(
        projects: List<Project>,
        weeklyCapacity: Map<DayOfWeek, Double>,
        fallbackHours: Double = UNSELECTED_DAY_FALLBACK_HOURS
    ): ScheduleResult {
        if (projects.isEmpty()) return ScheduleResult(emptyList(), emptyList(), 0.0)

        val today = LocalDate.now()
        val ordered = preSort(projects)
        val state = SearchState(dayCapacity = buildCapacityMap(today, ordered, weeklyCapacity, fallbackHours))

        backtrack(ordered, index = 0, today, assignment = mutableMapOf(), income = 0.0, state)

        return buildResult(today, ordered, state, weeklyCapacity, fallbackHours, emptyMap())
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
     * without permanently changing their weekly schedule.
     */
    fun reschedule(
        remainingProjects: List<Project>,
        weeklyCapacity: Map<DayOfWeek, Double>,
        fallbackHours: Double = UNSELECTED_DAY_FALLBACK_HOURS,
        capacityOverrides: Map<LocalDate, Double> = emptyMap()
    ): ScheduleResult {
        if (remainingProjects.isEmpty()) return ScheduleResult(emptyList(), emptyList(), 0.0)

        val today = LocalDate.now()
        val ordered = preSort(remainingProjects)
        val baseCapacity = buildCapacityMap(today, ordered, weeklyCapacity, fallbackHours).toMutableMap()
        capacityOverrides.forEach { (date, hours) -> baseCapacity[date] = hours }

        val state = SearchState(dayCapacity = baseCapacity)
        backtrack(ordered, index = 0, today, assignment = mutableMapOf(), income = 0.0, state)

        return buildResult(today, ordered, state, weeklyCapacity, fallbackHours, capacityOverrides)
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
        assignment: MutableMap<Int, Map<LocalDate, Double>>,
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

        val feasibleDays = feasibleDaysFor(project, today, state)
        val validAllocations = getValidAllocations(workload, feasibleDays, state)

        for (alloc in validAllocations) {
            assign(alloc, state)
            assignment[index] = alloc

            backtrack(projects, index + 1, today, assignment, income + project.totalIncome, state)

            unassign(alloc, state)
            assignment.remove(index)
        }

        skip(index, projects, today, assignment, income, state)
    }

    /** Hours this project still needs scheduled — full estimate, or remainder if partially logged. */
    private fun effectiveHours(project: Project): Double =
        if (project.hoursLogged > 0.0) project.hoursRemaining else project.hoursNeeded

    /** Every day from today through the project's deadline that has > 0 capacity. */
    private fun feasibleDaysFor(
        project: Project,
        today: LocalDate,
        state: SearchState
    ): List<LocalDate> {
        val days = mutableListOf<LocalDate>()
        var day = today
        while (!day.isAfter(project.deadlineDate)) {
            val capacity = state.dayCapacity[day] ?: 0.0
            if (capacity >= MIN_BOOKABLE_HOURS) days.add(day)
            day = day.plusDays(1)
        }
        return days
    }

    private fun getValidAllocations(
        workload: Double,
        days: List<LocalDate>,
        state: SearchState
    ): List<Map<LocalDate, Double>> {
        val results = mutableListOf<Map<LocalDate, Double>>()

        val suffixCap = DoubleArray(days.size + 1)
        var sum = 0.0
        for (i in days.indices.reversed()) {
            sum += state.dayCapacity[days[i]] ?: 0.0
            suffixCap[i] = sum
        }

        if (suffixCap[0] < workload) return results

        fun search(index: Int, remaining: Double, currentAlloc: MutableMap<LocalDate, Double>) {
            if (remaining <= 0.0) {
                results.add(currentAlloc.toMap())
                return
            }
            if (index >= days.size) return
            if (suffixCap[index] < remaining) return

            val day = days[index]
            val cap = state.dayCapacity[day] ?: 0.0

            if (cap >= MIN_BOOKABLE_HOURS) {
                var allocated = minOf(cap, remaining)
                val leftover = remaining - allocated
                if (leftover > 0.0 && leftover < MIN_BOOKABLE_HOURS) {
                    allocated = remaining - MIN_BOOKABLE_HOURS
                }

                if (allocated >= MIN_BOOKABLE_HOURS) {
                    currentAlloc[day] = allocated
                    search(index + 1, remaining - allocated, currentAlloc)
                    currentAlloc.remove(day)
                }
            }

            search(index + 1, remaining, currentAlloc)
        }

        search(0, workload, mutableMapOf())
        return results
    }

    private fun assign(alloc: Map<LocalDate, Double>, state: SearchState) {
        alloc.forEach { (day, hours) ->
            state.dayCapacity[day] = (state.dayCapacity[day] ?: 0.0) - hours
        }
    }

    private fun unassign(alloc: Map<LocalDate, Double>, state: SearchState) {
        alloc.forEach { (day, hours) ->
            state.dayCapacity[day] = (state.dayCapacity[day] ?: 0.0) + hours
        }
    }

    private fun skip(
        index: Int,
        projects: List<Project>,
        today: LocalDate,
        assignment: MutableMap<Int, Map<LocalDate, Double>>,
        income: Double,
        state: SearchState
    ) {
        assignment.remove(index)
        backtrack(projects, index + 1, today, assignment, income, state)
    }

    private fun recordIfBest(assignment: Map<Int, Map<LocalDate, Double>>, income: Double, state: SearchState) {
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

    /**
     * Day → available-hours map spanning today through the latest deadline.
     *
     * For dates present in [weeklyCapacity] (the current week the user set up),
     * that explicit value is used — with today additionally clamped by the
     * real-time clock. For dates beyond that (future weeks, since the weekly
     * schedule doesn't carry forward), [fallbackHours] is used instead.
     */
    private fun buildCapacityMap(
        today: LocalDate,
        projects: List<Project>,
        weeklyCapacity: Map<DayOfWeek, Double>,
        fallbackHours: Double
    ): MutableMap<LocalDate, Double> {
        val lastDeadline = projects.maxOf { it.deadlineDate }
        val totalDays    = ChronoUnit.DAYS.between(today, lastDeadline).toInt() + 1

        val map = mutableMapOf<LocalDate, Double>()
        var day = today
        repeat(totalDays) {
            val plannedHours = weeklyCapacity[day.dayOfWeek] ?: fallbackHours
            map[day] = if (day == today) todayRemainingHours(plannedHours) else plannedHours
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
        weeklyCapacity: Map<DayOfWeek, Double>,
        fallbackHours: Double,
        capacityOverrides: Map<LocalDate, Double>
    ): ScheduleResult {
        val finalCapacity = buildCapacityMap(today, projects, weeklyCapacity, fallbackHours)
        capacityOverrides.forEach { (date, hours) -> finalCapacity[date] = hours }
        projects.forEachIndexed { i, _ ->
            val alloc = state.bestAssignment[i]
            if (alloc != null) {
                alloc.forEach { (day, hours) ->
                    finalCapacity[day] = (finalCapacity[day] ?: 0.0) - hours
                }
            }
        }

        val accepted    = mutableListOf<ScheduledProject>()
        val unscheduled = mutableListOf<UnscheduledProject>()

        projects.forEachIndexed { i, proj ->
            val alloc = state.bestAssignment[i]
            if (alloc != null && alloc.isNotEmpty()) {
                accepted.add(ScheduledProject(proj, alloc))
            } else {
                unscheduled.add(
                    UnscheduledProject(
                        proj,
                        constraintReason(proj, today, weeklyCapacity, fallbackHours, finalCapacity)
                    )
                )
            }
        }

        accepted.sortBy { it.assignments.keys.minOrNull() }
        return ScheduleResult(accepted, unscheduled, state.bestIncome.coerceAtLeast(0.0))
    }

    /**
     * Explains *why* a potential project can't currently be accepted — phrased
     * around the real constraints: deadline, estimated hours, and now also the
     * specific days the user has dedicated to work this week.
     */
    private fun constraintReason(
        proj: Project,
        today: LocalDate,
        weeklyCapacity: Map<DayOfWeek, Double>,
        fallbackHours: Double,
        finalCapacity: Map<LocalDate, Double>
    ): String {
        if (proj.deadlineDate.isBefore(today)) {
            return "You can't accept this job — its deadline (${proj.deadlineDate.format(REASON_FMT)}) has already passed. Update the deadline to a future date."
        }

        val workingDaysInWeek = weeklyCapacity.values.count { it > UNSELECTED_DAY_FALLBACK_HOURS }
        if (workingDaysInWeek == 0) {
            return "You can't accept this job — you haven't dedicated any work days in your weekly schedule yet. Set up your weekly schedule to fit this in."
        }

        var cumCapacity = 0.0
        var day = today
        while (!day.isAfter(proj.deadlineDate)) {
            cumCapacity += finalCapacity[day] ?: 0.0
            day = day.plusDays(1)
        }

        if (cumCapacity < proj.hoursNeeded) {
            return "You can't accept this job — there aren't enough available hours (${proj.hoursNeeded.fmt()}h needed, ${cumCapacity.fmt()}h available) before its deadline on ${proj.deadlineDate.format(REASON_FMT)}."
        }

        return "You can't accept this job alongside your other potential projects — accepting it would mean less total income than your current best combination. Try dedicating more hours to a work day, or removing a lower-value project."
    }
}