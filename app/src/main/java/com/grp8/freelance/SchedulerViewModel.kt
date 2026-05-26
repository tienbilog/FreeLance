package com.grp8.freelance

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SchedulerViewModel : ViewModel() {

    // The live list of projects the user has entered
    private val _projects = MutableStateFlow(sampleProjects())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    // Daily hour cap (default 8)
    private val _dailyCap = MutableStateFlow(8.0)
    val dailyCap: StateFlow<Double> = _dailyCap.asStateFlow()

    // The result after running the scheduler (null = not run yet)
    private val _result = MutableStateFlow<ScheduleResult?>(null)
    val result: StateFlow<ScheduleResult?> = _result.asStateFlow()

    fun addProject() {
        val next = (_projects.value.maxOfOrNull { it.id } ?: 0) + 1
        _projects.value = _projects.value + Project(next, "New project", 5, 4.0, 300)
    }

    fun updateProject(updated: Project) {
        _projects.value = _projects.value.map { if (it.id == updated.id) updated else it }
    }

    fun removeProject(id: Int) {
        _projects.value = _projects.value.filter { it.id != id }
    }

    fun setDailyCap(hours: Double) {
        _dailyCap.value = hours.coerceIn(1.0, 16.0)
    }

    fun runScheduler() {
        _result.value = Scheduler.schedule(_projects.value, _dailyCap.value)
    }

    fun clearResult() {
        _result.value = null
    }

    private fun sampleProjects() = listOf(
        Project(1, "Logo design",        deadlineDay = 3, hoursNeeded = 6.0,  ratePerHour = 400),
        Project(2, "Blog article",        deadlineDay = 2, hoursNeeded = 3.0,  ratePerHour = 300),
        Project(3, "Product video edit",  deadlineDay = 5, hoursNeeded = 12.0, ratePerHour = 500),
        Project(4, "Social media kit",    deadlineDay = 4, hoursNeeded = 5.0,  ratePerHour = 350),
        Project(5, "Pitch deck",          deadlineDay = 3, hoursNeeded = 8.0,  ratePerHour = 600),
        Project(6, "Newsletter copy",     deadlineDay = 1, hoursNeeded = 2.0,  ratePerHour = 250),
    )
}