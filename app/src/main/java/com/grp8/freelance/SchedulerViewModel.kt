package com.grp8.freelance

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

class SchedulerViewModel : ViewModel() {

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _dailyCap = MutableStateFlow(8.0)
    val dailyCap: StateFlow<Double> = _dailyCap.asStateFlow()

    private val _result = MutableStateFlow<ScheduleResult?>(null)
    val result: StateFlow<ScheduleResult?> = _result.asStateFlow()

    private var idCtr = 1

    fun addProject(name: String, clientName: String, deadline: LocalDate, hours: Double, rate: Int) {
        _projects.value = _projects.value + Project(idCtr++, name, clientName, deadline, hours, rate)
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
}