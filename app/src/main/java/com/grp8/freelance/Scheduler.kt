package com.grp8.freelance

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

object Scheduler {

    fun schedule(projects: List<Project>, dailyCapHours: Double): ScheduleResult {
        if (projects.isEmpty()) return ScheduleResult(emptyList(), emptyList(), 0.0)

        val today = LocalDate.now()
        val now   = LocalTime.now()

        // Real-time fix: only count hours genuinely left today
        // e.g. at 11pm → ~60 min left → 60/1440 * dailyCap ≈ 0.33h
        val minutesLeftToday = ((23 - now.hour) * 60 + (59 - now.minute)).coerceAtLeast(0)
        val todayEffectiveCap = (minutesLeftToday / 1440.0) * dailyCapHours

        val sorted   = projects.sortedBy { it.deadlineDate }
        val lastDate = sorted.maxOf { it.deadlineDate }
        val totalDays = ChronoUnit.DAYS.between(today, lastDate).toInt() + 1

        val dayHours = mutableMapOf<LocalDate, Double>()
        var d = today
        repeat(totalDays) {
            dayHours[d] = if (d == today) todayEffectiveCap else dailyCapHours
            d = d.plusDays(1)
        }

        var bestIncome     = -1.0
        var bestAssignment = mutableMapOf<Int, LocalDate?>()

        fun backtrack(index: Int, assignment: MutableMap<Int, LocalDate?>, income: Double) {
            if (index == sorted.size) {
                if (income > bestIncome) {
                    bestIncome     = income
                    bestAssignment = assignment.toMutableMap()
                }
                return
            }
            val proj = sorted[index]

            // Try every valid day up to deadline (your original full backtracking preserved)
            var day = today
            while (!day.isAfter(proj.deadlineDate)) {
                val avail = dayHours[day] ?: 0.0
                if (avail >= proj.hoursNeeded) {
                    dayHours[day]     = avail - proj.hoursNeeded
                    assignment[index] = day
                    backtrack(index + 1, assignment, income + proj.totalIncome)
                    dayHours[day]     = avail
                    assignment[index] = null
                }
                day = day.plusDays(1)
            }

            // Also try skipping this project (your original logic preserved)
            assignment[index] = null
            backtrack(index + 1, assignment, income)
        }

        backtrack(0, mutableMapOf(), 0.0)

        val accepted = mutableListOf<ScheduledProject>()
        val dropped  = mutableListOf<DroppedProject>()

        sorted.forEachIndexed { i, proj ->
            val date = bestAssignment[i]
            if (date != null) {
                accepted.add(ScheduledProject(proj, date))
            } else {
                // Diagnose why it was dropped
                val deadlinePassed = !proj.deadlineDate.isAfter(today) &&
                        todayEffectiveCap < proj.hoursNeeded
                val reason = if (deadlinePassed) DropReason.PAST_DEADLINE
                else DropReason.NO_CAPACITY

                val explanation = when (reason) {
                    DropReason.PAST_DEADLINE ->
                        "\"${proj.name}\" was dropped because its deadline is today " +
                                "and only ${todayEffectiveCap.fmt()}h remain — " +
                                "not enough for the ${proj.hoursNeeded.fmt()}h required."
                    DropReason.NO_CAPACITY ->
                        "\"${proj.name}\" was dropped because no single day before " +
                                "${proj.deadlineDate.format(DATE_FMT)} had " +
                                "${proj.hoursNeeded.fmt()}h free. Accepting it would have " +
                                "reduced total income by ₱${proj.totalIncome.fmt()}."
                }
                dropped.add(DroppedProject(proj, reason, explanation))
            }
        }

        accepted.sortBy { it.assignedDate }
        return ScheduleResult(accepted, dropped, bestIncome.coerceAtLeast(0.0))
    }
}

fun Int.formatted(): String = "%,d".format(this)
fun Double.formatted(): String = "%,.0f".format(this)   // for ₱ amounts
fun Double.fmt(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString()
    else "%.1f".format(this)