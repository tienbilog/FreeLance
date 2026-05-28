package com.grp8.freelance

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val REASON_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

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
            dayHours[d] = if (d == today) todayRemainingHours(dailyCapHours) else dailyCapHours
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

            var day = today
            while (!day.isAfter(proj.deadlineDate)) {
                val avail = dayHours[day] ?: 0.0
                if (avail >= proj.hoursNeeded) {
                    dayHours[day] = avail - proj.hoursNeeded
                    assignment[index] = day
                    backtrack(index + 1, assignment, income + proj.totalIncome)
                    dayHours[day] = avail
                    assignment[index] = null
                }
                day = day.plusDays(1)
            }

            assignment[index] = null
            backtrack(index + 1, assignment, income)
        }

        backtrack(0, mutableMapOf(), 0.0)

        val finalDayHours = mutableMapOf<LocalDate, Double>()
        var fd = today
        repeat(totalDays) {
            finalDayHours[fd] = if (fd == today) todayRemainingHours(dailyCapHours) else dailyCapHours
            fd = fd.plusDays(1)
        }
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
                val reason = buildRejectionReason(proj, today, dailyCapHours, finalDayHours)
                dropped.add(DroppedProject(proj, reason))
            }
        }

        accepted.sortBy { it.assignedDate }
        return ScheduleResult(accepted, dropped, bestIncome.coerceAtLeast(0.0))
    }

    private fun buildRejectionReason(
        proj: Project,
        today: LocalDate,
        dailyCapHours: Double,
        finalDayHours: Map<LocalDate, Double>
    ): String {
        return when {
            proj.hoursNeeded > dailyCapHours -> {
                val needed = proj.hoursNeeded.fmt()
                val cap = dailyCapHours.fmt()
                "Needs ${needed}h in one day, but your daily cap is ${cap}h. " +
                        "Raise your daily cap to at least ${needed}h to schedule this."
            }

            proj.deadlineDate.isBefore(today) -> {
                "Deadline has already passed (${proj.deadlineDate.format(REASON_FMT)}). " +
                        "Update the deadline to a future date."
            }

            else -> {
                val earliestFreeSlot = finalDayHours.entries
                    .filter { (date, hours) -> !date.isBefore(today) && hours >= proj.hoursNeeded }
                    .minByOrNull { it.key }

                if (earliestFreeSlot != null && earliestFreeSlot.key.isAfter(proj.deadlineDate)) {
                    "All days before ${proj.deadlineDate.format(REASON_FMT)} were filled by " +
                            "earlier-deadline projects. Extend the deadline to " +
                            "${earliestFreeSlot.key.format(REASON_FMT)} to fit this in."
                } else if (earliestFreeSlot != null) {
                    "Skipping this project allowed higher-earning work to fit the schedule. " +
                            "Raise your daily cap to create more room."
                } else {
                    "Your schedule is fully booked. Raise your daily cap or remove another project to fit this in."
                }
            }
        }
    }

    private fun todayRemainingHours(dailyCapHours: Double): Double {
        val now = java.time.LocalTime.now()
        val secondsLeft = (24 * 3600) - (now.hour * 3600 + now.minute * 60 + now.second)
        return minOf(dailyCapHours, secondsLeft / 3600.0).coerceAtLeast(0.0)
    }
}

fun Int.formatted(): String = "%,d".format(this)
fun Double.fmt(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString() else "%.1f".format(this)
