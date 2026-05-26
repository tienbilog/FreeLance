package com.grp8.freelance

object Scheduler {

    fun schedule(projects: List<Project>, dailyCapHours: Double): ScheduleResult {
        val log = mutableListOf<String>()

        if (projects.isEmpty()) {
            return ScheduleResult(emptyList(), emptyList(), 0, listOf("No projects to schedule."))
        }

        // --- Algorithm 1: Presort by deadline (earliest first) ---
        val sorted = projects.sortedBy { it.deadlineDay }
        log.add("Presort: ${sorted.joinToString(" → ") { "${it.name} (day ≤${it.deadlineDay})" }}")

        val maxDay = sorted.maxOf { it.deadlineDay }

        // dayHours[d] = remaining hours available on day d (index 1..maxDay)
        val dayHours = DoubleArray(maxDay + 1) { dailyCapHours }

        // assignment[i] = day assigned to sorted[i], or -1 if dropped
        var bestIncome = -1
        var bestAssignment = IntArray(sorted.size) { -1 }

        // --- Algorithm 2: Backtracking ---
        fun backtrack(index: Int, assignment: IntArray, income: Int) {
            if (index == sorted.size) {
                // All projects processed — check if this is the best result
                if (income > bestIncome) {
                    bestIncome = income
                    bestAssignment = assignment.copyOf()
                    log.add("✓ Complete assignment found — income: ₱${income.formatted()}")
                }
                return
            }

            val proj = sorted[index]
            var placed = false

            for (day in 1..proj.deadlineDay) {
                if (dayHours[day] >= proj.hoursNeeded) {
                    // Valid slot found — assign and recurse
                    dayHours[day] -= proj.hoursNeeded
                    assignment[index] = day
                    log.add("  Assign \"${proj.name}\" → Day $day (${proj.hoursNeeded}h, ₱${proj.totalIncome.formatted()}, ${dayHours[day].fmt()}h left)")
                    backtrack(index + 1, assignment, income + proj.totalIncome)
                    // Undo (backtrack)
                    dayHours[day] += proj.hoursNeeded
                    assignment[index] = -1
                    placed = true
                    break // greedy: take the first valid slot
                } else {
                    log.add("  ✗ Prune \"${proj.name}\" on Day $day — needs ${proj.hoursNeeded}h, only ${dayHours[day].fmt()}h left")
                }
            }

            if (!placed) {
                // No valid day before deadline — drop this project
                log.add("  ✗ Drop \"${proj.name}\" — no slot before deadline")
                assignment[index] = -1
                backtrack(index + 1, assignment, income)
            }
        }

        backtrack(0, IntArray(sorted.size) { -1 }, 0)

        // Build final result from bestAssignment
        val accepted = mutableListOf<ScheduledProject>()
        val dropped = mutableListOf<Project>()

        sorted.forEachIndexed { i, proj ->
            val day = bestAssignment[i]
            if (day >= 1) accepted.add(ScheduledProject(proj, day))
            else dropped.add(proj)
        }

        // Sort accepted by day for display
        accepted.sortBy { it.assignedDay }

        return ScheduleResult(accepted, dropped, bestIncome.coerceAtLeast(0), log)
    }
}

// Extension helpers for clean number formatting
fun Int.formatted(): String = "%,d".format(this)
fun Double.fmt(): String = if (this == this.toLong().toDouble()) this.toLong().toString() else "%.1f".format(this)