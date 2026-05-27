package com.grp8.freelance

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object Scheduler {

    fun schedule(projects: List<Project>, dailyCapHours: Double): ScheduleResult {
        if (projects.isEmpty()) return ScheduleResult(emptyList(), emptyList(), 0.0)

        val today = LocalDate.now()
        val sorted = projects.sortedBy { it.deadlineDate }
        val lastDate = sorted.maxOf { it.deadlineDate }
        val totalDays = ChronoUnit.DAYS.between(today, lastDate).toInt() + 1

        val dayHours = mutableMapOf<LocalDate, Double>()
        var d = today
        repeat(totalDays) {
            if (d == today) {
                val now = java.time.LocalTime.now()
                val secondsLeft = (24 * 3600) - (now.hour * 3600 + now.minute * 60 + now.second)
                val fractionLeft = secondsLeft / (24.0 * 3600)
                dayHours[d] = (dailyCapHours * fractionLeft).coerceAtLeast(0.0)
            } else {
                dayHours[d] = dailyCapHours
            }
            d = d.plusDays(1)
        }

        var bestIncome = -1.0
        var bestAssignment = mutableMapOf<Int, LocalDate?>()

        fun backtrack(index: Int, assignment: MutableMap<Int, LocalDate?>, income: Double) {
            if (index == sorted.size) {
                if (income > bestIncome) {
                    bestIncome = income
                    bestAssignment = assignment.toMutableMap()
                }
                return
            }
            val proj = sorted[index]

            // Try placing the project on every valid day up to its deadline
            var day = today
            while (!day.isAfter(proj.deadlineDate)) {
                val avail = dayHours[day] ?: 0.0
                if (avail >= proj.hoursNeeded) {
                    dayHours[day] = avail - proj.hoursNeeded
                    assignment[index] = day
                    backtrack(index + 1, assignment, income + proj.totalIncome)
                    dayHours[day] = avail          // restore hours
                    assignment[index] = null       // restore assignment
                }
                day = day.plusDays(1)
            }

            // Also try skipping this project entirely
            assignment[index] = null
            backtrack(index + 1, assignment, income)
        }

        backtrack(0, mutableMapOf(), 0.0)

        val finalDayHours = mutableMapOf<LocalDate, Double>()
        var fd = today
        repeat(totalDays) { finalDayHours[fd] = dailyCapHours; fd = fd.plusDays(1) }
        sorted.forEachIndexed { i, proj ->
            val date = bestAssignment[i]
            if (date != null) finalDayHours[date] = (finalDayHours[date] ?: 0.0) - proj.hoursNeeded
        }

        val accepted = mutableListOf<ScheduledProject>()
        val dropped = mutableListOf<DroppedProject>()
        sorted.forEachIndexed { i, proj ->
            val date = bestAssignment[i]
            if (date != null) {
                accepted.add(ScheduledProject(proj, date))
            } else {
                val reason = when {
                    proj.hoursNeeded > dailyCapHours ->
                        "Exceeds working hours per day."
                    proj.deadlineDate.isBefore(today) ->
                        "Deadline has already passed."
                    else ->
                        "No time could be freed up before this deadline without reducing total earnings."
                }
                dropped.add(DroppedProject(proj, reason))
            }
        }
        accepted.sortBy { it.assignedDate }
        return ScheduleResult(accepted, dropped, bestIncome.coerceAtLeast(0.0))
    }
}

fun Int.formatted(): String = "%,d".format(this)
fun Double.fmt(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString() else "%.1f".format(this)